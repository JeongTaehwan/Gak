// requirements.md TL 5절 — 경기 선택 시 결장 명단과 사유 갈래
package page.usetaehwan.gak.dto.analysis;

import page.usetaehwan.gak.domain.AbsenceReason;

/**
 * 한 경기에서 빠진 선수 한 명. 타임라인에서 경기를 선택했을 때 보여줄 명단의 한 줄이다.
 *
 * <p><b>확정 결장만 싣는다.</b> 불투명(Questionable)은 {@code absentCount}에서 빼는 것과
 * 같은 이유로 명단에서도 뺀다 — 나올지 모르는 선수를 "빠졌다"고 적으면 명단이 부풀고,
 * 인원수와 명단 길이가 어긋난다.
 *
 * @param playerId   선수 id
 * @param playerName 선수 이름(API 원문)
 * @param reason     결장 사유 갈래(부상/징계/질병/차출/기타)
 */
public record MatchAbsentee(
		long playerId,
		String playerName,
		AbsenceReason reason
) {
}
