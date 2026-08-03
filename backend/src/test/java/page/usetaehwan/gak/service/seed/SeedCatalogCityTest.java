package page.usetaehwan.gak.service.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 도시 좌표 매칭.
 *
 * <p>여기 적힌 문자열은 전부 <b>API-Football 이 실제로 준 값</b>이다. 맨유 2023-24
 * 원정 경기장에서 그대로 뽑았다 — 지어낸 예시가 아니라, 좌표를 못 찾아 이동거리가
 * 부분합이 되게 만든 바로 그 값들이다.
 *
 * <p>시드를 채우는 것보다 <b>이름을 맞추는 게 먼저</b>였다. 못 찾은 11곳 중 2곳은
 * 도시가 이미 시드에 있었는데 {@code "Nottingham, Nottinghamshire"} 처럼 주(州)명이
 * 붙어 있어 못 찾은 것이었다.
 */
@SpringBootTest
@ActiveProfiles("test")
class SeedCatalogCityTest {

	@Autowired SeedCatalog seedCatalog;

	@Test
	@DisplayName("쉼표 뒤 주(州)명이 붙어 있어도 찾는다 — 시드에 이미 있던 도시들")
	void stripsTheCountySuffix() {
		// 이 둘은 시드에 도시가 있었는데 이름 형식 때문에 못 찾던 것들이다
		assertThat(seedCatalog.coordinates("Nottingham, Nottinghamshire"))
				.isEqualTo(seedCatalog.coordinates("Nottingham"));
		assertThat(seedCatalog.coordinates("Wolverhampton, West Midlands"))
				.isEqualTo(seedCatalog.coordinates("Wolverhampton"));
	}

	@Test
	@DisplayName("현지어 표기를 영문 시드 키로 옮긴다")
	void mapsLocalSpellings() {
		assertThat(seedCatalog.coordinates("München"))
				.isEqualTo(seedCatalog.coordinates("Munich"));
		assertThat(seedCatalog.coordinates("København"))
				.isEqualTo(seedCatalog.coordinates("Copenhagen"));
	}

	@Test
	@DisplayName("맨유 2023-24 원정에서 좌표를 못 찾던 11곳이 이제 전부 잡힌다")
	void resolvesEveryVenueThatUsedToBeMissing() {
		String[] asApiGaveThem = {
				"Nottingham, Nottinghamshire", "Bournemouth, Dorset", "Brentford, Middlesex",
				"Burnley", "Falmer, East Sussex", "København", "Luton, Bedfordshire",
				"München", "Sheffield", "Wigan", "Wolverhampton, West Midlands",
		};
		for (String city : asApiGaveThem) {
			assertThat(seedCatalog.coordinates(city))
					.as("API 가 준 도시명 %s", city)
					.isNotNull();
		}
	}

	@Test
	@DisplayName("모르는 도시는 여전히 null — 정규화가 없는 좌표를 만들어 내지 않는다")
	void stillReturnsNullForUnknownCities() {
		assertThat(seedCatalog.coordinates("Nowhere, Neverland")).isNull();
		assertThat(seedCatalog.coordinates("존재하지않는도시")).isNull();
		assertThat(seedCatalog.coordinates(null)).isNull();
		assertThat(seedCatalog.coordinates("   ")).isNull();
	}

	@Test
	@DisplayName("비슷한 이름으로 억지 매칭하지 않는다 — 엉뚱한 좌표는 좌표 없음보다 나쁘다")
	void doesNotFuzzyMatch() {
		// "Manchester" 가 시드에 있지만 이건 다른 도시다
		assertThat(seedCatalog.coordinates("Manchester-by-the-Sea")).isNull();
		assertThat(seedCatalog.coordinates("New London")).isNull();
	}
}
