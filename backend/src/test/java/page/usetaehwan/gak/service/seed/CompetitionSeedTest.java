package page.usetaehwan.gak.service.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.service.seed.CompetitionSeeder.CompetitionSeed;

/**
 * 시드에 적은 id가 <b>정말 그 대회가 맞는지</b>를 저장해 둔 실제 {@code /leagues} 응답
 * ({@code leagues-raw.json}, 940개 대회)과 대조한다.
 *
 * <p>이 검증이 필요한 이유는 이름이 유일하지 않기 때문이다. "Premier League"는 31개 나라에,
 * "FA Cup"은 7개 나라에 있고, "Serie A"는 이탈리아와 브라질에 동시에 있다. 게다가
 * 여자부·유소년·하부 리그가 거의 같은 이름을 쓴다. 사람이 눈으로 옮겨 적은 id 목록은
 * 언젠가 한 줄이 틀리는데, 그 틀림이 조용히 "여자부 DFB 포칼 동기화"로 나타난다.
 *
 * <p>API를 호출하지 않는다 — 저장된 응답 파일만 읽는다.
 */
class CompetitionSeedTest {

	private static List<CompetitionSeed> seeds;
	private static Map<Long, ApiLeague> apiLeagues;

	private record ApiLeague(String name, String type, String country) {
	}

	@BeforeAll
	static void loadOnce() throws Exception {
		ObjectMapper mapper = new ObjectMapper();
		DefaultResourceLoader loader = new DefaultResourceLoader();

		seeds = new CompetitionSeeder(null, loader, mapper).readSeeds();

		apiLeagues = new HashMap<>();
		try (InputStream in = loader.getResource("classpath:apifootball/leagues-raw.json")
				.getInputStream()) {
			JsonNode root = mapper.readTree(in);
			for (JsonNode item : root.path("response")) {
				JsonNode league = item.path("league");
				apiLeagues.put(league.path("id").asLong(), new ApiLeague(
						league.path("name").asText(),
						league.path("type").asText(),
						item.path("country").path("name").asText()));
			}
		}
	}

	@Test
	@DisplayName("시드는 대회 16개이고 id가 중복되지 않는다")
	void seedHasSixteenUniqueCompetitions() {
		assertThat(seeds).hasSize(16);
		assertThat(seeds.stream().map(CompetitionSeed::id).collect(Collectors.toSet()))
				.hasSize(16);
	}

	@Test
	@DisplayName("커버 범위는 유럽 5대 리그 + K리그1 — 각 리그에 자국컵이 하나씩 있다")
	void everyLeagueHasItsDomesticCup() {
		Set<String> leagueCountries = seeds.stream()
				.filter(s -> s.type() == CompetitionType.LEAGUE)
				.map(CompetitionSeed::country)
				.collect(Collectors.toSet());
		Set<String> cupCountries = seeds.stream()
				.filter(s -> s.type() == CompetitionType.CUP)
				.map(CompetitionSeed::country)
				.collect(Collectors.toSet());

		assertThat(leagueCountries).containsExactlyInAnyOrder(
				"England", "Spain", "Germany", "Italy", "France", "South-Korea");
		// 자국컵이 빠진 리그가 있으면 그 팀들만 일정이 헐거워 보인다 — 밀집도가 거짓이 된다.
		assertThat(cupCountries).containsAll(leagueCountries);
	}

	@Test
	@DisplayName("시드의 모든 id가 실제 API 대회 목록에 있고, 이름·나라가 일치한다")
	void everySeededIdMatchesTheRealLeague() {
		for (CompetitionSeed seed : seeds) {
			ApiLeague api = apiLeagues.get(seed.id());
			assertThat(api)
					.as("id %d(%s)가 API 대회 목록에 없습니다", seed.id(), seed.name())
					.isNotNull();
			assertThat(api.name())
					.as("id %d의 이름이 다릅니다", seed.id())
					.isEqualTo(seed.name());
			assertThat(api.country())
					.as("id %d(%s)의 나라가 다릅니다", seed.id(), seed.name())
					.isEqualTo(seed.country());
		}
	}

	@Test
	@DisplayName("이름으로는 대회를 특정할 수 없다 — id로만 다뤄야 하는 이유")
	void namesAreNotUnique() {
		Map<String, List<Long>> byName = apiLeagues.entrySet().stream()
				.collect(Collectors.groupingBy(e -> e.getValue().name(),
						Collectors.mapping(Map.Entry::getKey, Collectors.toList())));

		// "Serie A" = 이탈리아 135 + 브라질 71. 우리 시드는 135만 쓴다 — 이름으로 골랐으면
		// 브라질 리그를 동기화하고 있었을 것이다.
		assertThat(byName.get("Serie A")).contains(135L, 71L);
		// "FA Cup" = 잉글랜드 45 + 한국 294 + 그 밖의 나라들.
		assertThat(byName.get("FA Cup")).contains(45L, 294L).hasSizeGreaterThan(2);
		// 가장 극단적인 예 — 같은 이름의 대회가 수십 개.
		assertThat(byName.get("Premier League")).contains(39L).hasSizeGreaterThan(10);
	}

	@Test
	@DisplayName("API의 league.type으로는 HYBRID를 판별할 수 없다 — 그래서 시드가 타입을 정한다")
	void apiTypeCannotExpressHybrid() {
		Set<String> apiTypes = apiLeagues.values().stream()
				.map(ApiLeague::type)
				.collect(Collectors.toSet());
		assertThat(apiTypes).containsExactlyInAnyOrder("League", "Cup");

		// 유럽대항전 3개는 조별리그(순위) + 녹아웃(토너먼트)인데 API는 전부 "Cup"이라고 한다.
		for (long hybridId : List.of(2L, 3L, 848L)) {
			assertThat(apiLeagues.get(hybridId).type()).isEqualTo("Cup");
			assertThat(seedOf(hybridId).type()).isEqualTo(CompetitionType.HYBRID);
		}
	}

	@Test
	@DisplayName("여자부·유소년·하부 리그 id가 시드에 섞여 들어가지 않았다")
	void noWomensYouthOrLowerDivisionIds() {
		// 이름이 헷갈리기 쉬운 실제 사례들 — 전부 시드에 없어야 한다.
		List<Long> forbidden = List.of(
				947L,  // DFB Pokal - Women
				715L,  // DFB Pokal - Youth (우리가 쓰는 건 81)
				891L,  // Coppa Italia Serie C
				892L,  // Coppa Italia Serie D (우리가 쓰는 건 137)
				218L,  // Bundesliga (Austria) — 독일은 78
				181L,  // FA Cup (Scotland) — 잉글랜드는 45
				301L); // Pro League (UAE) — 사우디는 307

		Set<Long> seededIds = seeds.stream().map(CompetitionSeed::id).collect(Collectors.toSet());
		assertThat(seededIds).doesNotContainAnyElementsOf(forbidden);
	}

	@Test
	@DisplayName("시즌 캘린더 구분 — 한 해로 끝나는 대회만 calendarSeason=true")
	void calendarSeasonFlagsAreCorrect() {
		// 한국은 3~12월 시즌이라 시즌 번호 = 그 해 연도.
		// (브라질 71·아르헨티나 128 도 같은 부류였는데 커버 범위에서 빠졌다. 다시 넣으면
		//  calendarSeason=true 로 넣어야 한다.)
		for (long id : List.of(292L, 294L)) {
			assertThat(seedOf(id).calendarSeason())
					.as("id %d는 한 해로 끝나는 시즌입니다", id)
					.isTrue();
		}
		// 유럽은 8월~다음 해 5월 걸침 시즌.
		for (long id : List.of(39L, 140L, 2L, 45L, 66L)) {
			assertThat(seedOf(id).calendarSeason())
					.as("id %d는 걸침 시즌입니다", id)
					.isFalse();
		}
	}

	private static CompetitionSeed seedOf(long id) {
		return seeds.stream().filter(s -> s.id() == id).findFirst().orElseThrow();
	}
}
