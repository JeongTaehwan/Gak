package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Standing;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.OpponentStrength;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.StandingRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 상대 강도 — <b>"누구를 상대로 그 성적이었나"</b>.
 *
 * <p>여기서 지키는 것은 하나다. <b>모르는 것을 아는 척하지 않기.</b> 컵 경기에는 순위가
 * 없고, 시즌 초 상대는 경기 수가 얇아 순위를 말할 수 없다. 그 경기들을 분모에서 빼되
 * 뺐다는 사실을 남긴다 — "6경기 상대 평균 8위"가 실제로는 4경기 기준이면 그건 거짓이다.
 */
@SpringBootTest
@Import(DatabaseCleaner.class)
@ActiveProfiles("test")
// 세션을 열어 둔다. 운영에서는 TeamDiagnosticsService 가 @EntityGraph 로 대회까지 함께
// 읽어 넘기지만, 여기서는 save() 가 돌려준 엔티티를 그대로 쓰므로 대회가 지연 프록시다.
@Transactional
class OpponentStrengthServiceTest {

	/** 리그 한 라운드 간격. 경기 수를 쌓아 순위를 "믿을 만하게" 만드는 데 쓴다. */
	private static final Instant KICKOFF = Instant.parse("2024-01-01T15:00:00Z");

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired OpponentStrengthService service;
	@Autowired FixtureRepository fixtureRepository;
	@Autowired TeamRepository teamRepository;
	@Autowired CompetitionRepository competitionRepository;
	@Autowired StandingRepository standingRepository;

	private Competition league;
	private Competition cup;
	private final List<Team> teams = new ArrayList<>();
	private long nextFixtureId = 1;

	@BeforeEach
	void reset() {
		databaseCleaner.clearAllButCompetitions();
		teams.clear();
		nextFixtureId = 1;

		league = competitionRepository.save(Competition.builder()
				.id(900L).name("Test League").nameKo("테스트리그").shortNameKo("리그")
				.country("Test").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).build());
		cup = competitionRepository.save(Competition.builder()
				.id(901L).name("Test Cup").nameKo("테스트컵").shortNameKo("컵")
				.country("Test").type(CompetitionType.CUP)
				.calendarSeason(false).displayed(true).build());

		// 10팀. id 1 이 우리 팀, 2~10 이 상대.
		for (long i = 1; i <= 10; i++) {
			teams.add(teamRepository.save(
					Team.builder().id(i).name("Team " + i).nameKo("팀" + i).build()));
		}
	}

	/** 리그 경기 하나. {@code dayOffset} 로 시점을 조절한다. */
	private Fixture play(Competition competition, long homeId, long awayId,
	                     int homeGoals, int awayGoals, int dayOffset) {
		return fixtureRepository.save(Fixture.builder()
				.id(nextFixtureId++)
				.competition(competition).season(2024).round("R")
				.homeTeam(team(homeId)).awayTeam(team(awayId))
				.kickoff(KICKOFF.plus(dayOffset, ChronoUnit.DAYS))
				.status(FixtureStatus.FT).goalsHome(homeGoals).goalsAway(awayGoals)
				.build());
	}

	private Team team(long id) {
		return teams.get((int) id - 1);
	}

	/**
	 * 상대 9팀(2~10)의 풀리그를 돌려 <b>순위가 또렷한 표</b>를 만든다.
	 *
	 * <p>번호가 작을수록 이긴다 — 팀2 가 1위, 팀10 이 9위. 모든 팀이 8경기를 치르므로
	 * {@link LeagueTable#MIN_MATCHES_FOR_RANK} 를 넘겨 전원 순위가 나온다.
	 *
	 * <p>우리 팀(1)은 여기 끼지 않는다. 우리 성적이 상대 순위를 흔들면 검증이 흐려진다.
	 *
	 * <p>결과 승점: 팀2 24 · 팀3 21 · 팀4 18 · 팀5 15 · 팀6 12 · 팀7 9 · 팀8 6 · 팀9 3 · 팀10 0.
	 * 표가 9팀이므로 상위권 경계는 3위까지다(9 × 0.3).
	 */
	private void buildTable() {
		int day = -100;
		for (long i = 2; i <= 10; i++) {
			for (long j = i + 1; j <= 10; j++) {
				play(league, i, j, 3, 0, day++);   // 번호가 작은 쪽이 이긴다
			}
		}
	}

	@Test
	@DisplayName("상대 순위를 그 경기 시점 기준으로 매기고 상위권/그 외로 가른다")
	void splitsByOpponentRankAtKickoff() {
		buildTable();   // 팀2·3·4 가 상위권(3위까지)

		// 우리 팀의 최근 경기: 상위권 2팀(2,3) 과 하위권 2팀(5,6)
		List<Fixture> recent = List.of(
				play(league, 1L, 2L, 0, 1, 10),   // 상위권에게 패
				play(league, 1L, 3L, 1, 1, 11),   // 상위권과 무
				play(league, 1L, 5L, 2, 0, 12),   // 그 외에게 승
				play(league, 1L, 6L, 3, 0, 13));  // 그 외에게 승

		OpponentStrength o = service.of(1L, recent);

		assertThat(o.available()).isTrue();
		assertThat(o.measured()).isEqualTo(4);
		assertThat(o.unmeasured()).isZero();
		// 표 크기는 **마지막으로 순위를 매긴 경기 시점** 기준이다. 그 시점엔 우리 팀도
		// 이미 리그 경기를 치러 표에 올라 있으므로 9팀이 아니라 10팀이다.
		assertThat(o.tableSize()).isEqualTo(10);

		assertThat(o.vsTop().matches()).isEqualTo(2);
		assertThat(o.vsTop().wins()).isZero();
		assertThat(o.vsTop().draws()).isEqualTo(1);
		assertThat(o.vsTop().losses()).isEqualTo(1);

		assertThat(o.vsRest().matches()).isEqualTo(2);
		assertThat(o.vsRest().wins()).isEqualTo(2);
	}

	@Test
	@DisplayName("컵 경기는 순위가 없어 분모에서 빠진다 — 0위로 채우지 않는다")
	void cupMatchesAreUnmeasured() {
		buildTable();

		List<Fixture> recent = List.of(
				play(league, 1L, 2L, 0, 1, 10),
				play(cup, 1L, 3L, 2, 1, 11),      // 컵 — 상대가 상위권이어도 안 잡힌다
				play(cup, 1L, 5L, 1, 0, 12));

		OpponentStrength o = service.of(1L, recent);

		assertThat(o.measured()).isEqualTo(1);
		assertThat(o.unmeasured()).isEqualTo(2);
		// 컵에서 상위권 팀을 이겼지만 상위권 성적에 잡히지 않는다 — 이 지표의 한계다
		assertThat(o.vsTop().matches()).isEqualTo(1);
		assertThat(o.vsTop().wins()).isZero();
	}

	@Test
	@DisplayName("시즌 초라 상대의 경기 수가 얇으면 순위를 말하지 않는다")
	void refusesToRankOpponentsTooEarly() {
		// 상대들이 2경기씩만 치른 상태 (기준은 5경기)
		play(league, 2L, 3L, 1, 0, -3);
		play(league, 4L, 5L, 1, 0, -3);
		play(league, 2L, 4L, 1, 0, -2);
		play(league, 3L, 5L, 1, 0, -2);

		List<Fixture> recent = List.of(play(league, 1L, 2L, 0, 1, 10));

		OpponentStrength o = service.of(1L, recent);

		assertThat(o.available()).isFalse();
		assertThat(o.unmeasured()).isEqualTo(1);
		assertThat(o.averageRank()).isNull();
	}

	@Test
	@DisplayName("순위표가 있으면 승점 삭감을 대조로 잡아내 순위에 반영한다")
	void appliesPointDeductionsFoundByComparingWithTheTable() {
		buildTable();

		// 팀2 는 계산상 1위(24점)지만, 순위표에는 20점이 깎여 4점으로 적혀 있다.
		// 우리 계산과 순위표의 차이가 곧 삭감분이고, 서비스는 그걸 스스로 알아내야 한다.
		int computed = LeagueTable.pointsAfter(
				fixtureRepository.findByCompetitionIdAndSeasonOrderByKickoffAsc(league.getId(), 2024),
				2L, 8);
		assertThat(computed).as("팀2 는 8전 전승이라 24점").isEqualTo(24);
		standingRepository.save(Standing.builder()
				.competition(league).season(2024).team(team(2L))
				.rank(8).points(computed - 20).played(8)      // −20 삭감
				.goalsFor(24).goalsAgainst(0)
				.updatedAt(Instant.now()).build());

		List<Fixture> recent = List.of(play(league, 1L, 2L, 0, 1, 10));

		OpponentStrength o = service.of(1L, recent);

		assertThat(o.deductionsKnown()).as("순위표가 있으니 확인했다").isTrue();
		// 20점이 깎이면 더 이상 상위권이 아니다 → 상위권 성적에 안 잡힌다
		assertThat(o.vsTop().matches()).isZero();
		assertThat(o.vsRest().matches()).isEqualTo(1);
	}

	@Test
	@DisplayName("순위표가 없으면 '삭감 확인 못 함'을 밝힌다 — 삭감 0이라고 단정하지 않는다")
	void reportsWhenDeductionsCouldNotBeChecked() {
		buildTable();

		OpponentStrength o = service.of(1L, List.of(play(league, 1L, 2L, 0, 1, 10)));

		assertThat(o.available()).isTrue();
		assertThat(o.deductionsKnown()).isFalse();
	}

	@Test
	@DisplayName("경기별 상대 순위도 같은 기준으로 준다 — 못 매기는 경기는 맵에 없다")
	void ranksByFixtureSkipsWhatItCannotRank() {
		buildTable();

		Fixture leagueMatch = play(league, 1L, 2L, 0, 1, 10);
		Fixture cupMatch = play(cup, 1L, 3L, 2, 1, 11);

		var ranks = service.ranksByFixture(1L, List.of(leagueMatch, cupMatch));

		assertThat(ranks).containsKey(leagueMatch.getId());
		// 컵은 아예 넣지 않는다 — null 이나 0 으로 채우면 화면이 그걸 순위로 그린다
		assertThat(ranks).doesNotContainKey(cupMatch.getId());
	}

	@Test
	@DisplayName("최근 경기가 없으면 0이 아니라 '모름'이다")
	void emptyRecentIsUnknownNotZero() {
		OpponentStrength o = service.of(1L, List.of());

		assertThat(o.available()).isFalse();
		assertThat(o.averageRank()).isNull();
		assertThat(o.tableSize()).isNull();
	}
}
