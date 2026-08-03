package page.usetaehwan.gak.service.sync;

import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.external.apifootball.ApiFootballClient;
import page.usetaehwan.gak.repository.CompetitionRepository;

/**
 * 순위표 동기화.
 *
 * <p>네트워크를 기다리는 쪽(여기)과 DB 를 쓰는 쪽({@link StandingUpsertService})을 나눈다.
 * 트랜잭션 안에서 외부 호출을 기다리면 커넥션을 그만큼 붙잡는다.
 *
 * <h2>컵대회는 대상이 아니다</h2>
 * <p>토너먼트에는 순위표가 없다. {@code /standings} 를 불러 봐야 빈 응답이고, 그건
 * 하루 100요청 예산에서 그냥 낭비다. 그래서 {@link CompetitionType#LEAGUE} 만 부른다.
 *
 * <p><b>하이브리드(챔피언스리그 등)도 지금은 제외한다.</b> 조별리그 표가 조마다 하나씩
 * 있는데, 서로 다른 조의 1위 둘을 같은 잣대로 "1위"라 부르면 상대 강도가 거짓이 된다.
 * 조별 비교를 제대로 하려면 조 간 보정이 필요하고 그건 별도 판단이라, 지금은 하지 않고
 * <b>안 한다고 밝힌다</b>.
 */
@Service
public class StandingSyncService {

	private static final Logger log = LoggerFactory.getLogger(StandingSyncService.class);

	private final ApiFootballClient client;
	private final StandingUpsertService upsertService;
	private final CompetitionRepository competitionRepository;

	public StandingSyncService(ApiFootballClient client,
	                           StandingUpsertService upsertService,
	                           CompetitionRepository competitionRepository) {
		this.client = client;
		this.upsertService = upsertService;
		this.competitionRepository = competitionRepository;
	}

	/**
	 * @param skipped 순위표가 없는 대회라 부르지 않았다
	 */
	public record SyncReport(long competitionId, int season, boolean skipped, String skipReason,
	                         int tables, int rows, int unknownTeams, int requestCount) {

		static SyncReport skipped(long competitionId, int season, String reason) {
			return new SyncReport(competitionId, season, true, reason, 0, 0, 0, 0);
		}
	}

	/** 순위표를 가진 대회인가 — 이 판단이 예산을 지킨다. */
	public static boolean hasStandings(Competition competition) {
		return competition.getType() == CompetitionType.LEAGUE;
	}

	public SyncReport sync(long competitionId, int season) {
		Competition competition = competitionRepository.findById(competitionId)
				.orElseThrow(() -> new NoSuchElementException("대회를 찾을 수 없습니다: " + competitionId));

		if (!hasStandings(competition)) {
			String reason = "%s 은(는) %s 라 순위표가 없습니다".formatted(
					competition.getName(), competition.getType());
			log.debug("순위표 동기화 건너뜀: {}", reason);
			return SyncReport.skipped(competitionId, season, reason);
		}

		// 예산 기록은 결장 동기화와 같은 방식이다 — 수동 실행은 SyncLog 를 남기지 않는다.
		// 스케줄러에 태울 때 SyncPlanner 를 거치게 하면서 함께 정리한다(8/21 체크리스트).
		var fetch = client.fetchStandings(competitionId, season);
		var result = upsertService.upsert(competitionId, season, fetch.items());

		log.info("순위표 동기화: {} 시즌 {} → 표 {}개 · {}줄 (모르는 팀 {}) · 요청 {}회",
				competition.getName(), season, result.tables(), result.rows(),
				result.unknownTeams(), fetch.requestCount());
		return new SyncReport(competitionId, season, false, null,
				result.tables(), result.rows(), result.unknownTeams(), fetch.requestCount());
	}
}
