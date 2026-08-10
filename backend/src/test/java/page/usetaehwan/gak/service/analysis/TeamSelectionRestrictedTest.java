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
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.TeamSelection;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 1차 비공개 검증 — <b>맨체스터 유나이티드 하나만</b> 고를 수 있다.
 *
 * <p>기본 설정({@code gak.teams.allowed-team-ids: 33})을 그대로 쓴다. 여기서 확인하는 것은
 * 두 가지다 — 파생 계산이 여러 팀을 내놓아도 게이트가 하나로 좁힌다는 것, 그리고
 * <b>게이트가 좁히기만 한다</b>는 것(허용 목록에 있다고 그 시즌 기록이 없는 팀이 생기지 않는다).
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
class TeamSelectionRestrictedTest {

	private static final long MAN_UTD = 33L;
	private static final long OTHER = 100L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired TeamSelectionService selectionService;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired FixtureRepository fixtureRepository;

	@BeforeEach
	void seed() {
		databaseCleaner.clearAllButCompetitions();

		Competition epl = competitionRepository.save(Competition.builder()
				.id(39L).name("Premier League").nameKo("프리미어리그").shortNameKo("리그")
				.country("England").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).selectable(true).build());

		Team manUtd = teamRepository.save(
				Team.builder().id(MAN_UTD).name("Manchester United").nameKo("맨유").code("MUN").build());
		Team other = teamRepository.save(
				Team.builder().id(OTHER).name("Other FC").nameKo("다른팀").build());

		fixtureRepository.save(Fixture.builder()
				.id(1L).competition(epl).season(2023).round("Regular Season - 1")
				.homeTeam(manUtd).awayTeam(other)
				.kickoff(Instant.parse("2023-08-14T19:00:00Z"))
				.status(FixtureStatus.FT).goalsHome(1).goalsAway(0).build());
	}

	@Test
	@DisplayName("파생 계산은 두 팀을 내놓지만 1차 검증에서는 맨유만 고를 수 있다")
	void onlyManchesterUnitedIsSelectable() {
		TeamSelection s = selectionService.resolve(2023, MAN_UTD);

		assertThat(s.restricted()).isTrue();
		assertThat(s.teams()).extracting(TeamSelection.Option::teamId).containsExactly(MAN_UTD);
		assertThat(s.teams()).extracting(TeamSelection.Option::name).containsExactly("맨유");
	}

	@Test
	@DisplayName("허용 목록은 좁히기만 한다 — 그 시즌 기록이 없으면 목록에도 없다")
	void allowListNeverAddsATeam() {
		// 2022에는 이 팀의 선택 기준 대회 경기가 없다. 33이 허용 목록에 있어도 뜨지 않는다.
		TeamSelection s = selectionService.resolve(2022, MAN_UTD);

		assertThat(s.teams()).isEmpty();
		assertThat(s.selected().teamId()).isEqualTo(MAN_UTD);
		assertThat(s.selected().eligible()).isFalse();
	}
}
