package page.usetaehwan.gak.dto.analysis;

import java.util.List;

/**
 * 일정 밀집도 진단 결과.
 *
 * <p>밀집 구간이 <b>없을 때</b>도 정보가 있어야 한다. 빈 목록만 내려보내면 "여유로운
 * 일정"과 "기준에 한 끗 모자란 빡빡한 일정"을 구분할 수 없고, 표본이 적어서 애초에
 * 판정이 불가능한 경우와도 섞인다. {@link #busiestWindowMatchCount}와
 * {@link #detectable}이 그 셋을 갈라 준다.
 *
 * @param windowDays              적용한 창 폭(일). 양 끝 포함
 * @param minMatches              적용한 밀집 기준 경기 수
 * @param detectable              판정이 가능한 표본이었는가(전체 경기 수 ≥ minMatches)
 * @param analyzedMatchCount      판정에 실제로 쓴 경기 수(연기·취소 제외 후)
 * @param busiestWindowMatchCount 가장 빡빡했던 {@code windowDays}일 창의 경기 수
 * @param shortestGapDays         전체에서 가장 짧았던 경기 간격(일). 경기가 2개 미만이면 null
 * @param medianGapDays           경기 간격의 <b>중앙값</b>(일). 아래 설명 참고. 2개 미만이면 null
 * @param spans                   병합된 밀집 구간들(시간 오름차순)
 */
public record CongestionReport(
		int windowDays,
		int minMatches,
		boolean detectable,
		int analyzedMatchCount,
		int busiestWindowMatchCount,
		Integer shortestGapDays,
		Double medianGapDays,
		List<CongestionSpanView> spans
) {

	/**
	 * 간격의 대표값으로 평균이 아니라 <b>중앙값</b>을 쓰는 이유.
	 *
	 * <p>한 시즌 간격에는 3일이 줄줄이 있다가 인터내셔널 브레이크나 겨울 휴식기에 24일이
	 * 한 번 끼어든다. 평균은 이 극단값에 통째로 끌려간다 — 3,3,4,3,24의 평균은 7.4일이라
	 * "일주일에 한 번 뛰는 여유로운 팀"처럼 읽히지만, 실제로는 다섯 경기 중 넷이 나흘 이내다.
	 * 중앙값(3일)은 "보통 며칠 만에 다시 뛰는가"를 그대로 말해 준다.
	 */
	public static CongestionReport of(int windowDays, int minMatches, boolean detectable,
	                                  int analyzedMatchCount, int busiestWindowMatchCount,
	                                  Integer shortestGapDays, Double medianGapDays,
	                                  List<CongestionSpanView> spans) {
		return new CongestionReport(windowDays, minMatches, detectable, analyzedMatchCount,
				busiestWindowMatchCount, shortestGapDays, medianGapDays, List.copyOf(spans));
	}
}
