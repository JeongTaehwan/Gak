package page.usetaehwan.gak.domain;

import java.util.Locale;

/**
 * 결장이 확정인가 불확실한가. API의 {@code player.type} 값을 접는다.
 *
 * <p>이 둘을 합치면 안 된다. 맨유 2023 시즌 346건 중 56건이 {@code "Questionable"}인데,
 * 이걸 결장으로 세면 "이 경기에 9명이 빠졌다"가 실제보다 부풀려진다. 반대로 버리면
 * "출전이 불투명한 선수가 몇이었나"라는 사실을 잃는다. 그래서 저장은 둘 다 하고,
 * <b>세는 건 {@link #OUT}만</b> 센다.
 */
public enum AbsenceStatus {

	/** 결장 확정 ({@code "Missing Fixture"}). */
	OUT,

	/** 출전 불투명 ({@code "Questionable"}). 결장자 수에는 넣지 않는다. */
	DOUBTFUL;

	/**
	 * 모르는 값은 {@link #DOUBTFUL}로 접는다. 확정으로 밀어 넣으면 결장자 수가 조용히
	 * 부풀고, 그 숫자가 진단의 근거로 쓰인다.
	 */
	public static AbsenceStatus from(String type) {
		if (type == null) {
			return DOUBTFUL;
		}
		return type.toLowerCase(Locale.ROOT).contains("missing") ? OUT : DOUBTFUL;
	}
}
