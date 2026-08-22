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
 * @param teamCode    3글자 코드(예 "MUN"). 화면의 팀 마크가 쓴다. 없을 수 있다
 * @param generatedAt 이 결과를 계산한 시각(서버 시계)
 * @param window      무엇을 보고 무엇을 뺐는가
 * @param matches     날짜순 경기별 부하(간격·밀집 소속·이동거리)
 * @param congestion  일정 밀집도(전 대회 기준)
 * @param leagueCongestion 자국 리그 경기만으로 <b>다시 판정한</b> 밀집도. "리그만 보기"가
 *                    쓴다 — 전 대회 값을 화면이 걸러서 다시 세면 계산이 두 곳이 된다.
 *                    같은 스냅샷에서 함께 계산하므로 두 값이 서로 다른 순간을 볼 수 없다
 * @param form        최근 폼
 * @param travel      누적 이동거리
 * @param absences    결장 요약(부상·징계·질병 등)
 * @param omissions   계산하지 못한 지표와 그 이유
 * @param syncCoverage 조회 시즌에 대한 노출 대회별 동기화 상태. lastSuccessAt이 null인
 *                    대회는 "수집 전" — 화면이 경기 부재를 대회 단위로만 말할 근거
 */
public record TeamDiagnostics(
		long teamId,
		String teamName,
		String teamCode,
		Instant generatedAt,
		AnalysisWindow window,
		List<MatchLoad> matches,
		CongestionReport congestion,
		CongestionReport leagueCongestion,
		FormSummary form,
		OpponentStrength opponentStrength,
		TravelSummary travel,
		AbsenceSummary absences,
		List<Omission> omissions,
		List<CompetitionSyncStatus> syncCoverage
) {
}
