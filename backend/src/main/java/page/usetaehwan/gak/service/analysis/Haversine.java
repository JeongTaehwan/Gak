package page.usetaehwan.gak.service.analysis;

import page.usetaehwan.gak.domain.Venue;

/**
 * 두 지점 사이의 <b>대권 거리</b>(great-circle distance) — 구(球) 위를 따라간 최단 거리.
 *
 * <h2>왜 좌표 차이를 그냥 못 쓰나</h2>
 * <p>위경도는 평면 좌표가 아니라 <b>각도</b>다. 위도 1도는 어디서나 약 111km지만,
 * 경도 1도의 실제 거리는 위도에 따라 달라진다 — 적도에서 111km, 런던(북위 51도)에서
 * 약 70km, 극점에서 0km다. 지구가 구라 위로 갈수록 경도선이 서로 모이기 때문이다.
 * 그래서 {@code √(Δlat² + Δlon²)}(피타고라스)로 재면 유럽 위도에서 동서 거리가
 * 1.5배쯤 부풀고, 남북·동서가 섞인 이동은 더 어긋난다.
 *
 * <p>비유하면 지구본에 실을 대고 재는 것과, 지구본을 종이에 눌러 편 지도 위에서 자로
 * 재는 것의 차이다. 눌러 편 지도는 극 쪽이 늘어나 있어(메르카토르에서 그린란드가
 * 아프리카만 해 보이는 이유) 그 위의 직선 길이는 실제 거리가 아니다.
 *
 * <p>하버사인은 두 지점을 구 표면 위의 점으로 보고 그 사이 <b>호(arc)의 길이</b>를 낸다.
 * 두 점을 잇는 중심각 θ를 구한 뒤 {@code 거리 = 지구 반지름 × θ}로 환산하는 것이다.
 * 이름의 유래인 haversine 함수(hav θ = sin²(θ/2))를 쓰는 이유는, 두 점이 아주 가까울 때
 * 코사인 공식이 부동소수점에서 정밀도를 잃는 문제를 피하기 위해서다.
 *
 * <h2>정확도</h2>
 * <p>지구는 완전한 구가 아니라 적도 쪽이 부푼 타원체라, 하버사인에는 최대 0.5% 정도
 * 오차가 있다(런던–맨체스터 262km에서 1km 남짓). 여기서 재려는 건 "이 팀이 이 기간에
 * 얼마나 돌아다녔나"이고, 애초에 경기장이 아니라 <b>도시</b> 좌표를 쓰고 있으며 실제
 * 이동은 직선이 아니라 도로·항로다. 이 용도에서 타원체 공식(Vincenty)까지 갈 이유가 없다.
 */
public final class Haversine {

	/** 지구 평균 반지름(km, IUGG). */
	public static final double EARTH_RADIUS_KM = 6371.0088;

	private Haversine() {
	}

	/**
	 * 두 경기장 사이 거리(km). <b>둘 중 하나라도 좌표가 없으면 null</b>을 돌려준다.
	 *
	 * <p>API가 경기장 좌표를 주지 않아 시드로 주요 도시만 채운 상태다. 좌표가 없는 건
	 * 흔한 정상 상황이지 오류가 아니므로, 예외를 던지지 않고 "이 지표는 생략"을 뜻하는
	 * null로 표현한다. 호출부는 null을 0으로 접지 말 것 — "이동하지 않았다"와
	 * "이동거리를 모른다"는 전혀 다른 사실이다.
	 */
	public static Double distanceKm(Venue from, Venue to) {
		if (from == null || to == null || !from.hasCoordinates() || !to.hasCoordinates()) {
			return null;
		}
		return distanceKm(from.getLatitude(), from.getLongitude(),
				to.getLatitude(), to.getLongitude());
	}

	/** 위경도(도 단위) 두 쌍 사이의 대권 거리(km). */
	public static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
		double phi1 = Math.toRadians(lat1);
		double phi2 = Math.toRadians(lat2);
		double deltaPhi = Math.toRadians(lat2 - lat1);
		double deltaLambda = Math.toRadians(lon2 - lon1);

		// hav(θ) = sin²(Δφ/2) + cos φ₁ · cos φ₂ · sin²(Δλ/2)
		// 경도 차이에 cos φ가 곱해지는 이 부분이 "위도가 높을수록 경도 1도가 짧다"를 담는다.
		double h = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2)
				+ Math.cos(phi1) * Math.cos(phi2)
				* Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2);

		// 반올림 오차로 h가 1을 아주 살짝 넘으면 asin이 NaN이 된다(지구 반대편 좌표에서 발생).
		double centralAngle = 2 * Math.asin(Math.min(1.0, Math.sqrt(h)));
		return EARTH_RADIUS_KM * centralAngle;
	}
}
