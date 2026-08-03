package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Team;

/**
 * <b>실제 응답</b>으로 순위 계산을 검증한다.
 *
 * <p>2023-24 프리미어리그 380경기와 그 시즌 최종 순위표를 실제로 받아 둔 것이 있다
 * ({@code src/test/resources/apifootball/}). 경기 결과로 만든 표가 API 의 표와 맞는지
 * 견주면, 우리 계산이 옳은지와 <b>승점 삭감을 짚어내는지</b>가 한 번에 드러난다.
 *
 * <p>이건 조작한 표본이 아니라 실제로 일어난 시즌이다 — 에버턴은 재정 규정 위반으로
 * 승점이 깎였고, 노팅엄 포레스트도 마찬가지였다. 그 사건이 이 테스트의 기대값이다.
 */
class LeagueTableRealDataTest {

	private static final long PREMIER_LEAGUE = 39L;
	private static final int SEASON = 2023;
	private static final long EVERTON = 45L;
	private static final long NOTTINGHAM_FOREST = 65L;

	private final ObjectMapper mapper = new ObjectMapper();

	@Test
	@DisplayName("경기 결과로 만든 최종 표가 API 순위표와 승점까지 일치한다 (삭감 팀 제외)")
	void computedTableMatchesTheOfficialOne() throws Exception {
		List<Fixture> fixtures = loadFixtures();
		Map<Long, Integer> officialPoints = loadOfficialPoints();

		assertThat(fixtures).as("실제 캡처 파일이 있어야 한다").hasSize(380);
		assertThat(officialPoints).hasSize(20);

		LeagueTable.Snapshot computed = LeagueTable.at(fixtures, Instant.parse("2030-01-01T00:00:00Z"));

		Map<Long, Integer> gaps = new HashMap<>();
		officialPoints.forEach((teamId, official) -> {
			int ours = computed.byTeam().get(teamId).points();
			if (ours != official) {
				gaps.put(teamId, ours - official);
			}
		});

		// 어긋난 팀은 딱 둘이고, 그 차이가 실제 승점 삭감분이다.
		assertThat(gaps).hasSize(2);
		assertThat(gaps).containsEntry(EVERTON, 8);
		assertThat(gaps).containsEntry(NOTTINGHAM_FOREST, 4);
	}

	@Test
	@DisplayName("삭감을 반영하면 순위까지 공식 표와 완전히 일치한다")
	void applyingDeductionsReproducesTheOfficialRanking() throws Exception {
		List<Fixture> fixtures = loadFixtures();
		Map<Long, Integer> officialRank = loadOfficialRanks();

		// /standings 대조로 얻는 값이 이것이다 (OpponentStrengthService.deductionsFor)
		Map<Long, Integer> deductions = Map.of(EVERTON, 8, NOTTINGHAM_FOREST, 4);

		LeagueTable.Snapshot corrected =
				LeagueTable.at(fixtures, Instant.parse("2030-01-01T00:00:00Z"), deductions);

		for (LeagueTable.Row row : corrected.rows()) {
			assertThat(row.rank())
					.as("팀 %d 의 순위", row.teamId())
					.isEqualTo(officialRank.get(row.teamId()).intValue());
		}
	}

	@Test
	@DisplayName("삭감을 무시하면 에버턴이 세 계단 위로 뜬다 — 그래서 대조가 필요하다")
	void ignoringDeductionsMisplacesEverton() throws Exception {
		List<Fixture> fixtures = loadFixtures();
		Instant end = Instant.parse("2030-01-01T00:00:00Z");

		int withoutCorrection = LeagueTable.at(fixtures, end).byTeam().get(EVERTON).rank();
		int withCorrection = LeagueTable.at(fixtures, end,
				Map.of(EVERTON, 8, NOTTINGHAM_FOREST, 4)).byTeam().get(EVERTON).rank();

		assertThat(withoutCorrection).isEqualTo(12);
		assertThat(withCorrection).isEqualTo(15);
	}

	@Test
	@DisplayName("시즌 초에는 순위를 말하지 않는다 — 2경기 치른 1위는 순위가 아니다")
	void refusesToRankTooEarly() throws Exception {
		List<Fixture> fixtures = loadFixtures();

		// 개막 2주차 — 대부분의 팀이 2~3경기만 치른 시점
		LeagueTable.Snapshot early = LeagueTable.at(fixtures, Instant.parse("2023-08-28T00:00:00Z"));

		assertThat(early.rows()).as("경기는 이미 치러졌다").isNotEmpty();
		assertThat(early.rows())
				.as("모든 팀이 %d경기 미만이라 순위를 못 준다", LeagueTable.MIN_MATCHES_FOR_RANK)
				.allSatisfy(row -> assertThat(early.rankOf(row.teamId())).isNull());
	}

	@Test
	@DisplayName("시즌 중반에는 순위를 준다 — 그리고 그건 최종 순위와 다르다")
	void midSeasonRankDiffersFromFinalRank() throws Exception {
		List<Fixture> fixtures = loadFixtures();
		Map<Long, Integer> officialRank = loadOfficialRanks();

		LeagueTable.Snapshot newYear = LeagueTable.at(fixtures, Instant.parse("2024-01-01T00:00:00Z"));

		assertThat(newYear.rankOf(EVERTON)).isNotNull();
		// 이 차이가 이 클래스의 존재 이유다. 최종 순위를 쓰면 1월 경기를 5월의 정보로 평가하게 된다.
		long moved = newYear.rows().stream()
				.filter(r -> newYear.rankOf(r.teamId()) != null)
				.filter(r -> r.rank() != officialRank.get(r.teamId()))
				.count();
		assertThat(moved).as("1월 순위와 최종 순위가 같은 시즌은 없다").isGreaterThan(5);
	}

	// --- 실제 캡처 파일 읽기 ----------------------------------------------------

	private List<Fixture> loadFixtures() throws Exception {
		Competition league = Competition.builder()
				.id(PREMIER_LEAGUE).name("Premier League").type(CompetitionType.LEAGUE)
				.calendarSeason(false).displayed(true).build();
		Map<Long, Team> teams = new HashMap<>();
		List<Fixture> fixtures = new ArrayList<>();

		for (JsonNode node : read("fixtures-league39-season2023.json").path("response")) {
			JsonNode goals = node.path("goals");
			if (goals.path("home").isNull() || goals.path("away").isNull()) {
				continue;
			}
			fixtures.add(Fixture.builder()
					.id(node.path("fixture").path("id").asLong())
					.competition(league)
					.season(SEASON)
					.homeTeam(team(teams, node.path("teams").path("home")))
					.awayTeam(team(teams, node.path("teams").path("away")))
					.kickoff(Instant.parse(node.path("fixture").path("date").asText()))
					.status(FixtureStatus.FT)
					.goalsHome(goals.path("home").asInt())
					.goalsAway(goals.path("away").asInt())
					.build());
		}
		return fixtures;
	}

	private Map<Long, Integer> loadOfficialPoints() throws Exception {
		Map<Long, Integer> out = new HashMap<>();
		for (JsonNode row : officialTable()) {
			out.put(row.path("team").path("id").asLong(), row.path("points").asInt());
		}
		return out;
	}

	private Map<Long, Integer> loadOfficialRanks() throws Exception {
		Map<Long, Integer> out = new HashMap<>();
		for (JsonNode row : officialTable()) {
			out.put(row.path("team").path("id").asLong(), row.path("rank").asInt());
		}
		return out;
	}

	private JsonNode officialTable() throws Exception {
		return read("standings-league39-season2023.json")
				.path("response").get(0).path("league").path("standings").get(0);
	}

	private static Team team(Map<Long, Team> cache, JsonNode node) {
		long id = node.path("id").asLong();
		return cache.computeIfAbsent(id, k ->
				Team.builder().id(k).name(node.path("name").asText()).build());
	}

	private JsonNode read(String fileName) throws Exception {
		try (InputStream in = getClass().getResourceAsStream("/apifootball/" + fileName)) {
			assertThat(in).as("캡처 파일 %s 가 있어야 한다", fileName).isNotNull();
			return mapper.readTree(in);
		}
	}
}
