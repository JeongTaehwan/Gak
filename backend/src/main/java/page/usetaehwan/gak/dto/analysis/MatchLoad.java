package page.usetaehwan.gak.dto.analysis;

import java.time.Instant;
import java.util.List;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Pick;

/**
 * 한 경기가 이 팀의 일정에 얹은 부하. 진단 계산의 최소 단위이자, 타임라인 화면이
 * 한 줄로 그릴 값이다.
 *
 * <p>여기엔 <b>숫자와 사실만</b> 담는다. "3/17"·"3일 휴식" 같은 표기 문자열은 넣지 않는다.
 * 날짜 포맷과 문구는 화면의 몫이고, 서버가 문자열을 만들어 내려보내면 화면이 바꿀 때마다
 * 서버를 고쳐야 한다.
 *
 * <h2>판정은 여기서 끝난다</h2>
 * <p>승/무/패({@link #result}), 대회 성격({@link #competitionType}), 홈/원정({@link #home})은
 * 모두 <b>서버가 정한 값</b>이고, 화면은 그리기만 한다. 같은 판정 규칙을 화면에도 두면
 * 한쪽만 고쳤을 때 타임라인의 승패와 (같은 데이터를 읽는) AI 진단의 승패가 갈린다 —
 * 사용자에게는 앱이 자기 말을 뒤집는 것으로 보인다. 판정의 출처는 하나여야 한다.
 *
 * <h2>승부차기 — 집계와 표시를 분리한다</h2>
 * <p>{@link #result}는 승부차기로 갈린 경기를 <b>무승부(D)</b>로 준다({@link FormSummary} 참고).
 * 대신 {@link #shootoutFor}/{@link #shootoutAgainst}로 PK 스코어 자체를 함께 주어, 화면이
 * "PK 승" 같은 표기를 따로 붙일 수 있게 한다. 진출을 W로 세면 "120분을 뛰었다"는 피로
 * 신호가 폼에서 지워지고, 반대로 PK 결과를 감추면 화면이 사실을 빠뜨린다. 서로 다른
 * 질문이므로 서로 다른 필드로 답한다.
 *
 * <h2>중립 경기는 아직 다루지 않는다</h2>
 * <p>{@link #home}이 boolean인 건 중립 경기(결승 등)를 표현할 자리가 없다는 뜻이다.
 * API가 중립 여부를 주지 않기 때문이다. 경기장 이름으로 추측할 수도 있지만(웸블리면 중립?)
 * 웸블리는 토트넘의 임시 홈이었던 적이 있고 유럽 결승 장소는 매년 바뀐다. 틀린 사실을
 * 지어내느니 없는 채로 두고, 중립 플래그가 생기면 그때 필드를 늘린다.
 *
 * @param fixtureId            경기 id (화면이 자기 목록과 맞출 때 쓰는 유일한 열쇠)
 * @param kickoff              킥오프(UTC)
 * @param competitionId        대회 id (이름은 유일하지 않으므로 판별은 항상 id로)
 * @param competitionName      대회 표기명(한글 우선)
 * @param competitionShortName 좁은 자리(타임라인 뱃지)용 짧은 표기명
 * @param competitionType      대회 성격(LEAGUE/CUP/HYBRID). 화면의 대회 색 구분 기준
 * @param opponentId           상대 팀 id
 * @param opponentName         상대 팀 표기명(한글 우선, 없으면 영문)
 * @param home                 우리 팀이 홈이면 true
 * @param status               경기 상태(화면이 "예정/진행 중"을 구분할 수 있게 그대로 준다)
 * @param result               우리 팀 관점 승/무/패. <b>결과 미확정이면 null</b>
 * @param goalsFor             우리 팀 득점(연장 포함 최종). 미확정이면 null
 * @param goalsAgainst         우리 팀 실점(연장 포함 최종). 미확정이면 null
 * @param shootoutFor          승부차기 우리 득점. 승부차기가 없었으면 null
 * @param shootoutAgainst      승부차기 상대 득점. 승부차기가 없었으면 null
 * @param gapDays              직전 경기와의 간격(일). 목록의 첫 경기는 null
 * @param congestionSpanId     소속된 밀집 구간 id. 밀집이 아니면 null
 * @param leagueGapDays        직전 <b>리그</b> 경기와의 간격(일). 리그 경기가 아니거나
 *                             첫 리그 경기면 null — 리그만 보기가 컵을 뺀 간격을 그릴 근거.
 *                             화면이 날짜를 빼서 다시 만들면 계산이 두 곳이 된다
 * @param leagueCongestionSpanId 리그 경기만으로 다시 판정한 밀집 구간에서의 소속 id.
 *                             {@link TeamDiagnostics#leagueCongestion()}의 구간을 가리킨다.
 *                             리그 경기가 아니거나 리그 기준 밀집이 아니면 null
 * @param extraMinutes         정규시간 초과 소화 시간(분). 연장 없으면 0
 * @param travelKm             이 경기를 위한 이동거리(km). 홈경기는 0, 좌표를 모르면 <b>null</b>
 * @param absentCount          이 경기에 빠진 확정 결장 인원. <b>결장 데이터가 없는 경기는 null</b>
 *                             — 0(아무도 안 빠짐)과 "모름"은 다르다
 * @param absentees            이 경기의 확정 결장 명단(사유 갈래 포함). {@code absentCount}와
 *                             같은 규칙 — <b>데이터가 없는 경기는 null</b>, 확인했는데 확정
 *                             결장이 없으면 빈 목록. 길이는 항상 {@code absentCount}와 같다
 * @param inAnalysis           이 경기가 진단 계산에 들어갔는가. 아직 치르지 않은 경기는 false다
 *                             — 목록에는 실리지만(타임라인·예측이 쓴다) 폼·밀집도·이동거리
 *                             어디에도 들어가지 않는다. 화면이 "여기까지 치렀다"를 그릴 근거
 */
public record MatchLoad(
		long fixtureId,
		Instant kickoff,
		long competitionId,
		String competitionName,
		String competitionShortName,
		CompetitionType competitionType,
		long opponentId,
		String opponentName,
		/** 이 경기 <b>직전</b>의 상대 리그 순위. 컵이거나 시즌 초라 말할 수 없으면 null */
		Integer opponentRank,
		boolean home,
		FixtureStatus status,
		Pick result,
		Integer goalsFor,
		Integer goalsAgainst,
		Integer shootoutFor,
		Integer shootoutAgainst,
		Integer gapDays,
		Integer congestionSpanId,
		Integer leagueGapDays,
		Integer leagueCongestionSpanId,
		int extraMinutes,
		Double travelKm,
		Integer absentCount,
		List<MatchAbsentee> absentees,
		boolean inAnalysis
) {
}
