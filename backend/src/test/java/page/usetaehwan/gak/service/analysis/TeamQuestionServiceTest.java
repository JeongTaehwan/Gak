package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.TeamAnswer;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 자유 질문 — <b>답할 수 없을 때 무엇이라고 말하는가.</b>
 *
 * <p>테스트 환경에는 Anthropic 키가 없다({@code application-test.yml}). 그래서 여기서
 * 검증하는 것은 모델의 답이 아니라 <b>모델을 부르기 전후에 코드가 무엇을 지키는가</b>다 —
 * 부를 자격이 없는 요청을 걸러 내는가, 부르지 못했을 때 성공한 척하지 않는가, 분모를
 * 응답에 싣는가. 모델이 있는 환경에서만 되는 검증에 기대면 이 규칙들은 CI에서 사라진다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class TeamQuestionServiceTest {

	private static final long MAN_UTD = 33L;
	private static final long OTHER = 100L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired TeamQuestionService questionService;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired FixtureRepository fixtureRepository;

	private Competition epl;
	private Competition faCup;
	private Team manUtd;
	private Team other;

	@BeforeEach
	void seed() {
		databaseCleaner.clearAllButCompetitions();

		epl = competitionRepository.save(Competition.builder()
				.id(39L).name("Premier League").nameKo("프리미어리그").shortNameKo("리그")
				.country("England").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).selectable(true).build());
		faCup = competitionRepository.save(Competition.builder()
				.id(45L).name("FA Cup").nameKo("FA컵").shortNameKo("FA컵")
				.country("England").type(CompetitionType.CUP)
				.calendarSeason(false).displayed(true).selectable(false).build());

		manUtd = teamRepository.save(
				Team.builder().id(MAN_UTD).name("Manchester United").nameKo("맨유").code("MUN").build());
		other = teamRepository.save(
				Team.builder().id(OTHER).name("Other FC").nameKo("다른팀").build());
	}

	@Test
	@DisplayName("그 시즌 경기가 없으면 부르지 않고 '근거 데이터 부족'이라고 말한다")
	void noDataForThatSeasonIsInsufficientData() {
		TeamAnswer answer = questionService.answer(MAN_UTD, 2019, "왜 부진한가요?");

		assertThat(answer.status()).isEqualTo(TeamAnswer.Status.INSUFFICIENT_DATA);
		assertThat(answer.answer()).isNull();
		assertThat(answer.evidence()).isEmpty();
		assertThat(answer.statusMessage()).isNotBlank();
	}

	@Test
	@DisplayName("컵 경기만 있는 시즌은 판정 불가 — 컵 몇 경기로 시즌을 진단하지 않는다")
	void cupOnlySeasonIsNotDiagnosable() {
		fixture(faCup, 1L, 2023, Instant.parse("2024-01-08T19:00:00Z"), FixtureStatus.FT);

		TeamAnswer answer = questionService.answer(MAN_UTD, 2023, "왜 부진한가요?");

		assertThat(answer.status()).isEqualTo(TeamAnswer.Status.INSUFFICIENT_DATA);
		assertThat(answer.statusMessage()).contains("1부 리그");
		assertThat(answer.basis().leagueRecord()).isFalse();
	}

	@Test
	@DisplayName("일정만 있고 아직 아무것도 안 치렀으면 '판정할 수 없다' — 성적이 나쁜 게 아니다")
	void scheduleWithoutPlayedMatchesIsNotBadForm() {
		fixture(epl, 2L, 2023, Instant.now().plus(30, ChronoUnit.DAYS), FixtureStatus.NS);

		TeamAnswer answer = questionService.answer(MAN_UTD, 2023, "왜 부진한가요?");

		assertThat(answer.status()).isEqualTo(TeamAnswer.Status.INSUFFICIENT_DATA);
		assertThat(answer.basis().analyzedFixtures()).isZero();
		// 안 치른 경기를 무승부·패배로 접지 않았다는 사실이 분모에 그대로 보인다.
		assertThat(answer.basis().upcomingFixtures()).isEqualTo(1);
	}

	@Test
	@DisplayName("분석을 못 불렀으면 실패라고 말한다 — 답을 지어내지 않는다")
	void missingAnalyzerIsReportedAsFailureNotAnAnswer() {
		fixture(epl, 3L, 2023, Instant.parse("2023-08-14T19:00:00Z"), FixtureStatus.FT);

		TeamAnswer answer = questionService.answer(MAN_UTD, 2023, "왜 부진한가요?");

		// 키가 없는 테스트 환경이다. 근거 데이터 부족(기다리면 되는 것)과 구분돼야 한다.
		assertThat(answer.status()).isEqualTo(TeamAnswer.Status.ANALYSIS_FAILED);
		assertThat(answer.answer()).isNull();
	}

	@Test
	@DisplayName("답하지 못해도 분모는 함께 온다 — 무엇을 보고 못 답했는지가 보여야 한다")
	void basisTravelsWithEveryAnswer() {
		fixture(epl, 4L, 2023, Instant.parse("2023-08-14T19:00:00Z"), FixtureStatus.FT);
		fixture(epl, 5L, 2023, Instant.now().plus(30, ChronoUnit.DAYS), FixtureStatus.NS);

		TeamAnswer answer = questionService.answer(MAN_UTD, 2023, "왜 부진한가요?");

		assertThat(answer.basis().season()).isEqualTo(2023);
		assertThat(answer.basis().seasonFixtures()).isEqualTo(2);
		assertThat(answer.basis().analyzedFixtures()).isEqualTo(1);
		assertThat(answer.basis().upcomingFixtures()).isEqualTo(1);
		assertThat(answer.basis().leagueRecord()).isTrue();
	}

	@Test
	@DisplayName("같은 질문이라도 시즌이 다르면 다른 기간을 본다")
	void seasonDecidesWhatTheAnswerLooksAt() {
		fixture(epl, 6L, 2022, Instant.parse("2022-08-06T14:00:00Z"), FixtureStatus.FT);
		fixture(epl, 7L, 2023, Instant.parse("2023-08-14T19:00:00Z"), FixtureStatus.FT);
		fixture(epl, 8L, 2023, Instant.parse("2023-08-21T19:00:00Z"), FixtureStatus.FT);

		assertThat(questionService.answer(MAN_UTD, 2022, "왜 부진한가요?").basis().seasonFixtures())
				.isEqualTo(1);
		assertThat(questionService.answer(MAN_UTD, 2023, "왜 부진한가요?").basis().seasonFixtures())
				.isEqualTo(2);
	}

	private void fixture(Competition competition, long id, int season,
	                     Instant kickoff, FixtureStatus status) {
		fixtureRepository.save(Fixture.builder()
				.id(id).competition(competition).season(season).round("Regular Season - 1")
				.homeTeam(manUtd).awayTeam(other).kickoff(kickoff).status(status)
				.goalsHome(status == FixtureStatus.FT ? 1 : null)
				.goalsAway(status == FixtureStatus.FT ? 0 : null)
				.build());
	}
}
