package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import java.util.List;

/**
 * 한 팀의 진단 계산 결과 전체.
 *
 * <p><b>이 객체는 저장되지 않는다.</b> 요청마다 경기 사실(kickoff·결과·경기장 좌표)에서
 * 새로 계산해 만든다. 저장하면 경기 데이터가 갱신될 때 조용히 어긋나기 시작한다 —
 * 연기된 경기의 날짜가 바뀌면 밀집 구간도 바뀌어야 하는데, 저장된 값은 바뀌지 않는다.
 * {@link #generatedAt}은 그 사실을 드러내려고 넣었다.
 *
 * @param teamId      진단 대상 팀
 * @param teamName    표기명(한글 우선)
 * @param generatedAt 이 결과를 계산한 시각(서버 시계)
 * @param window      무엇을 보고 무엇을 뺐는가
 * @param matches     날짜순 경기별 부하(간격·밀집 소속·이동거리)
 * @param congestion  일정 밀집도
 * @param form        최근 폼
 * @param travel      누적 이동거리
 * @param omissions   계산하지 못한 지표와 그 이유
 */
public record TeamDiagnostics(
		long teamId,
		String teamName,
		Instant generatedAt,
		AnalysisWindow window,
		List<MatchLoad> matches,
		CongestionReport congestion,
		FormSummary form,
		TravelSummary travel,
		List<Omission> omissions
) {
}
