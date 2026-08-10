package page.usetaehwan.gak.service.analysis;

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
import org.springframework.test.context.TestPropertySource;
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
 * 선택 가능 팀과 시즌 — <b>둘 다 저장하지 않고 경기에서 파생 계산한다.</b>
 *
 * <p>여기서 지키는 것은 두 가지다.
 * <ol>
 *   <li><b>과거를 현재 소속으로 덮어쓰지 않는다.</b> 강등된 팀의 과거 시즌을 볼 수 있어야
 *       회고가 성립한다.</li>
 *   <li><b>고른 팀을 조용히 바꾸지 않는다.</b> 그 시즌 선택 대상이 아니어도 팀은 그대로 두고
 *       "기록이 없다"고 말한다.</li>
 * </ol>
 *
 * <p>출시 단계 제한(1차 비공개 검증)은 꺼 두고 <b>파생 규칙 자체</b>를 본다. 제한이 켜진
 * 동작은 {@code TeamSelectionRestrictedTest} 가 따로 검증한다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "gak.teams.allowed-team-ids=")
class TeamSelectionServiceTest {

	private static final long MAN_UTD = 33L;
	private static final long PROMOTED = 100L;   // 2023에 올라온 팀
	private static final long RELEGATED = 200L;  // 2022에 있다가 내려간 팀
	private static final long EUROPE_ONLY = 300L; // 유럽대항전만 뛴 팀
	private static final long CUP_ONLY = 400L;    // 컵만 뛴 팀

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired TeamSelectionService selectionService;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired FixtureRepository fixtureRepository;

	private long nextFixtureId = 1;

	@BeforeEach
	void seed() {
		databaseCleaner.clearAllButCompetitions();
		nextFixtureId = 1;

		Competition epl = competition(39L, "Premier League", CompetitionType.LEAGUE, true);
		Competition ucl = competition(2L, "UEFA Champions League", CompetitionType.HYBRID, false);
		Competition faCup = competition(45L, "FA Cup", CompetitionType.CUP, false);

		team(MAN_UTD, "Manchester United", "맨유");
		team(PROMOTED, "Promoted FC", "승격");
		team(RELEGATED, "Relegated FC", "강등");
		team(EUROPE_ONLY, "Europe Only FC", "유럽");
		team(CUP_ONLY, "Cup Only FC", "컵");

		// 2022 — 맨유와 '강등' 이 1부에 있었다.
		fixture(epl, 2022, MAN_UTD, RELEGATED, Instant.parse("2022-08-06T14:00:00Z"), FixtureStatus.FT);

		// 2023 — '강등' 은 내려가고 '승격' 이 올라왔다.
		fixture(epl, 2023, MAN_UTD, PROMOTED, Instant.parse("2023-08-14T19:00:00Z"), FixtureStatus.FT);

		// 선택 기준이 아닌 대회들 — 목록에 영향을 주면 안 된다.
		fixture(ucl, 2023, MAN_UTD, EUROPE_ONLY, Instant.parse("2023-09-20T19:00:00Z"), FixtureStatus.FT);
		fixture(faCup, 2023, MAN_UTD, CUP_ONLY, Instant.parse("2024-01-08T19:00:00Z"), FixtureStatus.FT);

		// 2024 — 일정만 들어왔고 아직 아무도 안 치렀다(프리시즌에 흔한 상태).
		fixture(epl, 2024, MAN_UTD, PROMOTED,
				Instant.now().plus(30, ChronoUnit.DAYS), FixtureStatus.NS);
	}

	@Test
	@DisplayName("현재 시즌은 치른 경기가 있는 시즌 중 가장 큰 값 — 일정만 있는 시즌으로 넘어가지 않는다")
	void currentSeasonIgnoresFixtureOnlySeasons() {
		TeamSelection s = selectionService.resolve(null, MAN_UTD);

		// 2024 경기가 DB에 있지만 아직 하나도 안 치렀다. 넘어가면 화면이 통째로 빈다.
		assertThat(s.currentSeason()).isEqualTo(2023);
		assertThat(s.season()).isEqualTo(2023);
		assertThat(s.current()).isTrue();
	}

	@Test
	@DisplayName("선택 가능 팀은 조회 시즌에 선택 기준 대회 경기가 있는 팀뿐이다")
	void selectableTeamsComeFromSelectionBasisCompetitions() {
		TeamSelection s = selectionService.resolve(2023, MAN_UTD);

		assertThat(s.teams()).extracting(TeamSelection.Option::teamId)
				.containsExactlyInAnyOrder(MAN_UTD, PROMOTED);
		// 챔피언스리그만, 컵만 뛴 팀은 선택 대상이 아니다 — 순위표도 리그 진단도 없다.
		assertThat(s.teams()).extracting(TeamSelection.Option::teamId)
				.doesNotContain(EUROPE_ONLY, CUP_ONLY);
	}

	@Test
	@DisplayName("승격·강등 — 과거 시즌 목록을 현재 소속으로 바꾸지 않는다")
	void pastSeasonsKeepTheirOwnTopFlight() {
		assertThat(selectionService.resolve(2022, MAN_UTD).teams())
				.extracting(TeamSelection.Option::teamId)
				.containsExactlyInAnyOrder(MAN_UTD, RELEGATED);

		assertThat(selectionService.resolve(2023, MAN_UTD).teams())
				.extracting(TeamSelection.Option::teamId)
				.containsExactlyInAnyOrder(MAN_UTD, PROMOTED);
	}

	@Test
	@DisplayName("한 칸씩 이동한다 — 가장 오래된 시즌에서는 이전 이동이 없다")
	void movesOneSeasonAtATime() {
		TeamSelection latest = selectionService.resolve(2023, MAN_UTD);
		assertThat(latest.previousSeason()).isEqualTo(2022);
		// 현재 시즌에 도달했으므로 앞으로 갈 곳이 없다(2024는 아직 안 치른 시즌이다).
		assertThat(latest.nextSeason()).isNull();

		TeamSelection oldest = selectionService.resolve(2022, MAN_UTD);
		assertThat(oldest.previousSeason()).isNull();  // "이전 시즌 데이터가 없습니다"
		assertThat(oldest.nextSeason()).isEqualTo(2023);
		assertThat(oldest.current()).isFalse();
	}

	@Test
	@DisplayName("그 시즌 선택 대상이 아니어도 팀을 바꾸지 않는다 — 기록이 없다고만 말한다")
	void ineligibleTeamIsKeptNotSwapped() {
		TeamSelection s = selectionService.resolve(2022, PROMOTED);

		assertThat(s.selected().teamId()).isEqualTo(PROMOTED);
		assertThat(s.selected().eligible()).isFalse();
		// 목록에는 그 시즌 1부 팀이 그대로 나오고, 고른 팀만 대상이 아닌 상태다.
		assertThat(s.teams()).extracting(TeamSelection.Option::teamId).doesNotContain(PROMOTED);
	}

	@Test
	@DisplayName("컵만 뛴 시즌도 '선택 대상 아님'이다 — 2부라고 단정하지 않는다")
	void cupOnlyTeamIsNotEligible() {
		assertThat(selectionService.eligible(CUP_ONLY, 2023)).isFalse();
		assertThat(selectionService.eligible(MAN_UTD, 2023)).isTrue();
	}

	@Test
	@DisplayName("없는 팀은 예외 — '선택 대상이 아님'과 '존재하지 않음'은 다르다")
	void unknownTeamIsNotTheSameAsIneligible() {
		assertThatThrownBy(() -> selectionService.resolve(2023, 999999L))
				.isInstanceOf(NoSuchElementException.class);
	}

	@Test
	@DisplayName("선택 기준 대회 경기가 하나도 없으면 '볼 시즌이 없다'고 답한다")
	void noSelectableFixturesMeansNoSeasons() {
		databaseCleaner.clearAllButCompetitions();

		TeamSelection s = selectionService.resolve(null, null);

		// "선택 가능 팀 0개"가 아니다 — 애초에 조회할 시즌이 없다.
		assertThat(s.season()).isNull();
		assertThat(s.currentSeason()).isNull();
		assertThat(s.teams()).isEmpty();
	}

	// ── 조립 도우미 ──────────────────────────────────────────────────────────

	private Competition competition(long id, String name, CompetitionType type, boolean selectable) {
		return competitionRepository.save(Competition.builder()
				.id(id).name(name).nameKo(name).shortNameKo("리그").country("England")
				.type(type).calendarSeason(false).displayed(true).selectable(selectable)
				.build());
	}

	private void team(long id, String name, String nameKo) {
		teamRepository.save(Team.builder().id(id).name(name).nameKo(nameKo).build());
	}

	private void fixture(Competition competition, int season, long homeId, long awayId,
	                     Instant kickoff, FixtureStatus status) {
		fixtureRepository.save(Fixture.builder()
				.id(nextFixtureId++)
				.competition(competition)
				.season(season)
				.round("Regular Season - 1")
				.homeTeam(teamRepository.findById(homeId).orElseThrow())
				.awayTeam(teamRepository.findById(awayId).orElseThrow())
				.kickoff(kickoff)
				.status(status)
				.goalsHome(status == FixtureStatus.FT ? 1 : null)
				.goalsAway(status == FixtureStatus.FT ? 0 : null)
				.build());
	}
}
