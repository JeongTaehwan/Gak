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

	/** 도시명으로 좌표 찾기. 없으면 null(이동거리 계산 생략 — 에러 아님). */
	public Coordinates coordinates(String city) {
		return city == null ? null : cityCoordinates.get(city);
	}

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
