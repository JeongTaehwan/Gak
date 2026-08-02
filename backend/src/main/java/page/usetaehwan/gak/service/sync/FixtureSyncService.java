package page.usetaehwan.gak.service.sync;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.config.SyncProperties;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.domain.SyncStatus;
import page.usetaehwan.gak.external.apifootball.ApiFootballClient;
import page.usetaehwan.gak.external.apifootball.ApiFootballException;
import page.usetaehwan.gak.external.apifootball.ReplayDataMissingException;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;

/**
 * 동기화 오케스트레이션 — 가져오고, 반영하고, 이력을 남긴다.
 *
 * <p><b>이 클래스에는 {@code @Transactional}이 없다.</b> 의도적이다. 외부 호출과 DB 쓰기를
 * 한 트랜잭션에 묶으면 HTTP 응답을 기다리는 내내 DB 커넥션이 잠긴다.
 * 그래서 순서를 이렇게 나눈다.
 * <pre>
 *   1) fetch   — 트랜잭션 밖 (네트워크)
 *   2) upsert  — 트랜잭션 안 (FixtureUpsertService, 대회 하나 = 트랜잭션 하나)
 *   3) 이력 저장 — 별도 트랜잭션 (upsert가 롤백돼도 실패 기록은 남아야 한다)
 * </pre>
 *
 * <h2>실패하면 어떻게 되나</h2>
 * 한 대회의 실패는 그 대회에서 끝난다. 예외를 잡아 FAILED로 기록하고 다음 대회로 넘어가며,
 * 다음 주기에 자연히 재시도된다. 별도의 재시도 큐가 필요 없는 이유는 upsert가 멱등이기 때문이다
 * — 같은 응답을 몇 번 적용하든 결과 상태가 같다. "어디까지 반영했는지"를 기억할 필요가 없다.
 *
 * <h2>기록하는 실패와 기록하지 않는 실패</h2>
 * <table border="1">
 *   <caption>결과별 처리</caption>
 *   <tr><th>상황</th><th>이력</th><th>이유</th></tr>
 *   <tr><td>통신 실패·타임아웃·HTTP 오류</td><td>FAILED로 기록</td>
 *       <td>시도했고 실패했다. 재시도 판단과 원인 추적에 필요하다</td></tr>
 *   <tr><td>HTTP 200 + body의 errors</td><td>FAILED로 기록</td>
 *       <td>같음. 플랜 제한 메시지가 그대로 남아야 원인을 안다</td></tr>
 *   <tr><td>일일 요청 예산 소진</td><td>SKIPPED로 기록</td>
 *       <td>그날 무엇을 못 했는지가 정보다</td></tr>
 *   <tr><td>재생 파일 없음</td><td><b>기록하지 않음</b> (로그만)</td>
 *       <td>시도조차 하지 않았고, 파일을 채우기 전까지 매 주기 반복될 사실이다.
 *           쌓아 두면 진짜 실패가 그 안에 묻힌다</td></tr>
 * </table>
 * 마지막 경우에도 {@link SyncLog} 객체는 만들어 돌려준다(저장만 하지 않는다).
 * 수동 실행 결과에는 "왜 안 됐는지"가 보여야 하기 때문이다 —
 * 저장 여부는 {@link SyncLog#isRecorded()}로 구분한다.
 */
@Service
public class FixtureSyncService {

	private static final Logger log = LoggerFactory.getLogger(FixtureSyncService.class);

	private final ApiFootballClient client;
	private final FixtureUpsertService upsertService;
	private final CompetitionRepository competitionRepository;
	private final SyncLogRepository syncLogRepository;
	private final SyncPlanner planner;
	private final RequestBudget budget;
	private final SyncProperties properties;
	private final Clock clock;

	public FixtureSyncService(ApiFootballClient client,
	                          FixtureUpsertService upsertService,
	                          CompetitionRepository competitionRepository,
	                          SyncLogRepository syncLogRepository,
	                          SyncPlanner planner,
	                          RequestBudget budget,
	                          SyncProperties properties,
	                          Clock clock) {
		this.client = client;
		this.upsertService = upsertService;
		this.competitionRepository = competitionRepository;
		this.syncLogRepository = syncLogRepository;
		this.planner = planner;
		this.budget = budget;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @param attempted 시도한 대회 수
	 * @param succeeded 성공한 대회 수
	 * @param spent     이번 회차에 소모한 요청 수
	 * @param logs      각 대회의 이력
	 */
	public record SyncReport(int attempted, int succeeded, int spent, List<SyncLog> logs) {
	}

	/**
	 * 주기가 지난 대회들을 예산이 허락하는 만큼 동기화한다. 스케줄러의 진입점.
	 */
	public SyncReport syncDueCompetitions() {
		Instant now = Instant.now(clock);
		List<SyncPlanner.Candidate> candidates = planner.selectDue(now);
		List<SyncLog> logs = new ArrayList<>();
		List<Long> noReplayData = new ArrayList<>();
		int succeeded = 0;
		int spent = 0;

		if (candidates.isEmpty()) {
			log.debug("동기화 대상 없음 — 모든 대회가 아직 갱신 주기 안에 있습니다.");
			return new SyncReport(0, 0, 0, List.of());
		}

		log.info("동기화 시작: 대상 {}개, 오늘 남은 요청 예산 {}",
				candidates.size(), budget.remainingToday());

		for (SyncPlanner.Candidate candidate : candidates) {
			Competition competition = candidate.competition();

			// 재생 모드는 할당량을 쓰지 않으므로 예산 검사를 건너뛴다.
			if (client.source() == SyncSource.REAL && !budget.canSpend(1)) {
				logs.add(syncLogRepository.save(SyncLog.skipped(
						competition.getId(), seasonOf(competition), Instant.now(clock),
						client.source(),
						"일일 요청 예산 소진 — 다음 주기에 재시도합니다.")));
				log.warn("일일 요청 예산 소진. 남은 대회 {}개는 다음 주기로 미룹니다.",
						candidates.size() - logs.size());
				break;
			}

			SyncLog result = syncCompetition(competition);
			logs.add(result);
			spent += result.getRequestCount();
			if (result.getStatus() == SyncStatus.SUCCESS) {
				succeeded++;
			}
			if (!result.isRecorded()) {
				// 재생 파일이 없어 이력에 남기지 않은 대회. 여기 모아 한 줄로만 알린다.
				noReplayData.add(result.getCompetitionId());
			}
		}

		if (!noReplayData.isEmpty()) {
			log.info("재생 데이터가 없어 건너뛴 대회 {}개 (이력에 남기지 않음): {}",
					noReplayData.size(), noReplayData);
		}
		log.info("동기화 종료: {}개 시도 / {}개 성공 / 요청 {}회 소모 (오늘 누적 {}회)",
				logs.size(), succeeded, spent, budget.spentToday());
		return new SyncReport(logs.size(), succeeded, spent, logs);
	}

	/** 대회 id로 한 건 동기화(수동 실행·테스트용). */
	public SyncLog syncCompetition(Long competitionId) {
		Competition competition = competitionRepository.findById(competitionId)
				.orElseThrow(() -> new NoSuchElementException(
						"대회를 찾을 수 없습니다. competitionId=" + competitionId));
		return syncCompetition(competition);
	}

	/**
	 * 대회 하나를 동기화하고 결과를 이력으로 남긴다. 예외를 밖으로 던지지 않는다
	 * — 한 대회의 실패가 나머지 대회의 동기화를 막지 않아야 한다.
	 *
	 * @return 이 시도의 결과. 재생 파일이 없어 <b>저장하지 않은</b> 경우도 객체는 돌려주므로,
	 *         DB에 남았는지는 {@link SyncLog#isRecorded()}로 확인한다
	 */
	public SyncLog syncCompetition(Competition competition) {
		int season = seasonOf(competition);
		Instant startedAt = Instant.now(clock);
		int consumed = 0;

		try {
			// 1) 네트워크 — 트랜잭션 밖
			ApiFootballClient.FixturesFetch fetch = client.fetchFixtures(competition.getId(), season);
			consumed = fetch.requestCount();

			// 2) DB 반영 — 대회 하나 = 트랜잭션 하나
			FixtureUpsertService.UpsertResult result =
					upsertService.upsert(competition.getId(), season, fetch.items());

			log.info("[{}] {} 시즌 {} — 경기 {}건 반영, 신규 팀 {} / 경기장 {} (요청 {}회)",
					client.source(), competition.getName(), season,
					result.fixtureCount(), result.newTeamCount(), result.newVenueCount(), consumed);

			return syncLogRepository.save(SyncLog.success(
					competition.getId(), season, startedAt, Instant.now(clock), client.source(),
					consumed, result.fixtureCount(), result.newTeamCount(), result.newVenueCount()));

		} catch (ReplayDataMissingException e) {
			// 개발용 재생 파일이 아직 없는 대회. 실패가 아니라 "가진 데이터가 없음"이므로
			// 이력에 남기지 않는다 — 매 주기 반복될 사실이라 쌓이면 진짜 실패를 가린다.
			// (ApiFootballException 의 하위 타입이라 반드시 그보다 먼저 잡아야 한다)
			log.debug("[{}] {} 시즌 {} — 재생 데이터 없음: {}",
					client.source(), competition.getName(), season, e.getMessage());
			return SyncLog.skipped(competition.getId(), season, startedAt, client.source(),
					e.getMessage());

		} catch (ApiFootballException e) {
			// 통신 실패·타임아웃·HTTP 오류·body의 errors 필드가 전부 여기로 온다.
			log.warn("[{}] {} 시즌 {} 동기화 실패: {}",
					client.source(), competition.getName(), season, e.getMessage());
			return syncLogRepository.save(SyncLog.failed(
					competition.getId(), season, startedAt, Instant.now(clock), client.source(),
					e.consumedRequests(), e.getMessage()));

		} catch (RuntimeException e) {
			// 매핑·DB 반영 단계의 실패. 요청은 이미 나갔으므로 소모량은 그대로 기록한다.
			log.error("[{}] {} 시즌 {} 반영 실패", client.source(), competition.getName(), season, e);
			return syncLogRepository.save(SyncLog.failed(
					competition.getId(), season, startedAt, Instant.now(clock), client.source(),
					consumed, e.getClass().getSimpleName() + ": " + e.getMessage()));
		}
	}

	/**
	 * 이 대회의 현재 시즌 번호.
	 *
	 * <p>{@code gak.sync.season-override}가 있으면 그 값을 쓴다. 무료 플랜은 최근 시즌 접근을
	 * 막는 경우가 있어({@code "Free plans do not have access to this season"}), 개발 중에는
	 * 접근 가능한 시즌으로 고정할 수 있어야 한다.
	 */
	private int seasonOf(Competition competition) {
		Integer override = properties.seasonOverride();
		if (override != null) {
			return override;
		}
		return competition.seasonFor(LocalDate.now(clock.withZone(ZoneOffset.UTC)));
	}
}
