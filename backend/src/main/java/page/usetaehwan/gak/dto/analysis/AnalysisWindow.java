package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;

/**
 * 이번 계산이 무엇을 보고 무엇을 뺐는지.
 *
 * <p>진단 숫자보다 먼저 읽혀야 하는 값이다. "밀집 구간 0개"가 일정이 여유로워서인지,
 * 우리가 가진 경기가 3건뿐이어서인지를 여기서 알 수 있다.
 *
 * @param from             집계 대상의 첫 킥오프. 대상이 없으면 null
 * @param to               집계 대상의 마지막 킥오프. 대상이 없으면 null
 * @param totalFixtures    이 팀의 전체 경기 수(DB에 있는 것 전부)
 * @param analyzedFixtures 그중 실제로 계산에 넣은 수
 * @param excludedFixtures 연기·취소·중단으로 뺀 수
 */
public record AnalysisWindow(
		Instant from,
		Instant to,
		int totalFixtures,
		int analyzedFixtures,
		int excludedFixtures
) {
}
