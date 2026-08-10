package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
import page.usetaehwan.gak.domain.Standing;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.StandingsTable;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.StandingRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 순위표는 <b>조회 시즌의</b> 표다.
 *
 * <p>예전에는 "그 팀의 가장 최근 리그 경기"로 시즌을 정했다. 그래서 2022 회고를 보면서
 * 순위표 탭을 열면 2023 표가 떴다 — 에러는 없고 표는 멀쩡하고 옆 화면과 다른 해를 말한다.
 * 여기서 지키는 것은 그 대체가 다시는 일어나지 않는다는 것이다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class StandingsQueryServiceTest {

	private static final long MAN_UTD = 33L;
	private static final long OTHER = 100L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired StandingsQueryService standingsQueryService;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired StandingRepository standingRepository;

	private Competition epl;
	private Team manUtd;
	private Team other;

	@BeforeEach
	void seed() {
		databaseCleaner.clearAllButCompetitions();

		epl = competitionRepository.save(Competition.builder()
				.id(39L).name("Premier League").nameKo("프리미어리그").shortNameKo("리그")
				.country("England").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).selectable(true).build());
		Competition faCup = competitionRepository.save(Competition.builder()
				.id(45L).name("FA Cup").nameKo("FA컵").shortNameKo("FA컵")
				.country("England").type(CompetitionType.CUP)
				.calendarSeason(false).displayed(true).selectable(false).build());

		manUtd = teamRepository.save(
				Team.builder().id(MAN_UTD).name("Manchester United").nameKo("맨유").code("MUN").build());
		other = teamRepository.save(
				Team.builder().id(OTHER).name("Other FC").nameKo("다른팀").build());

		// 2023 리그 경기 + 그 시즌 순위표
		league(1L, 2023, Instant.parse("2023-08-14T19:00:00Z"));
		standing(2023, 1, manUtd, 86);
		standing(2023, 2, other, 75);

		// 2022 에는 컵 경기만 있다 — 리그 순위표를 볼 근거가 없다.
		fixtureRepository.save(Fixture.builder()
				.id(2L).competition(faCup).season(2022).round("Round of 32")
				.homeTeam(manUtd).awayTeam(other)
				.kickoff(Instant.parse("2023-01-08T19:00:00Z"))
				.status(FixtureStatus.FT).goalsHome(2).goalsAway(1).build());
	}

	@Test
	@DisplayName("조회 시즌의 표를 준다")
	void returnsTheRequestedSeasonsTable() {
		StandingsTable table = standingsQueryService.forTeam(MAN_UTD, 2023);

		assertThat(table.available()).isTrue();
		assertThat(table.season()).isEqualTo(2023);
		assertThat(table.rows()).hasSize(2);
		assertThat(table.rows().get(0).highlighted()).isTrue();
	}

	@Test
	@DisplayName("그 시즌 리그 기록이 없으면 다른 시즌 표로 대체하지 않는다")
	void neverSubstitutesAnotherSeason() {
		StandingsTable table = standingsQueryService.forTeam(MAN_UTD, 2022);

		assertThat(table.available()).isFalse();
		assertThat(table.rows()).isEmpty();
		assertThat(table.season()).isNull();
		// 왜 없는지를 말한다 — 빈 표를 그리면 "순위 없음"인지 "0위"인지 알 수 없다.
		assertThat(table.unavailableReason()).contains("이 시즌");
	}

	@Test
	@DisplayName("리그 경기는 있는데 표를 아직 못 받았으면 그 사실을 말한다")
	void tellsWhenTableIsNotSyncedYet() {
		league(3L, 2021, Instant.parse("2021-08-14T19:00:00Z"));

		StandingsTable table = standingsQueryService.forTeam(MAN_UTD, 2021);

		assertThat(table.available()).isFalse();
		assertThat(table.unavailableReason()).contains("동기화");
	}

	private void league(long fixtureId, int season, Instant kickoff) {
		fixtureRepository.save(Fixture.builder()
				.id(fixtureId).competition(epl).season(season).round("Regular Season - 1")
				.homeTeam(manUtd).awayTeam(other).kickoff(kickoff)
				.status(FixtureStatus.FT).goalsHome(1).goalsAway(0).build());
	}

	private void standing(int season, int rank, Team team, int points) {
		standingRepository.save(Standing.builder()
				.competition(epl).season(season).team(team).rank(rank)
				.played(38).points(points).goalsFor(0).goalsAgainst(0)
				.updatedAt(Instant.parse("2024-05-20T00:00:00Z")).build());
	}
}
