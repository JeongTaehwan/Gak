package page.usetaehwan.gak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.prediction.PredictionAccuracy;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 적중률 집계.
 *
 * <p>여기서 지키는 것은 하나다 — <b>앱이 스스로를 과장하지 않는 것.</b> 3번 맞히고
 * "적중률 100%"라고 적는 순간 이 앱이 파는 숫자가 의미를 잃는다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class PredictionAccuracyServiceTest {

	private static final Instant KICKOFF = Instant.parse("2024-08-17T14:00:00Z");

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired PredictionAccuracyService accuracyService;
	@Autowired PredictionScoringService scoringService;
	@Autowired PredictionRepository predictionRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired CompetitionRepository competitionRepository;

	private Team home;
	private Team away;
	private Competition league;

	@BeforeEach
	void reset() {
		databaseCleaner.clearAllButCompetitions();
		league = competitionRepository.save(Competition.builder()
				.id(39L).name("Premier League").nameKo("프리미어리그").shortNameKo("리그")
				.country("England").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).build());
		home = teamRepository.save(Team.builder().id(1L).name("Home FC").nameKo("홈").build());
		away = teamRepository.save(Team.builder().id(2L).name("Away FC").nameKo("원정").build());
	}

	/** 홈 승(2-0) 경기 하나 + 그에 대한 예측. 예측은 킥오프 한 시간 전에 남긴 것으로. */
	private void predict(long fixtureId, FixtureStatus status, Integer gh, Integer ga, Pick pick) {
		Fixture fixture = fixtureRepository.save(Fixture.builder()
				.id(fixtureId).competition(league).season(2024).round("Regular Season - 1")
				.homeTeam(home).awayTeam(away)
				.kickoff(KICKOFF.plus(fixtureId, ChronoUnit.DAYS))
				.status(status).goalsHome(gh).goalsAway(ga).build());
		predictionRepository.save(Prediction.create(fixture, home, pick,
				fixture.getKickoff().minus(1, ChronoUnit.HOURS)));
	}

	@Test
	@DisplayName("예측이 없으면 '적중률 0%'가 아니라 '기록 없음'이다")
	void noPredictionsIsNotZeroPercent() {
		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.scored()).isZero();
		assertThat(a.hitRate()).isNull();
		assertThat(a.confidence()).isEqualTo(SampleConfidence.NONE);
		assertThat(a.recent()).isEmpty();
	}

	@Test
	@DisplayName("표본이 5건 미만이면 적중률을 내지 않고 개수만 준다")
	void suppressesRateOnSmallSamples() {
		predict(1L, FixtureStatus.FT, 2, 0, Pick.W);   // 적중
		predict(2L, FixtureStatus.FT, 2, 0, Pick.W);   // 적중
		predict(3L, FixtureStatus.FT, 0, 2, Pick.W);   // 빗나감
		scoringService.scorePending();

		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.scored()).isEqualTo(3);
		assertThat(a.hits()).isEqualTo(2);
		assertThat(a.misses()).isEqualTo(1);
		// 2/3 을 "66.7%"로 적으면 소수점이 표본의 빈약함을 가린다
		assertThat(a.hitRate()).isNull();
		assertThat(a.confidence()).isEqualTo(SampleConfidence.LOW);
	}

	@Test
	@DisplayName("표본이 충분하면 적중률을 낸다")
	void computesRateOnceSampleIsEnough() {
		for (long i = 1; i <= 4; i++) {
			predict(i, FixtureStatus.FT, 2, 0, Pick.W);   // 적중 4
		}
		predict(5L, FixtureStatus.FT, 0, 2, Pick.W);       // 빗나감 1
		scoringService.scorePending();

		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.scored()).isEqualTo(5);
		assertThat(a.hitRate()).isEqualTo(0.8);
		assertThat(a.confidence()).isEqualTo(SampleConfidence.MODERATE);
	}

	@Test
	@DisplayName("미채점 예측은 분모에 넣지 않고 따로 센다")
	void pendingPredictionsDoNotMoveTheRate() {
		for (long i = 1; i <= 5; i++) {
			predict(i, FixtureStatus.FT, 2, 0, Pick.W);
		}
		scoringService.scorePending();
		double before = accuracyService.of(home.getId(), 20).hitRate();

		// 아직 안 치른 경기에 예측을 더 남긴다 — 적중률이 흔들리면 안 된다
		predict(6L, FixtureStatus.NS, null, null, Pick.L);
		predict(7L, FixtureStatus.NS, null, null, Pick.D);

		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.hitRate()).isEqualTo(before);
		assertThat(a.scored()).isEqualTo(5);
		assertThat(a.pending()).isEqualTo(2);
	}

	@Test
	@DisplayName("예측값별 성적을 따로 준다 — 전체 적중률 하나로는 편향이 안 보인다")
	void breaksDownByPick() {
		predict(1L, FixtureStatus.FT, 2, 0, Pick.W);   // W 적중
		predict(2L, FixtureStatus.FT, 2, 0, Pick.W);   // W 적중
		predict(3L, FixtureStatus.FT, 2, 0, Pick.D);   // D 빗나감
		predict(4L, FixtureStatus.FT, 1, 1, Pick.D);   // D 적중
		scoringService.scorePending();

		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.byPick().get(Pick.W).predicted()).isEqualTo(2);
		assertThat(a.byPick().get(Pick.W).hits()).isEqualTo(2);
		assertThat(a.byPick().get(Pick.D).predicted()).isEqualTo(2);
		assertThat(a.byPick().get(Pick.D).hits()).isEqualTo(1);
		// 갈래별로도 표본이 작으면 비율은 감춘다
		assertThat(a.byPick().get(Pick.W).hitRate()).isNull();
	}

	@Test
	@DisplayName("기록에 '킥오프 몇 분 전에 남겼는지'가 함께 온다 — 규칙이 지켜졌음을 보여 준다")
	void recordsCarryLeadTime() {
		predict(1L, FixtureStatus.FT, 2, 0, Pick.W);
		scoringService.scorePending();

		var record = accuracyService.of(home.getId(), 20).recent().get(0);

		assertThat(record.leadTimeMinutes()).isEqualTo(60);
		assertThat(record.pick()).isEqualTo(Pick.W);
		assertThat(record.isHit()).isTrue();
		assertThat(record.opponentName()).isEqualTo("원정");
		assertThat(record.home()).isTrue();
	}

	@Test
	@DisplayName("집계는 채점하지 않는다 — 조회 시점이 기록을 바꾸면 안 된다")
	void queryingDoesNotScore() {
		predict(1L, FixtureStatus.FT, 2, 0, Pick.W);

		PredictionAccuracy a = accuracyService.of(home.getId(), 20);

		assertThat(a.scored()).isZero();
		assertThat(a.pending()).isEqualTo(1);
		assertThat(predictionRepository.findByIsHitNotNull()).isEmpty();
	}

	@Test
	@DisplayName("없는 팀은 예외")
	void unknownTeamIsRejected() {
		assertThatThrownBy(() -> accuracyService.of(999999L, 20))
				.isInstanceOf(NoSuchElementException.class);
	}
}
