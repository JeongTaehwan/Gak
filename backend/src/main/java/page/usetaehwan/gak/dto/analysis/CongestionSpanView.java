package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;

/**
 * 병합된 밀집 구간 하나.
 *
 * <p>구간의 경계를 <b>배열 인덱스가 아니라 경기 id로</b> 준다. 인덱스는 "누가 만든
 * 어떤 목록의 몇 번째"인지에 의존하는데, 서버는 연기·취소된 경기를 빼고 세고 화면은
 * 그것까지 그릴 수 있어 두 목록의 번호가 어긋난다. id는 그런 전제 없이 항상 같은 경기를
 * 가리킨다.
 *
 * @param id                  응답 안에서만 유효한 일련번호(0부터). 저장하지 않는 값이다
 * @param startFixtureId      구간 첫 경기 id
 * @param endFixtureId        구간 마지막 경기 id
 * @param from                구간 첫 경기 킥오프
 * @param to                  구간 마지막 경기 킥오프
 * @param spanDays            구간 폭(일) — 첫 경기와 마지막 경기의 날짜 차이
 * @param matchCount          구간 안 경기 수
 * @param awayCount           그중 원정 경기 수
 * @param extraTimeMatchCount 그중 연장·승부차기까지 간 경기 수
 * @param extraMinutes        그로 인해 정규시간을 넘겨 더 뛴 총 시간(분)
 * @param shortestGapDays     구간 안에서 가장 짧았던 경기 간격(일)
 * @param travelKm            구간 중 이동거리 합(km). 좌표를 모르는 경기가 있으면 <b>부분합</b>
 * @param travelUnknownCount  좌표가 없어 이동거리에 못 넣은 원정 경기 수
 */
public record CongestionSpanView(
		int id,
		long startFixtureId,
		long endFixtureId,
		Instant from,
		Instant to,
		int spanDays,
		int matchCount,
		int awayCount,
		int extraTimeMatchCount,
		int extraMinutes,
		int shortestGapDays,
		Double travelKm,
		int travelUnknownCount
) {
}
