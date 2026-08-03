package page.usetaehwan.gak.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
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
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.service.PredictionScoringService.ScoringReport;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 채점 배치. 예측 생성이 "킥오프 이전에만"이라면 채점은 그 반대쪽 짝이고, 둘이 다 있어야
 * 적중률이라는 숫자가 성립한다.
 *
 * <p>여기서 못박는 것은 세 가지다 — <b>주어(어느 팀 관점인가)</b>를 지키는지,
 * <b>모르는 것을 틀린 것으로 만들지 않는지</b>, 그리고 <b>몇 번을 돌려도 같은지</b>.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class PredictionScoringServiceTest {

	private static final long EPL = 39L;

	@Autowired DatabaseCleaner databaseCleaner;
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
				.id(EPL).name("Premier League").nameKo("프리미어리그").shortNameKo("리그")
				.country("England").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).build());
		home = teamRepository.save(Team.builder().id(1L).name("Home FC").build());
		away = teamRepository.save(Team.builder().id(2L).name("Away FC").build());
	}

	// --- 도우미 --------------------------------------------------------------

	private static final Instant KICKOFF = Instant.parse("2024-08-17T14:00:00Z");

	private Fixture saveFixture(long id, FixtureStatus status, Integer goalsHome, Integer goalsAway) {
		return fixtureRepository.save(Fixture.builder()
				.id(id).competition(league).season(2024).round("Regular Season - 1")
				.homeTeam(home).awayTeam(away).kickoff(KICKOFF)
				.status(status).goalsHome(goalsHome).goalsAway(goalsAway)
				.build());
	}

	/** 킥오프 이전 규칙을 우회하지 않고 예측을 만든다 — 한 시간 전에 남긴 것으로. */
	private Prediction savePrediction(Fixture fixture, Team team, Pick pick) {
		return predictionRepository.save(
				Prediction.create(fixture, team, pick, KICKOFF.minus(1, ChronoUnit.HOURS)));
	}

	// =========================================================================

	@Nested
	@DisplayName("채점")
	class Scoring {

		@Test
		@DisplayName("끝난 경기의 실제 결과로 적중 여부를 매긴다")
		void scoresFinishedFixtures() {
			Fixture fixture = saveFixture(100L, FixtureStatus.FT, 2, 0); // 홈 승
			savePrediction(fixture, home, Pick.W);   // 적중
			savePrediction(fixture, home, Pick.D);   // 빗나감

			ScoringReport report = scoringService.scorePending();

			assertThat(report.scored()).isEqualTo(2);
			assertThat(report.hits()).isEqualTo(1);
			assertThat(report.misses()).isEqualTo(1);
			assertThat(predictionRepository.findByIsHitNotNull()).hasSize(2);
		}

		@Test
		@DisplayName("같은 경기라도 팀에 따라 승패가 뒤집힌다 — 주어를 지킨다")
		void resultDependsOnWhichTeamThePredictionWasAbout() {
			Fixture fixture = saveFixture(101L, FixtureStatus.FT, 2, 0); // 홈 2-0 승
			Prediction homeSideWin = savePrediction(fixture, home, Pick.W);
			Prediction awaySideWin = savePrediction(fixture, away, Pick.W);

			scoringService.scorePending();

			// 홈 관점 W 는 적중, 원정 관점 W 는 빗나감. 주어가 없으면 둘 다 같게 채점된다.
			assertThat(predictionRepository.findById(homeSideWin.getId()).orElseThrow())
					.satisfies(p -> {
						assertThat(p.getResolvedResult()).isEqualTo(Pick.W);
						assertThat(p.getIsHit()).isTrue();
					});
			assertThat(predictionRepository.findById(awaySideWin.getId()).orElseThrow())
					.satisfies(p -> {
						assertThat(p.getResolvedResult()).isEqualTo(Pick.L);
						assertThat(p.getIsHit()).isFalse();
					});
		}

		@Test
		@DisplayName("승부차기로 갈린 경기는 무승부로 채점한다 — 폼 집계와 같은 규칙")
		void shootoutScoresAsDraw() {
			// 120분 2-2 후 PK 로 갈렸다. goals 는 연장까지의 스코어다.
			Fixture fixture = saveFixture(102L, FixtureStatus.PEN, 2, 2);
			savePrediction(fixture, home, Pick.D);
			savePrediction(fixture, home, Pick.W);

			scoringService.scorePending();

			assertThat(predictionRepository.findAll())
					.extracting(Prediction::getResolvedResult)
					.containsOnly(Pick.D);
			assertThat(scoringService.scorePending().scored()).isZero();
		}
	}

	@Nested
	@DisplayName("건드리지 않는 것")
	class LeftAlone {

		@Test
		@DisplayName("아직 안 끝난 경기는 채점하지 않는다")
		void ignoresUnfinishedFixtures() {
			savePrediction(saveFixture(200L, FixtureStatus.NS, null, null), home, Pick.W);
			savePrediction(saveFixture(201L, FixtureStatus.LIVE, 1, 0), home, Pick.W);

			ScoringReport report = scoringService.scorePending();

			assertThat(report.candidates()).isZero();
			assertThat(predictionRepository.findByIsHitNotNull()).isEmpty();
		}

		@Test
		@DisplayName("상태는 종료인데 득점이 아직 없으면 보류한다 — 모르는 걸 '틀렸다'로 만들지 않는다")
		void defersWhenGoalsHaveNotArrivedYet() {
			// 동기화가 상태를 먼저 받고 득점이 비어 오는 순간이 실제로 있다.
			Fixture fixture = saveFixture(202L, FixtureStatus.FT, null, null);
			Prediction prediction = savePrediction(fixture, home, Pick.W);

			ScoringReport report = scoringService.scorePending();

			assertThat(report.scored()).isZero();
			assertThat(report.deferred()).isEqualTo(1);
			// isHit=false 로 굳어 버리면 영영 "빗나간 예측"으로 남는다
			assertThat(predictionRepository.findById(prediction.getId()).orElseThrow())
					.satisfies(p -> {
						assertThat(p.getIsHit()).isNull();
						assertThat(p.isScored()).isFalse();
					});
		}

		@Test
		@DisplayName("득점이 들어오면 다음 회차가 그대로 집어 간다")
		void deferredPredictionIsPickedUpOnceResultArrives() {
			Fixture fixture = saveFixture(203L, FixtureStatus.FT, null, null);
			savePrediction(fixture, home, Pick.W);
			assertThat(scoringService.scorePending().deferred()).isEqualTo(1);

			// 다음 동기화가 득점을 채워 넣었다.
			fixture.applyApiFacts(league, 2024, "Regular Season - 1", home, away, null,
					KICKOFF, FixtureStatus.FT, 90, 3, 1, null, null, null, null);
			fixtureRepository.save(fixture);

			ScoringReport report = scoringService.scorePending();

			assertThat(report.scored()).isEqualTo(1);
			assertThat(report.hits()).isEqualTo(1);
		}
	}

	@Nested
	@DisplayName("멱등성")
	class Idempotence {

		@Test
		@DisplayName("몇 번을 돌려도 결과가 같다 — 두 번째부터는 채점할 게 없다")
		void reRunningScoresNothingNew() {
			Fixture fixture = saveFixture(300L, FixtureStatus.FT, 1, 0);
			savePrediction(fixture, home, Pick.W);

			assertThat(scoringService.scorePending().scored()).isEqualTo(1);
			assertThat(scoringService.scorePending().scored()).isZero();
			assertThat(scoringService.scorePending().candidates()).isZero();
		}

		@Test
		@DisplayName("이미 채점된 예측은 결과가 바뀌어도 다시 매기지 않는다")
		void doesNotReScoreAfterResultChanges() {
			Fixture fixture = saveFixture(301L, FixtureStatus.FT, 1, 0); // 홈 승
			Prediction prediction = savePrediction(fixture, home, Pick.W);
			scoringService.scorePending();
			assertThat(predictionRepository.findById(prediction.getId()).orElseThrow().getIsHit())
					.isTrue();

			// 나중에 API 가 스코어를 정정했다(0-1 로 뒤집힘).
			fixture.applyApiFacts(league, 2024, "Regular Season - 1", home, away, null,
					KICKOFF, FixtureStatus.FT, 90, 0, 1, null, null, null, null);
			fixtureRepository.save(fixture);

			scoringService.scorePending();

			// 한 번 매긴 성적은 그대로 둔다. 나중에 조용히 뒤집히는 쪽이 더 나쁘다.
			assertThat(predictionRepository.findById(prediction.getId()).orElseThrow())
					.satisfies(p -> {
						assertThat(p.getIsHit()).isTrue();
						assertThat(p.getResolvedResult()).isEqualTo(Pick.W);
					});
		}
	}

	@Nested
	@DisplayName("예측 생성 규칙 — 채점의 전제")
	class CreationRules {

		@Test
		@DisplayName("킥오프 이후에는 예측을 만들 수 없다")
		void cannotPredictAfterKickoff() {
			Fixture fixture = saveFixture(400L, FixtureStatus.NS, null, null);

			assertThatThrownBy(() -> Prediction.create(fixture, home, Pick.W, KICKOFF))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("킥오프 이전");
			assertThatThrownBy(() ->
					Prediction.create(fixture, home, Pick.W, KICKOFF.plusSeconds(1)))
					.isInstanceOf(IllegalArgumentException.class);
		}

		@Test
		@DisplayName("그 경기에 뛰지 않는 팀으로는 예측할 수 없다 — 영영 채점 안 되는 기록을 막는다")
		void cannotPredictForATeamThatIsNotPlaying() {
			Fixture fixture = saveFixture(401L, FixtureStatus.NS, null, null);
			Team outsider = teamRepository.save(
					Team.builder().id(99L).name("Somewhere Else FC").build());

			assertThatThrownBy(() ->
					Prediction.create(fixture, outsider, Pick.W, KICKOFF.minusSeconds(60)))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("뛰지 않는 팀");
		}

		@Test
		@DisplayName("결과 없이 채점하지 않는다")
		void cannotResolveWithoutAResult() {
			Fixture fixture = saveFixture(402L, FixtureStatus.NS, null, null);
			Prediction prediction = savePrediction(fixture, home, Pick.W);

			assertThatThrownBy(() -> prediction.resolve(null))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("집계 입력")
	class Aggregation {

		@Test
		@DisplayName("팀별 채점 완료 예측을 킥오프 순으로 준다")
		void listsScoredPredictionsPerTeam() {
			savePrediction(saveFixture(500L, FixtureStatus.FT, 2, 0), home, Pick.W);
			savePrediction(saveFixture(501L, FixtureStatus.FT, 0, 2), away, Pick.W);
			savePrediction(saveFixture(502L, FixtureStatus.NS, null, null), home, Pick.W);

			scoringService.scorePending();

			List<Prediction> homeScored = predictionRepository.findScoredByTeam(home.getId());
			assertThat(homeScored).hasSize(1);
			assertThat(homeScored.get(0).getIsHit()).isTrue();
			assertThat(predictionRepository.findScoredByTeam(away.getId())).hasSize(1);
		}
	}
}
