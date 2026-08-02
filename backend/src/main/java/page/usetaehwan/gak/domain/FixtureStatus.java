package page.usetaehwan.gak.domain;

/**
 * 경기 진행 상태. API-Football의 {@code fixture.status.short} 값을 그대로 담는다.
 *
 * <p>스펙에서 강조한 종료 상태(FT/AET/PEN)는 결과 확정(예측 채점)의 기준이 된다.
 * 다만 "다음 경기 예측"을 하려면 아직 시작 안 한 경기({@link #NS})도 저장해야 하므로
 * 예측 앱이 실제로 다루는 라이프사이클 상태를 함께 담았다.
 * 사실만 저장한다는 원칙에 따라, 여기서 파생되는 값(폼·승점률 등)은 저장하지 않는다.
 */
public enum FixtureStatus {

	/** 예정 (Not Started) */
	NS(false),

	/** 진행 중 계열: 전반/하프타임/후반/연장/승부차기 대기 등 */
	LIVE(false),

	/** 정규시간 종료 (Full Time) */
	FT(true),

	/** 연장 후 종료 (After Extra Time) */
	AET(true),

	/** 승부차기 후 종료 (Penalty) */
	PEN(true),

	/** 연기 (Postponed) */
	PST(false),

	/** 취소 (Cancelled) */
	CANC(false),

	/** 중단/기타 미확정 */
	ABD(false);

	private final boolean finished;

	FixtureStatus(boolean finished) {
		this.finished = finished;
	}

	/** 결과가 확정된 상태인가(FT/AET/PEN). 예측 채점은 이 상태에서만 이뤄진다. */
	public boolean isFinished() {
		return finished;
	}

	/**
	 * API의 {@code fixture.status.short} 코드를 우리 상태로 접는다.
	 *
	 * <p>API는 진행 중 국면을 1H/HT/2H/ET/BT/P/SUSP/INT로 잘게 나누지만, 이 앱은
	 * "예정 / 진행 중 / 확정 / 무산"만 구분하면 된다. 저장하는 값이 적을수록 그 값에
	 * 의존하는 코드도 적어진다.
	 *
	 * <p>모르는 코드는 {@link #ABD}(미확정)로 접는다. 파싱을 실패시키지 않는 쪽을 택한 건,
	 * API가 새 코드를 하나 추가했다고 그날 동기화 전체가 멈추면 안 되기 때문이다.
	 */
	public static FixtureStatus fromApiCode(String code) {
		if (code == null) {
			return ABD;
		}
		return switch (code.toUpperCase()) {
			case "TBD", "NS" -> NS;
			case "1H", "HT", "2H", "ET", "BT", "P", "SUSP", "INT", "LIVE" -> LIVE;
			case "FT" -> FT;
			case "AET" -> AET;
			case "PEN" -> PEN;
			case "PST" -> PST;
			case "CANC" -> CANC;
			// ABD(중단), AWD(몰수승), WO(부전승), 그 외 미지의 코드
			default -> ABD;
		};
	}
}
