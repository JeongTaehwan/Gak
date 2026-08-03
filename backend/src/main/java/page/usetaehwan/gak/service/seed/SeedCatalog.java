package page.usetaehwan.gak.service.seed;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 시드 조회표 — API가 주지 않는 정보를 채워 넣는 곳.
 *
 * <ul>
 *   <li>{@code team-names-ko.json} — 팀 한글 표기(API는 영문만 준다)</li>
 *   <li>{@code city-coordinates.json} — 도시 좌표(API는 경기장 좌표를 주지 않는다)</li>
 * </ul>
 *
 * <p>동기화가 팀/경기장을 <b>새로 만들 때</b>만 참조한다. 이미 있는 행의 한글명·좌표를
 * 매번 덮어쓰면, 시드에 없는 항목이 계속 null로 되돌아간다.
 *
 * <p>파일이 없거나 깨져도 앱은 뜬다 — 한글명이 없으면 영문으로, 좌표가 없으면
 * 이동거리 계산을 생략하면 그만이다. 있으면 좋은 정보이지 필수 정보가 아니다.
 */
@Component
public class SeedCatalog {

	private static final Logger log = LoggerFactory.getLogger(SeedCatalog.class);

	private final ResourceLoader resourceLoader;
	private final ObjectMapper objectMapper;

	private Map<String, TeamName> teamNames = Map.of();
	private Map<String, Coordinates> cityCoordinates = Map.of();

	public SeedCatalog(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
		this.resourceLoader = resourceLoader;
		this.objectMapper = objectMapper;
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record TeamName(String nameKo, String shortNameKo, String code) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Coordinates(Double latitude, Double longitude) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record TeamNameSeed(Map<String, TeamName> teams) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record CitySeed(Map<String, Coordinates> cities) {
	}

	@PostConstruct
	void load() {
		TeamNameSeed teams = read("classpath:seeds/team-names-ko.json", TeamNameSeed.class);
		if (teams != null && teams.teams() != null) {
			teamNames = Map.copyOf(teams.teams());
		}
		CitySeed cities = read("classpath:seeds/city-coordinates.json", CitySeed.class);
		if (cities != null && cities.cities() != null) {
			cityCoordinates = Map.copyOf(cities.cities());
		}
		log.info("시드 로딩: 팀 한글명 {}건, 도시 좌표 {}건", teamNames.size(), cityCoordinates.size());
	}

	/** 팀 영문 원본명으로 한글 표기 찾기. 없으면 null(화면에서 영문 fallback). */
	public TeamName teamName(String englishName) {
		return englishName == null ? null : teamNames.get(englishName);
	}

	/**
	 * 도시명으로 좌표 찾기. 없으면 null(이동거리 계산 생략 — 에러 아님).
	 *
	 * <h2>왜 정규화가 필요한가</h2>
	 * <p>API 가 주는 도시명은 시드의 키와 형식이 다르다. 실제로 맨유 2023-24 원정 27경기 중
	 * 14경기가 좌표를 못 찾았는데, <b>절반은 도시가 시드에 없어서가 아니라 이름이 안 맞아서</b>였다.
	 *
	 * <pre>
	 * API "Nottingham, Nottinghamshire"   시드 "Nottingham"      → 쉼표 뒤 주(州)명
	 * API "Wolverhampton, West Midlands"  시드 "Wolverhampton"   → 같은 문제
	 * API "München" / "København"          시드 "Munich"/"Copenhagen" → 현지어 표기
	 * </pre>
	 *
	 * <p>그래서 세 단계로 찾는다. <b>시드를 채우기 전에 이게 먼저다</b> — 이름이 안 맞으면
	 * 도시를 아무리 넣어도 못 찾는다.
	 *
	 * <ol>
	 *   <li>있는 그대로</li>
	 *   <li>쉼표 앞부분만 ({@code "Nottingham, Nottinghamshire"} → {@code "Nottingham"})</li>
	 *   <li>현지어 별칭 ({@code "München"} → {@code "Munich"})</li>
	 * </ol>
	 *
	 * <p>못 찾으면 <b>여전히 null 이다.</b> 정규화는 찾을 확률을 올릴 뿐, 없는 좌표를
	 * 만들어 내지 않는다 — 그건 {@code omissions} 가 계속 정직하게 밝힌다.
	 */
	public Coordinates coordinates(String city) {
		if (city == null || city.isBlank()) {
			return null;
		}
		Coordinates exact = cityCoordinates.get(city.trim());
		if (exact != null) {
			return exact;
		}
		String head = city.split(",")[0].trim();
		Coordinates byHead = cityCoordinates.get(head);
		if (byHead != null) {
			return byHead;
		}
		String english = CITY_ALIASES.get(head);
		return english == null ? null : cityCoordinates.get(english);
	}

	/**
	 * 현지어 표기 → 시드가 쓰는 영문 표기.
	 *
	 * <p>API 는 도시명을 현지 표기로 준다({@code München}, {@code København}). 시드를 현지
	 * 표기로 바꾸는 대신 별칭을 두는 이유: 같은 도시가 대회·응답에 따라 두 표기로 다 올 수
	 * 있고, 시드 쪽을 한 표기로 고정해야 사람이 읽고 관리할 수 있다.
	 *
	 * <p>여기 없는 도시는 그냥 못 찾는다. 억지로 유사도 매칭을 하면 엉뚱한 도시의 좌표로
	 * 이동거리를 재게 되는데, 그건 좌표가 없는 것보다 나쁘다.
	 */
	private static final Map<String, String> CITY_ALIASES = Map.ofEntries(
			Map.entry("München", "Munich"),
			Map.entry("Munchen", "Munich"),
			Map.entry("København", "Copenhagen"),
			Map.entry("Kobenhavn", "Copenhagen"),
			Map.entry("Köln", "Cologne"),
			Map.entry("Wien", "Vienna"),
			Map.entry("Praha", "Prague"),
			Map.entry("Lisboa", "Lisbon"),
			Map.entry("Sevilla", "Seville"),
			Map.entry("Roma", "Rome"),
			Map.entry("Milano", "Milan"),
			Map.entry("Napoli", "Naples"),
			Map.entry("Torino", "Turin"),
			Map.entry("Firenze", "Florence"),
			Map.entry("Genève", "Geneva"),
			Map.entry("Zürich", "Zurich"),
			Map.entry("Bruxelles", "Brussels"),
			Map.entry("Brussel", "Brussels"),
			Map.entry("Antwerpen", "Antwerp"),
			Map.entry("Gent", "Ghent"),
			Map.entry("Den Haag", "The Hague"),
			Map.entry("Moskva", "Moscow"),
			Map.entry("Kyiv", "Kiev"),
			Map.entry("Warszawa", "Warsaw"),
			Map.entry("Kraków", "Krakow"),
			Map.entry("Beograd", "Belgrade"),
			Map.entry("Bucureşti", "Bucharest"),
			Map.entry("București", "Bucharest"),
			Map.entry("Athína", "Athens"),
			Map.entry("Aθήνα", "Athens"),
			Map.entry("İstanbul", "Istanbul"),
			Map.entry("Göteborg", "Gothenburg"));

	private <T> T read(String location, Class<T> type) {
		try (InputStream in = resourceLoader.getResource(location).getInputStream()) {
			return objectMapper.readValue(in, type);
		} catch (IOException e) {
			log.warn("시드 파일을 읽지 못했습니다({}): {} — 해당 정보 없이 진행합니다.",
					location, e.getMessage());
			return null;
		}
	}
}
