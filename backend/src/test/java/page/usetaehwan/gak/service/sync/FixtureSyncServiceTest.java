package page.usetaehwan.gak.service.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.domain.SyncStatus;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.CompetitionSeeder;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 동기화 파이프라인 통합 테스트 — 저장된 응답 파일 + 인메모리 DB.
 * <b>실제 API를 호출하지 않는다.</b>
 *
 * <p>가장 중요한 검증은 <b>멱등성</b>이다. 같은 응답을 두 번 적용해도 행이 늘지 않고
 * 상태가 같아야, 실패한 동기화를 "그냥 다시 돌리는 것"만으로 복구할 수 있다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class FixtureSyncServiceTest {

	private static final long EPL = 39L;
	private static final long UCL = 2L;
	private static final long LA_LIGA = 140L;   // 재생 파일을 일부러 두지 않은 대회
	private static final long MAN_UTD = 33L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired FixtureSyncService syncService;
	@Autowired CompetitionSeeder competitionSeeder;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired VenueRepository venueRepository;
	@Autowired SyncLogRepository syncLogRepository;

	@BeforeEach
	void reset() {
		databaseCleaner.clearAllButCompetitions();
		competitionSeeder.run(null);
	}

	@Test
	@DisplayName("시드가 대회 20개를 심는다")
	void seedsTwentyCompetitions() {
		assertThat(competitionRepository.findByDisplayedTrue()).hasSize(20);
	}

	@Test
	@DisplayName("경기·팀·경기장을 함께 저장한다")
	void storesFixturesTeamsAndVenues() {
		SyncLog result = syncService.syncCompetition(EPL);

		assertThat(result.getStatus()).isEqualTo(SyncStatus.SUCCESS);
		assertThat(result.getSource()).isEqualTo(SyncSource.REPLAY);
		assertThat(result.getFixtureCount()).isEqualTo(6);
		assertThat(fixtureRepository.countByCompetitionIdAndSeason(EPL, 2024)).isEqualTo(6);

		// fixture 응답에 등장한 팀 6개 / 경기장 6개가 함께 만들어진다.
		assertThat(teamRepository.count()).isEqualTo(6);
		assertThat(venueRepository.count()).isEqualTo(6);
	}

	@Test
	@DisplayName("재생 동기화는 요청 예산을 소모하지 않는다")
	void replaySyncSpendsNoQuota() {
		SyncLog result = syncService.syncCompetition(EPL);

		assertThat(result.getRequestCount()).isZero();
		assertThat(syncLogRepository.sumRequestCountSince(result.getStartedAt().minusSeconds(1)))
				.isZero();
	}

	@Test
	@DisplayName("두 번 돌려도 행이 늘지 않는다 — upsert 멱등성")
	void syncingTwiceIsIdempotent() {
		syncService.syncCompetition(EPL);
		long fixturesAfterFirst = fixtureRepository.count();
		long teamsAfterFirst = teamRepository.count();
		long venuesAfterFirst = venueRepository.count();

		SyncLog second = syncService.syncCompetition(EPL);

		assertThat(fixtureRepository.count()).isEqualTo(fixturesAfterFirst);
		assertThat(teamRepository.count()).isEqualTo(teamsAfterFirst);
		assertThat(venueRepository.count()).isEqualTo(venuesAfterFirst);

		// 두 번째는 전부 "갱신"이었다는 증거 — 새로 만든 팀·경기장이 0건.
		assertThat(second.getFixtureCount()).isEqualTo(6);
		assertThat(second.getNewTeamCount()).isZero();
		assertThat(second.getNewVenueCount()).isZero();
	}

	@Test
	@DisplayName("상태와 스코어를 국면별로 매핑한다")
	void mapsStatusAndScores() {
		syncService.syncCompetition(EPL);

		Fixture finished = fixtureRepository.findById(1208021L).orElseThrow();
		assertThat(finished.getStatus()).isEqualTo(FixtureStatus.FT);
		assertThat(finished.isFinished()).isTrue();
		assertThat(finished.getGoalsHome()).isEqualTo(1);
		assertThat(finished.getGoalsAway()).isEqualTo(3);
		assertThat(finished.getHalftime().getHome()).isZero();
		assertThat(finished.getHalftime().getAway()).isEqualTo(1);
		// 연장·승부차기가 없었던 경기는 해당 국면 자체가 없다.
		// (JPA는 구성 요소가 전부 null인 임베더블을 null로 읽어 온다)
		assertThat(finished.getExtratime()).isNull();
		assertThat(finished.getPenalty()).isNull();

		Fixture live = fixtureRepository.findById(1208024L).orElseThrow();
		assertThat(live.getStatus()).isEqualTo(FixtureStatus.LIVE);   // API "2H"
		assertThat(live.getElapsed()).isEqualTo(67);
		assertThat(live.isFinished()).isFalse();

		Fixture notStarted = fixtureRepository.findById(1208025L).orElseThrow();
		assertThat(notStarted.getStatus()).isEqualTo(FixtureStatus.NS);
		assertThat(notStarted.getGoalsHome()).isNull();

		Fixture postponed = fixtureRepository.findById(1208026L).orElseThrow();
		assertThat(postponed.getStatus()).isEqualTo(FixtureStatus.PST);
		assertThat(postponed.isFinished()).isFalse();
	}

	@Test
	@DisplayName("킥오프를 timestamp(UTC)로 저장한다")
	void storesKickoffAsInstant() {
		syncService.syncCompetition(EPL);

		assertThat(fixtureRepository.findById(1208021L).orElseThrow().getKickoff())
				.isEqualTo("2024-08-16T19:00:00Z");
	}

	@Test
	@DisplayName("연장·승부차기까지 간 녹아웃 경기를 국면별로 저장한다")
	void storesExtraTimeAndPenalties() {
		syncService.syncCompetition(UCL);

		Fixture shootout = fixtureRepository.findById(1310003L).orElseThrow();
		assertThat(shootout.getStatus()).isEqualTo(FixtureStatus.PEN);
		assertThat(shootout.getFulltime().getHome()).isEqualTo(2);
		assertThat(shootout.getExtratime().getHome()).isEqualTo(2);
		assertThat(shootout.getPenalty().getHome()).isEqualTo(4);
		assertThat(shootout.getPenalty().getAway()).isEqualTo(5);
	}

	@Test
	@DisplayName("경기장 미정도 정상 저장한다 — 에러가 아니라 사실이다")
	void venueMayBeAbsent() {
		syncService.syncCompetition(UCL);

		Fixture undecided = fixtureRepository.findById(1310004L).orElseThrow();
		assertThat(undecided.getVenue()).isNull();
		assertThat(undecided.getRound()).isEqualTo("Quarter-finals");
	}

	@Test
	@DisplayName("시드로 팀 한글명과 경기장 좌표를 채운다 — API가 주지 않는 정보")
	void fillsSeedOnlyInformation() {
		syncService.syncCompetition(EPL);

		Team manUtd = teamRepository.findById(MAN_UTD).orElseThrow();
		assertThat(manUtd.getName()).isEqualTo("Manchester United");
		assertThat(manUtd.getNameKo()).isEqualTo("맨체스터 유나이티드");
		assertThat(manUtd.displayName()).isEqualTo("맨유");

		// 좌표는 API가 주지 않는다 → 도시명으로 시드에서 채운다.
		Venue oldTrafford = venueRepository.findById(556L).orElseThrow();
		assertThat(oldTrafford.getCity()).isEqualTo("Manchester");
		assertThat(oldTrafford.hasCoordinates()).isTrue();
		assertThat(oldTrafford.getLatitude()).isEqualTo(53.4808);
	}

	@Test
	@DisplayName("재동기화가 한글명·좌표를 지우지 않는다 — 원본이 API가 아닌 필드들")
	void resyncKeepsSeedOnlyFields() {
		syncService.syncCompetition(EPL);
		syncService.syncCompetition(EPL);

		assertThat(teamRepository.findById(MAN_UTD).orElseThrow().getNameKo())
				.isEqualTo("맨체스터 유나이티드");
		assertThat(venueRepository.findById(556L).orElseThrow().hasCoordinates()).isTrue();
	}

	@Test
	@DisplayName("code가 없는 팀은 팀명 자음으로 채운다 — /fixtures 응답에는 code가 없다")
	void derivesMissingTeamCode() {
		syncService.syncCompetition(UCL);

		// 시드에 있는 팀은 시드 코드를 쓴다.
		assertThat(teamRepository.findById(541L).orElseThrow().getCode()).isEqualTo("RMA");
		// 시드에 없는 팀도 코드가 비어 있지 않다(프론트가 code 해시로 링 컬러를 만든다).
		assertThat(teamRepository.findById(505L).orElseThrow().getCode()).isNotBlank();
	}

	@Test
	@DisplayName("한 팀의 전 대회 경기를 날짜순으로 한 번에 읽는다")
	void readsCrossCompetitionScheduleInOneQuery() {
		syncService.syncCompetition(EPL);
		syncService.syncCompetition(UCL);

		List<Fixture> schedule = fixtureRepository.findTeamScheduleWithDetails(MAN_UTD);

		// 리그 2경기(홈 1 + 원정 1) + 챔스 1경기(홈).
		assertThat(schedule).hasSize(3);
		assertThat(schedule).extracting(Fixture::getKickoff).isSorted();
		// 대회를 가로지른 하나의 일정이라는 것 — 이 앱의 존재 이유.
		assertThat(schedule).extracting(f -> f.getCompetition().getId())
				.containsExactly(EPL, EPL, UCL);

		// @EntityGraph로 함께 읽었으므로 트랜잭션 밖에서도 연관 접근이 터지지 않는다(N+1 방지의 부수 효과).
		assertThat(schedule.getFirst().getHomeTeam().getName()).isNotBlank();
		assertThat(schedule.getFirst().getCompetition().getName()).isNotBlank();
	}

	@Test
	@DisplayName("재생 파일이 없으면 이력에 남기지 않는다 — 매 주기 반복될 사실이라")
	void missingReplayDataIsNotRecorded() {
		SyncLog result = syncService.syncCompetition(LA_LIGA);

		// 결과 객체는 돌려준다(수동 실행에서 이유를 봐야 하므로) — 저장만 하지 않는다.
		assertThat(result.getStatus()).isEqualTo(SyncStatus.SKIPPED);
		assertThat(result.getMessage()).contains("fixtures-league140-season2024.json");
		assertThat(result.isRecorded()).isFalse();
		assertThat(result.getId()).isNull();

		assertThat(syncLogRepository.findByCompetitionIdOrderByStartedAtDesc(LA_LIGA)).isEmpty();
		assertThat(syncLogRepository.count()).isZero();
		assertThat(fixtureRepository.count()).isZero();
	}

	@Test
	@DisplayName("여러 번 돌려도 이력이 쌓이지 않는다 — 진짜 실패가 묻히지 않게")
	void missingReplayDataNeverAccumulates() {
		for (int i = 0; i < 5; i++) {
			syncService.syncCompetition(LA_LIGA);
		}

		assertThat(syncLogRepository.count()).isZero();
	}

	@Test
	@DisplayName("데이터 없는 대회가 다른 대회의 동기화를 막지 않는다")
	void missingDataDoesNotBlockOthers() {
		syncService.syncCompetition(LA_LIGA);   // 재생 파일 없음
		SyncLog ok = syncService.syncCompetition(EPL);

		assertThat(ok.getStatus()).isEqualTo(SyncStatus.SUCCESS);
		assertThat(ok.isRecorded()).isTrue();
		// 이력에는 성공한 대회만 남는다.
		assertThat(syncLogRepository.findAll())
				.extracting(SyncLog::getCompetitionId)
				.containsExactly(EPL);
	}

	@Test
	@DisplayName("데이터를 채운 뒤 그냥 다시 돌리면 들어온다 — 재시도 큐가 필요 없는 이유")
	void retryJustWorks() {
		syncService.syncCompetition(LA_LIGA);
		assertThat(fixtureRepository.count()).isZero();

		syncService.syncCompetition(EPL);
		syncService.syncCompetition(EPL);   // 중복 반영이 아니라 갱신

		assertThat(fixtureRepository.countByCompetitionIdAndSeason(EPL, 2024)).isEqualTo(6);
	}

	@Test
	@DisplayName("이력에 언제·무엇을·몇 요청 썼는지가 남는다")
	void historyRecordsWhatWasSynced() {
		syncService.syncCompetition(EPL);

		SyncLog log = syncLogRepository.findByCompetitionIdOrderByStartedAtDesc(EPL).getFirst();
		assertThat(log.getCompetitionId()).isEqualTo(EPL);
		assertThat(log.getSeason()).isEqualTo(2024);
		assertThat(log.getStartedAt()).isNotNull();
		assertThat(log.getFinishedAt()).isNotNull();
		assertThat(log.elapsed().isNegative()).isFalse();
		assertThat(log.getSource()).isEqualTo(SyncSource.REPLAY);
		assertThat(log.getRequestCount()).isZero();
		assertThat(log.getNewTeamCount()).isEqualTo(6);
		assertThat(log.getNewVenueCount()).isEqualTo(6);
	}
}
