package page.usetaehwan.gak.domain;

import java.util.Locale;

/**
 * 결장 사유의 갈래.
 *
 * <p><b>API의 엔드포인트 이름은 {@code /injuries}지만 담긴 내용은 부상만이 아니다.</b>
 * 실제 응답(맨유 2023 시즌 346건)에는 {@code "Suspended"} 18건, {@code "Red Card"} 4건,
 * {@code "Illness"} 9건, {@code "Coach's decision"} 4건이 섞여 있다. 이걸 전부 "부상"으로
 * 부르면 화면이 "부상 5명"이라고 말하는데 그중 둘은 징계인 상태가 된다. 그래서 도메인
 * 이름은 {@code Injury}가 아니라 {@link Absence}(결장)이고, 갈래를 여기서 나눈다.
 *
 * <p>사유 원문({@code Absence.reason})은 그대로 보관한다. 이 분류는 <b>표시·집계를 위한
 * 요약</b>이지 원본을 대체하는 게 아니다 — API가 새 문구를 보내면 {@link #OTHER}로 떨어지고
 * 원문은 남으므로, 나중에 그 문구를 보고 규칙을 늘리면 된다.
 */
public enum AbsenceReason {

	/** 부상·수술·컨디션(Knee Injury, Muscle Injury, Surgery, Knock …). */
	INJURY,

	/** 징계(Suspended, Red Card, Yellow Cards). */
	SUSPENSION,

	/** 질병(Illness, Health problems). */
	ILLNESS,

	/** 대표팀 차출(National selection, International duty). */
	NATIONAL_DUTY,

	/** 그 밖(Inactive, Coach's decision, 그리고 아직 모르는 문구). */
	OTHER;

	/**
	 * API의 사유 문자열을 갈래로 접는다.
	 *
	 * <p>규칙을 문자열 매칭으로 두는 게 마음에 걸리지만, API가 코드값을 주지 않고 자유
	 * 문구만 준다. 대신 <b>모르는 문구를 부상으로 밀어 넣지 않는다</b> — 확실한 것만
	 * 분류하고 나머지는 {@link #OTHER}다. 부상자 수는 진단의 근거로 쓰이므로, 틀리게
	 * 부풀리느니 "기타"로 남기는 편이 낫다.
	 */
	public static AbsenceReason from(String reason) {
		if (reason == null || reason.isBlank()) {
			return OTHER;
		}
		String r = reason.toLowerCase(Locale.ROOT).trim();

		if (r.contains("suspend") || r.contains("red card") || r.contains("yellow card")) {
			return SUSPENSION;
		}
		if (r.contains("illness") || r.contains("health")) {
			return ILLNESS;
		}
		if (r.contains("national") || r.contains("international duty")) {
			return NATIONAL_DUTY;
		}
		// "Knee Injury"·"Injury" 같은 부상 계열 + 부상과 같은 결로 다루는 것들.
		if (r.contains("injury") || r.contains("surgery") || r.contains("knock")
				|| r.contains("match fitness")) {
			return INJURY;
		}
		return OTHER;
	}

	/** 선수단 가용성 문제인가(부상·질병). 징계·차출·로테이션과 구분한다. */
	public boolean isFitnessRelated() {
		return this == INJURY || this == ILLNESS;
	}
}
