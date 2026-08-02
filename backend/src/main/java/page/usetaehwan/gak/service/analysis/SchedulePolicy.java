package page.usetaehwan.gak.service.analysis;

import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;

/**
 * "어떤 경기가 어떤 계산에 들어가는가" — 진단 계산의 모든 포함/제외 규칙을 한곳에 모은다.
 *
 * <p>이 규칙이 흩어지면 지표마다 답이 달라진다. 밀집도는 연기된 경기를 세고 폼은 안 세는
 * 식으로 어긋나면, 화면의 "14일 5경기"와 "최근 5경기 성적"이 서로 다른 5경기를 가리키게 된다.
 *
 * <h2>상태별 판정</h2>
 * <table>
 *   <tr><th>상태</th><th>일정(밀집도·이동)</th><th>폼(승/무/패)</th></tr>
 *   <tr><td>NS(예정)</td><td>포함</td><td>제외</td></tr>
 *   <tr><td>LIVE(진행 중)</td><td>포함</td><td>제외</td></tr>
 *   <tr><td>FT/AET/PEN(종료)</td><td>포함</td><td>포함</td></tr>
 *   <tr><td>PST/CANC(연기·취소)</td><td>제외</td><td>제외</td></tr>
 *   <tr><td>ABD(중단·미지의 코드)</td><td>제외</td><td>제외</td></tr>
 * </table>
 *
 * <h2>LIVE를 이렇게 가른 이유</h2>
 * <p>LIVE는 <b>일정에는 넣고 폼에서는 뺀다.</b> 이 앱이 일정에서 재는 것은 "몸이 얼마나
 * 자주 불려 나가는가"인데, 진행 중인 경기는 이미 킥오프했으므로 그 부하는 <b>이미 발생한
 * 사실</b>이다. 반면 결과는 아직 사실이 아니다 — 후반 40분 1-0은 5분 뒤 1-1이 될 수 있고,
 * 그 값으로 승점률을 내면 새로고침할 때마다 숫자가 흔들린다. "확정된 것만 성적으로 센다"는
 * 이 앱의 다른 규칙(예측은 킥오프 이전에만, 채점은 종료 후에만)과도 결이 같다.
 *
 * <p>NS(예정)도 같은 이유로 일정에는 넣는다. 다가올 3주에 7경기가 잡혀 있다는 건 지금
 * 진단해야 할 사실이지, 그 경기가 끝나야 알 수 있는 게 아니다.
 *
 * <p>PST/CANC는 <b>일정에서도 뺀다.</b> 연기된 경기는 그날 뛰지 않았다. 남겨 두면 밀집도가
 * 실제보다 부풀고("14일 5경기"인데 실은 4경기), 그 위에 쌓는 진단이 통째로 틀어진다.
 * ABD는 중단된 경기이고 우리 파서에서 미지의 코드(AWD 몰수승, WO 부전승)도 여기로 접히므로,
 * "실제 소화 시간을 알 수 없는 경기"로 보고 함께 뺀다.
 */
public final class SchedulePolicy {

	/** 정규시간(분). 연장 초과분 계산의 기준선. */
	public static final int REGULATION_MINUTES = 90;

	/** 연장 한 세트(전·후반 15분씩). */
	public static final int EXTRA_TIME_MINUTES = 30;

	private SchedulePolicy() {
	}

	/**
	 * 일정 부하 계산(간격·밀집도·이동거리)에 넣을 경기인가.
	 * 예정·진행 중·종료된 경기 = "실제로 뛰거나 뛸 경기".
	 */
	public static boolean countsForSchedule(Fixture fixture) {
		if (fixture == null || fixture.getKickoff() == null) {
			return false;
		}
		FixtureStatus status = fixture.getStatus();
		return status == FixtureStatus.NS
				|| status == FixtureStatus.LIVE
				|| status.isFinished();
	}

	/**
	 * 폼(승/무/패·승점률) 계산에 넣을 경기인가.
	 * 결과가 확정(FT/AET/PEN)됐고 득점이 실제로 들어와 있어야 한다.
	 *
	 * <p>상태만 믿지 않고 득점 null을 함께 보는 건, 동기화가 상태를 먼저 받고 득점이
	 * 비어 오는 순간이 실제로 있기 때문이다. 그때 0-0으로 읽으면 있지도 않은 무승부가 생긴다.
	 */
	public static boolean countsForForm(Fixture fixture) {
		return fixture != null
				&& fixture.isFinished()
				&& fixture.getGoalsHome() != null
				&& fixture.getGoalsAway() != null;
	}

	/**
	 * 정규시간을 넘겨 더 소화한 시간(분). 연장 없으면 0.
	 *
	 * <p>AET(연장 종료)와 PEN(승부차기 종료)은 둘 다 연장 30분을 뛴 뒤에 갈린 경기다.
	 * 승부차기 자체는 뛰는 시간이 아니라 부하로 세지 않는다.
	 *
	 * <p>{@code elapsed} 필드(진행 분)를 쓰지 않는 이유: 이 값은 종료 경기에서도 null로
	 * 오는 경우가 있고, 와도 90/120 같은 반올림 값이라 추가시간을 담지 못한다. 우리가
	 * 필요한 정보는 "연장을 뛰었는가" 하나뿐이라, 더 신뢰할 수 있는 상태 코드로 판정한다.
	 */
	public static int extraMinutes(Fixture fixture) {
		if (fixture == null) {
			return 0;
		}
		FixtureStatus status = fixture.getStatus();
		return (status == FixtureStatus.AET || status == FixtureStatus.PEN)
				? EXTRA_TIME_MINUTES
				: 0;
	}

	/** 연장까지 간 경기인가(밀집 구간 요약의 "연장 N" 집계용). */
	public static boolean wentToExtraTime(Fixture fixture) {
		return extraMinutes(fixture) > 0;
	}
}
