package page.usetaehwan.gak.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 뉴스 게이트가 쓰는 팀 별칭 시드({@code seeds/team-news-aliases.json}).
 *
 * <p>{@link SeedCatalog}(한글명·좌표)와 파일을 나눈 이유는 <b>바뀌는 이유가 다르기</b>
 * 때문이다. 한글명은 표기 취향이고, 여기 값은 분류 정확도에 직결된다 — 별칭 하나를
 * 잘못 넣으면 남의 팀 기사가 피드에 들어온다. 그래서 테스트도 따로 붙는다
 * ({@code TeamNewsMatcherRealDataTest}).
 *
 * <p>파일이 없거나 깨지면 <b>게이트가 아무것도 통과시키지 않는다</b>. 뉴스가 안 뜨는 건
 * 불편하지만, 별칭 없이 통과시키면 아무 기사나 팀 화면에 들어온다. 조용히 틀리느니
 * 조용히 비어 있는 쪽이 낫다.
 */
@Component
public class NewsAliasCatalog {

	private static final Logger log = LoggerFactory.getLogger(NewsAliasCatalog.class);

	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;

	private Map<Long, TeamAliases> teams = Map.of();
	private List<String> excludeAny = List.of();
	private List<String> excludeBefore = List.of();

	public NewsAliasCatalog(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		this.resourceLoader = resourceLoader;
		this.objectMapper = objectMapper;
	}

	/** 테스트용 — 파일 없이 직접 구성한다. */
	public static NewsAliasCatalog of(Map<Long, TeamAliases> teams,
	                                  List<String> excludeAny,
	                                  List<String> excludeBefore) {
		NewsAliasCatalog catalog = new NewsAliasCatalog(null, null);
		catalog.teams = Map.copyOf(teams);
		catalog.excludeAny = List.copyOf(excludeAny);
		catalog.excludeBefore = List.copyOf(excludeBefore);
		return catalog;
	}

	public record TeamAliases(String name, List<String> aliases) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Seed(
			Map<String, TeamAliases> teams,
			List<String> excludeAny,
			List<String> excludeBefore) {
	}

	/** {@code public} 인 이유는 다른 패키지의 테스트가 실제 시드를 그대로 읽어 쓰기 때문이다. */
	@PostConstruct
	public void load() {
		Seed seed = read();
		if (seed == null) {
			log.warn("뉴스 별칭 시드를 읽지 못했습니다 — 뉴스 게이트가 아무것도 통과시키지 않습니다.");
			return;
		}
		Map<Long, TeamAliases> parsed = new LinkedHashMap<>();
		if (seed.teams() != null) {
			seed.teams().forEach((key, value) -> {
				Long teamId = parseTeamId(key);
				if (teamId == null || value == null
						|| value.aliases() == null || value.aliases().isEmpty()) {
					log.warn("뉴스 별칭 시드에서 건너뜀: key={}", key);
					return;
				}
				parsed.put(teamId, value);
			});
		}
		teams = Map.copyOf(parsed);
		excludeAny = lower(seed.excludeAny());
		excludeBefore = lower(seed.excludeBefore());

		log.info("뉴스 별칭 시드 로딩: 팀 {}건, 제외어 {}건, 선행 제외 {}건",
				teams.size(), excludeAny.size(), excludeBefore.size());
	}

	private static List<String> lower(List<String> values) {
		return values == null ? List.of()
				: values.stream()
				.filter(v -> v != null && !v.isBlank())
				.map(v -> v.toLowerCase(Locale.ROOT))
				.toList();
	}

	private static Long parseTeamId(String key) {
		try {
			return Long.valueOf(key.trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private Seed read() {
		String location = "classpath:seeds/team-news-aliases.json";
		try (InputStream in = resourceLoader.getResource(location).getInputStream()) {
			return objectMapper.readValue(in, Seed.class);
		} catch (IOException e) {
			log.warn("시드 파일을 읽지 못했습니다({}): {}", location, e.getMessage());
			return null;
		}
	}

	public Map<Long, TeamAliases> teams() {
		return teams;
	}

	/** 제목 어디에든 있으면 그 기사를 버리는 말(여자팀·유소년). */
	public List<String> excludeAny() {
		return excludeAny;
	}

	/** 별칭 바로 앞에 붙으면 그 등장을 무효로 만드는 말(ex-, former). */
	public List<String> excludeBefore() {
		return excludeBefore;
	}

	public boolean isEmpty() {
		return teams.isEmpty();
	}
}
