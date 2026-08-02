package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.domain.Venue;

class HaversineTest {

	private static final double MANCHESTER_LAT = 53.4808;
	private static final double MANCHESTER_LON = -2.2426;
	private static final double LONDON_LAT = 51.5074;
	private static final double LONDON_LON = -0.1278;
	private static final double MUNICH_LAT = 48.1351;
	private static final double MUNICH_LON = 11.5820;

	private static Venue venue(long id, Double lat, Double lon) {
		return Venue.builder().id(id).name("v" + id).latitude(lat).longitude(lon).build();
	}

	@Test
	@DisplayName("맨체스터–런던 약 262km")
	void manchesterToLondon() {
		double km = Haversine.distanceKm(MANCHESTER_LAT, MANCHESTER_LON, LONDON_LAT, LONDON_LON);

		// 실제 대권 거리 262.0km. 허용오차 1km는 좌표를 도시 중심으로 잡은 오차보다도 작다.
		assertThat(km).isCloseTo(262.0, within(1.0));
	}

	@Test
	@DisplayName("맨체스터–뮌헨 약 1136km (유럽 원정)")
	void manchesterToMunich() {
		double km = Haversine.distanceKm(MANCHESTER_LAT, MANCHESTER_LON, MUNICH_LAT, MUNICH_LON);

		assertThat(km).isCloseTo(1136.0, within(2.0));
	}

	@Test
	@DisplayName("같은 지점은 0km")
	void samePointIsZero() {
		assertThat(Haversine.distanceKm(LONDON_LAT, LONDON_LON, LONDON_LAT, LONDON_LON))
				.isCloseTo(0.0, within(0.001));
	}

	@Test
	@DisplayName("방향이 바뀌어도 거리는 같다")
	void isSymmetric() {
		double there = Haversine.distanceKm(MANCHESTER_LAT, MANCHESTER_LON, LONDON_LAT, LONDON_LON);
		double back = Haversine.distanceKm(LONDON_LAT, LONDON_LON, MANCHESTER_LAT, MANCHESTER_LON);

		assertThat(there).isCloseTo(back, within(0.000001));
	}

	@Test
	@DisplayName("경도 1도의 실제 거리는 위도에 따라 다르다 — 좌표 차이를 그냥 못 쓰는 이유")
	void oneDegreeOfLongitudeShrinksWithLatitude() {
		double atEquator = Haversine.distanceKm(0, 0, 0, 1);
		double atLondonLatitude = Haversine.distanceKm(51, 0, 51, 1);

		assertThat(atEquator).isCloseTo(111.2, within(0.5));
		assertThat(atLondonLatitude).isCloseTo(70.0, within(0.5));

		// 위도 1도는 어디서나 같은데 경도 1도는 영국 위도에서 63%로 줄어든다.
		// 단순 좌표 차이(피타고라스)로 재면 동서 이동이 1.6배 부풀어 나온다.
		double oneDegreeOfLatitude = Haversine.distanceKm(0, 0, 1, 0);
		assertThat(atLondonLatitude / oneDegreeOfLatitude).isCloseTo(0.63, within(0.02));
	}

	@Test
	@DisplayName("한쪽이라도 좌표가 없으면 null — 예외가 아니다")
	void missingCoordinatesYieldNull() {
		Venue withCoords = venue(1L, MANCHESTER_LAT, MANCHESTER_LON);
		Venue noCoords = venue(2L, null, null);
		Venue halfCoords = venue(3L, MANCHESTER_LAT, null);

		assertThat(Haversine.distanceKm(withCoords, noCoords)).isNull();
		assertThat(Haversine.distanceKm(noCoords, withCoords)).isNull();
		assertThat(Haversine.distanceKm(withCoords, halfCoords)).isNull();
		assertThat(Haversine.distanceKm(withCoords, null)).isNull();
		assertThat(Haversine.distanceKm(null, null)).isNull();
	}

	@Test
	@DisplayName("좌표가 둘 다 있으면 Venue로도 같은 값이 나온다")
	void venueOverloadMatchesRawCoordinates() {
		Double km = Haversine.distanceKm(
				venue(1L, MANCHESTER_LAT, MANCHESTER_LON),
				venue(2L, LONDON_LAT, LONDON_LON));

		assertThat(km).isNotNull();
		assertThat(km).isCloseTo(
				Haversine.distanceKm(MANCHESTER_LAT, MANCHESTER_LON, LONDON_LAT, LONDON_LON),
				within(0.000001));
	}
}
