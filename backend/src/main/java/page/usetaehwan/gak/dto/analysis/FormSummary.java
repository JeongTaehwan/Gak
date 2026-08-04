package page.usetaehwan.gak.dto.analysis;

import java.util.List;
import page.usetaehwan.gak.domain.Pick;

/**
 * 폼 — 전 대회를 통합한 승/무/패와 승점률.
 *
 * <h2>"최근 N경기"가 아니다</h2>
 * <p>예전에는 최근 6경기만 셌다. 그러면 이 카드만 다른 기간을 보게 되고, 옆에 놓인
 * 밀집 구간·이동거리와 분모가 어긋난다. 지금은 <b>진단 기간 전체</b>(그 시즌에서 치른
 * 경기)를 센다 — 기간이 무엇인지는 {@link AnalysisWindow}가 말한다.
 *
 * <p>비율({@link #pointsRate})은 표본이 {@link SampleConfidence#MIN_SAMPLE_FOR_RATE}경기
 * 미만이면 <b>계산하지 않는다</b>(null). 대신 {@link #wins}/{@link #draws}/{@link #losses}
 * 같은 <b>원래의 개수</b>는 표본이 1경기여도 항상 채운다 — "2경기 1승 1패"는 표본이
 * 작다는 사실까지 함께 보여 주지만, "승점률 66.7%"는 그 사실을 감춘다.
 *
 * <p>승부차기로 갈린 컵경기(PEN)는 <b>무승부</b>로 센다. 축구 통계의 관례이기도 하고,
 * 이 앱이 폼에서 재려는 건 "진출했는가"가 아니라 "90/120분 동안 얼마나 잘했는가"이기
 * 때문이다. (진출 여부는 밀집도 쪽에서 "연장을 뛰었다"는 부하로 이미 반영된다.)
 *
 * @param sampleSize       실제로 셀 수 있었던 경기 수(결과가 확정된 경기만). 기간 안의
 *                         경기라도 진행 중이거나 득점이 안 들어왔으면 여기서 빠진다
 * @param recent           날짜 오름차순 승/무/패. 화면의 폼 스트릭이 그대로 그린다
 * @param wins             승
 * @param draws            무
 * @param losses           패
 * @param points           획득 승점(승 3, 무 1)
 * @param maxPoints        가능한 최대 승점({@code sampleSize × 3}) — 비율의 분모를 드러낸다
 * @param pointsRate       승점률 {@code points / maxPoints} (0.0~1.0). 표본 부족 시 null
 * @param opponentStrength 상대 강도(붙은 상대들의 순위 평균). 순위 데이터가 없으면 null
 * @param confidence       표본 크기 등급
 */
public record FormSummary(
		int sampleSize,
		List<Pick> recent,
		int wins,
		int draws,
		int losses,
		int points,
		int maxPoints,
		Double pointsRate,
		Double opponentStrength,
		SampleConfidence confidence
) {

	public static FormSummary empty() {
		return new FormSummary(0, List.of(), 0, 0, 0, 0, 0,
				null, null, SampleConfidence.NONE);
	}
}
