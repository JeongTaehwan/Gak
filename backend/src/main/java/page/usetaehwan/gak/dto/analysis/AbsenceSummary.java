package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import page.usetaehwan.gak.domain.AbsenceReason;

/**
 * 결장 요약 — "이 기간에 얼마나 자주, 어떤 이유로 선수가 빠졌나".
 *
 * <p><b>"부상"이라고 부르지 않는다.</b> API의 {@code /injuries}에는 징계·질병·대표팀
 * 차출·감독 결정이 함께 들어온다(맨유 2023 시즌 346건 중 35건). 전부 부상으로 세면
 * 화면이 "부상 5명"이라 말하는데 그중 둘은 징계인 상태가 된다. 갈래별 개수를
 * {@link #byReason}으로 따로 준다.
 *
 * <p>{@link #coveredMatches}가 중요하다. API는 우리가 가진 모든 경기의 결장을 주지
 * 않는다 — 맨유 2023 시즌은 52경기 중 44경기만 데이터가 있었다(컵대회 누락). 그래서
 * "경기당 평균 결장 4.3명"을 쓰려면 분모가 52가 아니라 44여야 하고, 화면은 그 사실을
 * 밝혀야 한다. 안 그러면 데이터가 없는 경기를 "결장 0명"으로 읽는다.
 *
 * @param covered         결장 데이터가 하나라도 있는 경기가 존재하는가
 * @param coveredMatches  결장 데이터가 있는 경기 수(전체 경기 수가 아니다)
 * @param analyzedMatches 진단이 본 전체 경기 수 — 위와 비교하라고 함께 준다
 * @param totalOut        확정 결장 연인원(경기 × 선수). 같은 선수가 5경기 빠지면 5다
 * @param distinctPlayers 한 번이라도 결장한 서로 다른 선수 수
 * @param maxOutInOneMatch 한 경기 최대 결장 인원
 * @param byReason        사유 갈래별 확정 결장 연인원
 * @param topAbsentees    가장 많이 빠진 선수 상위 몇 명
 * @param lastSyncedAt    이 팀·시즌의 결장 데이터를 마지막으로 받아 온 시각.
 *                        <b>null이면 받은 적이 없다</b> — 그때의 "결장 0명"은 사실이
 *                        아니라 미수집이다. 화면은 이 값으로 <b>아직 안 받음</b>과
 *                        <b>받았지만 이 경기는 없음</b>을 갈라 말한다.
 *                        <p>⚠️ 이력이 있어도 경기별 결장을 0으로 채우지 않는다. API는 한
 *                        시즌 요청에도 일부 경기만 주기 때문이다(맨유 2023 시즌 52경기 중
 *                        44경기). 성공 이력은 "이 팀·시즌을 받아 왔다"까지만 보장한다
 */
public record AbsenceSummary(
		boolean covered,
		int coveredMatches,
		int analyzedMatches,
		int totalOut,
		int distinctPlayers,
		int maxOutInOneMatch,
		Map<AbsenceReason, Integer> byReason,
		List<AbsentPlayer> topAbsentees,
		Instant lastSyncedAt
) {

	/**
	 * @param playerId   선수 id
	 * @param playerName 표기명(영문 원본 — 선수 한글 매핑은 두지 않는다)
	 * @param matches    확정 결장 경기 수
	 * @param mainReason 가장 잦았던 사유 갈래
	 */
	public record AbsentPlayer(long playerId, String playerName, int matches,
	                           AbsenceReason mainReason) {
	}

	/**
	 * 결장 데이터가 아예 없을 때. "결장 0명"과 구분된다.
	 *
	 * @param lastSyncedAt 받아 온 이력이 있으면 그 시각. 받은 적이 없으면 null —
	 *                     "받아 봤는데 없더라"와 "아직 안 받았다"는 다른 말이다
	 */
	public static AbsenceSummary notCovered(int analyzedMatches, Instant lastSyncedAt) {
		return new AbsenceSummary(false, 0, analyzedMatches, 0, 0, 0, Map.of(), List.of(),
				lastSyncedAt);
	}
}
