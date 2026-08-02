package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import page.usetaehwan.gak.domain.FixtureStatus;

/**
 * 한 경기가 이 팀의 일정에 얹은 부하. 진단 계산의 최소 단위이자, 타임라인 화면이
 * 한 줄로 그릴 값이다.
 *
 * <p>여기엔 <b>숫자와 사실만</b> 담는다. "3/17"·"3일 휴식" 같은 표기 문자열은 넣지 않는다.
 * 날짜 포맷과 문구는 화면의 몫이고, 서버가 문자열을 만들어 내려보내면 화면이 바꿀 때마다
 * 서버를 고쳐야 한다.
 *
 * @param fixtureId        경기 id (화면이 자기 목록과 맞출 때 쓰는 유일한 열쇠)
 * @param kickoff          킥오프(UTC)
 * @param competitionId    대회 id (이름은 유일하지 않으므로 판별은 항상 id로)
 * @param competitionName  대회 표기명(한글 우선)
 * @param opponentId       상대 팀 id
 * @param opponentName     상대 팀 표기명(한글 우선, 없으면 영문)
 * @param home             우리 팀이 홈이면 true
 * @param status           경기 상태(화면이 "예정/진행 중"을 구분할 수 있게 그대로 준다)
 * @param gapDays          직전 경기와의 간격(일). 목록의 첫 경기는 null
 * @param congestionSpanId 소속된 밀집 구간 id. 밀집이 아니면 null
 * @param extraMinutes     정규시간 초과 소화 시간(분). 연장 없으면 0
 * @param travelKm         이 경기를 위한 이동거리(km). 홈경기는 0, 좌표를 모르면 <b>null</b>
 */
public record MatchLoad(
		long fixtureId,
		Instant kickoff,
		long competitionId,
		String competitionName,
		long opponentId,
		String opponentName,
		boolean home,
		FixtureStatus status,
		Integer gapDays,
		Integer congestionSpanId,
		int extraMinutes,
		Double travelKm
) {
}
