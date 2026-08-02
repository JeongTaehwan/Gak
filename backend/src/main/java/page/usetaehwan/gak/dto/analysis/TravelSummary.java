package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;

/**
 * 특정 기간의 누적 이동거리.
 *
 * <p>모델은 <b>홈 기지 왕복이 아닌 편도</b>다. 원정 경기 하나당 "홈 구장 → 그 경기장"
 * 한 번을 센다. 실제로는 돌아오는 이동도 있고 원정 두 경기를 붙여 도는 일정도 있지만,
 * 우리가 가진 사실은 경기장 위치뿐이라 그 이상을 지어내지 않는다. 지표의 목적은
 * 절대 km를 맞히는 게 아니라 <b>기간끼리 비교</b>하는 것이므로 일관된 기준이면 충분하다.
 *
 * <p>{@link #totalKm}는 좌표를 아는 경기만 더한 <b>부분합</b>일 수 있다. 그래서
 * {@link #measuredMatches}(잰 경기 수)와 {@link #unknownCoordinateMatches}(좌표를 몰라
 * 못 잰 경기 수)를 항상 함께 준다. 8번 중 3번만 잰 1,200km를 "이 기간 총 이동 1,200km"로
 * 읽으면 안 되기 때문이다.
 *
 * @param from                     집계 시작(포함)
 * @param to                       집계 끝(포함)
 * @param awayMatches              기간 내 원정 경기 수
 * @param measuredMatches          그중 좌표가 있어 실제로 잰 경기 수
 * @param unknownCoordinateMatches 그중 좌표가 없어 못 잰 경기 수
 * @param totalKm                  잰 경기들의 이동거리 합. 잰 경기가 0이면 null
 * @param averageKmPerMeasuredMatch 잰 경기당 평균 이동거리. 잰 경기가 0이면 null
 * @param longestTripKm            가장 멀었던 한 번의 이동. 잰 경기가 0이면 null
 */
public record TravelSummary(
		Instant from,
		Instant to,
		int awayMatches,
		int measuredMatches,
		int unknownCoordinateMatches,
		Double totalKm,
		Double averageKmPerMeasuredMatch,
		Double longestTripKm
) {

	public static TravelSummary empty(Instant from, Instant to) {
		return new TravelSummary(from, to, 0, 0, 0, null, null, null);
	}
}
