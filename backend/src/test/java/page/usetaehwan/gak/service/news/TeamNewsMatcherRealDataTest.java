package page.usetaehwan.gak.service.news;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import page.usetaehwan.gak.service.seed.NewsAliasCatalog;

/**
 * 게이트를 <b>실제 헤드라인 137건</b>으로 검증한다.
 *
 * <p>표본은 2026-08-03 에 RSS 4개에서 받은 것이고, 정답 라벨은 손으로 매겼다
 * ({@code src/test/resources/news/README.md} 에 기준이 적혀 있다).
 * 참 37 / 거짓 100.
 *
 * <h2>이 테스트가 지키는 것</h2>
 * <p><b>정밀도 100%</b> — 남의 팀 기사가 피드에 들어오지 않는다. 이게 재현율보다 중요하다.
 * 소식 하나를 놓치면 사용자는 모르지만, 엉뚱한 기사가 뜨면 바로 보인다.
 *
 * <p>별칭을 추가하거나 제외 규칙을 손대면 여기가 먼저 깨진다. 그게 이 테스트의 목적이다 —
 * 시드는 사람이 손으로 고치는 파일이고, 고칠 때 무엇이 무너지는지 알려 줄 장치가 필요하다.
 * ({@code CompetitionSeedTest} 가 대회 시드에 하는 일과 같다.)
 */
class TeamNewsMatcherRealDataTest {

	private static final long MAN_UTD = 33L;

	/** 어느 피드가 특정 팀 전용인가. 전용 피드도 게이트를 건너뛰지 않는다. */
	private static final Map<String, Long> FEED_TEAM = Map.of(
			"guardian-manutd", MAN_UTD,
			"men-manutd", MAN_UTD);

	private static List<Row> rows;
	private static TeamNewsMatcher matcher;

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Row(String id, String source, String title,
	           @JsonProperty("gold_target") boolean goldTarget,
	           @JsonProperty("gold_cat") String goldCategory) {
	}

	@BeforeAll
	static void loadFixture() throws IOException {
		ObjectMapper mapper = new ObjectMapper();
		try (InputStream in = TeamNewsMatcherRealDataTest.class
				.getResourceAsStream("/news/gold-and-keyword.json")) {
			assertThat(in).as("표본 파일이 있어야 한다").isNotNull();
			rows = List.of(mapper.readValue(in, Row[].class));
		}

		// 실제 시드를 그대로 읽는다 — 테스트용 별칭을 따로 두면 시드가 틀려도 초록불이 켜진다.
		NewsAliasCatalog catalog = new NewsAliasCatalog(new DefaultResourceLoader(), mapper);
		catalog.load();
		matcher = new TeamNewsMatcher(catalog);
	}

	private Optional<Long> match(Row row) {
		return matcher.match(row.title(), FEED_TEAM.get(row.source()));
	}

	@Test
	@DisplayName("표본이 기대한 모양이다 — 137건, 참 37 / 거짓 100")
	void fixtureShape() {
		assertThat(rows).hasSize(137);
		assertThat(rows.stream().filter(Row::goldTarget).count()).isEqualTo(37);
	}

	@Test
	@DisplayName("오탐이 0건이다 — 남의 팀 기사가 절대 들어오지 않는다")
	void noFalsePositives() {
		List<String> falsePositives = rows.stream()
				.filter(row -> !row.goldTarget())
				.filter(row -> match(row).isPresent())
				.map(row -> row.id() + " :: " + row.title())
				.toList();

		assertThat(falsePositives)
				.as("게이트를 통과했지만 맨유 1군 기사가 아닌 것")
				.isEmpty();
	}

	@Test
	@DisplayName("재현율 97% 이상 — 놓치는 건 제목에 구단명이 없는 1건뿐")
	void recall() {
		List<Row> missed = rows.stream()
				.filter(Row::goldTarget)
				.filter(row -> match(row).isEmpty())
				.toList();

		assertThat(missed)
				.as("놓친 것은 '제목에 구단명이 아예 없는' 경우만이어야 한다")
				.hasSize(1);
		assertThat(missed.get(0).title()).contains("Bruno Fernandes");

		double recall = (37.0 - missed.size()) / 37.0;
		assertThat(recall).isGreaterThanOrEqualTo(0.97);
	}

	@Test
	@DisplayName("여자팀·유소년은 구단명이 있어도 통과하지 못한다")
	void womensTeamIsExcluded() {
		List<String> womensHeadlines = List.of(
				"Man Utd boss Skinner leaves role before WSL season",
				"Skinner leaves Man Utd Women",
				"London City host Man Utd to kick off WSL season",
				"'Man Utd concerned about cost of WSL title chase' - Why Skinner left Man Utd");

		for (String title : womensHeadlines) {
			assertThat(matcher.match(title, null))
					.as(title)
					.isEmpty();
		}
	}

	@Test
	@DisplayName("전 소속 선수 기사는 통과하지 못한다 — 'ex-'/'former' 가 별칭 바로 앞에 붙은 경우")
	void formerPlayersAreExcluded() {
		List<String> formerPlayerHeadlines = List.of(
				"Ex-Man United star Casemiro endures nightmare start to Inter Miami career",
				"Chelsea blamed for major Alejandro Garnacho issue as ex-Man United star starts again",
				"Alejandro Garnacho sends clear message as ex-Man United star shows true feelings",
				"Ex-Man United talent fighting for next career move as he opens up on injury hell");

		for (String title : formerPlayerHeadlines) {
			assertThat(matcher.match(title, null))
					.as(title)
					.isEmpty();
		}
	}

	@Test
	@DisplayName("별칭이 여러 번 나오면 하나라도 깨끗하면 통과한다")
	void oneCleanOccurrenceIsEnough() {
		// 'ex-Man United' 로 시작하지만 뒤에 구단 자체 소식이 이어지는 문장.
		// 첫 등장만 보고 버리면 이런 기사를 놓친다.
		assertThat(matcher.match(
				"Ex-Man United star returns as Manchester United confirm coaching role", null))
				.contains(MAN_UTD);
	}

	@Test
	@DisplayName("전용 피드에도 우리 팀이 아닌 기사가 섞여 온다 — 그래서 게이트를 건너뛰지 않는다")
	void dedicatedFeedsStillNeedTheGate() {
		// "맨유 전담" 피드인데 실제로는 맨유 1군 기사가 아닌 것들.
		// Guardian 4건(일반 칼럼·퍼거슨 추모·토트넘 영입·전 코치 인터뷰)
		// + MEN 5건(가르나초 2건·카세미루·전 유망주·TV 편성 안내) = 9건.
		// 전용 피드라고 믿고 게이트를 건너뛰면 이 9건이 전부 들어온다.
		List<String> correctlyRejected = rows.stream()
				.filter(row -> FEED_TEAM.containsKey(row.source()))
				.filter(row -> !row.goldTarget())
				.filter(row -> match(row).isEmpty())
				.map(Row::id)
				.toList();

		assertThat(correctlyRejected).hasSize(9);

		// 전용 피드에서 게이트가 잘못 버린 것은 알려진 1건(제목에 구단명 없음)뿐이다.
		List<String> wronglyRejected = rows.stream()
				.filter(row -> FEED_TEAM.containsKey(row.source()))
				.filter(Row::goldTarget)
				.filter(row -> match(row).isEmpty())
				.map(Row::id)
				.toList();

		assertThat(wronglyRejected).containsExactly("guardian-manutd#14");
	}

	@Test
	@DisplayName("동명이인 함정 — 시드에 선수 이름을 넣지 않은 이유")
	void playerNamesWouldBeAmbiguous() {
		// 표본에 실제로 함께 있던 두 문장. 'Alonso' 를 별칭으로 넣으면 F1 기사가 들어온다.
		assertThat(matcher.match(
				"Alonso heals Real Madrid scars to lead Chelsea's senior revolution", null))
				.isEmpty();
		assertThat(matcher.match(
				"Will Verstappen, Alonso trigger F1 'silly season' chaos?", null))
				.isEmpty();
	}

	@Test
	@DisplayName("'United' 단독은 별칭이 아니다 — 다른 구단이 걸리면 안 된다")
	void bareUnitedIsNotAnAlias() {
		List<String> otherUniteds = List.of(
				"Newcastle United complete signing of Braga keeper",
				"West Ham United confirm Staveley takeover",
				"Leeds United come from 2-0 down to beat Liverpool",
				"Sheffield United appoint new head coach");

		for (String title : otherUniteds) {
			assertThat(matcher.match(title, null)).as(title).isEmpty();
		}
	}

	@Test
	@DisplayName("소유격·문장부호가 붙어도 별칭을 찾는다")
	void handlesPossessivesAndPunctuation() {
		assertThat(matcher.match("Man Utd's new signing impresses", null)).contains(MAN_UTD);
		assertThat(matcher.match("Inside Man United's trip to Stockholm", null)).contains(MAN_UTD);
		assertThat(matcher.match("MUFC confirm deal", null)).contains(MAN_UTD);
	}

	@Test
	@DisplayName("'Man' 이 다른 단어 안에 들어 있어도 걸리지 않는다")
	void doesNotMatchInsideOtherWords() {
		assertThat(matcher.match("Manchester City win the league", null)).isEmpty();
		assertThat(matcher.match("Mancini appointed as manager", null)).isEmpty();
	}
}
