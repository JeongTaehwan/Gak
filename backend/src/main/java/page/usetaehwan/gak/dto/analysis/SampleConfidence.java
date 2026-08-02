package page.usetaehwan.gak.dto.analysis;

/**
 * 표본 크기가 이 지표를 얼마나 믿을 만하게 만드는가.
 *
 * <p>같은 "승점률 100%"라도 2경기와 20경기는 전혀 다른 말이다. 숫자만 내려보내면 화면도
 * 사용자도 그 차이를 알 수 없으므로, <b>표본 크기를 숫자와 함께 등급으로 실어 보낸다.</b>
 * 화면은 이 값을 보고 "표본 2경기" 같은 단서를 붙이거나 아예 비율을 감출 수 있다.
 *
 * <p>{@link #NONE}/{@link #LOW}에서는 비율(승점률 등)을 계산하지 <b>않는다</b>(null).
 * 3경기 2승 1패를 "승점률 66.7%"로 적으면, 소수점 한 자리가 주는 정밀함이 표본의
 * 빈약함을 가려 버린다. 이럴 땐 "3경기 2승 1패"라는 원래의 사실이 더 정직하다.
 */
public enum SampleConfidence {

	/** 표본 0 — 계산할 것이 없다. */
	NONE,

	/** 1~4경기 — 한 경기의 무게가 25% 이상이다. 비율로 말하지 않는다. */
	LOW,

	/** 5~9경기 — 경향을 조심스럽게 말할 수 있다. */
	MODERATE,

	/** 10경기 이상 — 통상적인 "최근 폼" 판단에 충분하다. */
	SUFFICIENT;

	/** 비율을 공개해도 되는 최소 표본. */
	public static final int MIN_SAMPLE_FOR_RATE = 5;

	public static SampleConfidence of(int sampleSize) {
		if (sampleSize <= 0) {
			return NONE;
		}
		if (sampleSize < MIN_SAMPLE_FOR_RATE) {
			return LOW;
		}
		return sampleSize < 10 ? MODERATE : SUFFICIENT;
	}

	/** 이 등급에서 비율(승점률 등)을 계산해 내려도 되는가. */
	public boolean allowsRates() {
		return this == MODERATE || this == SUFFICIENT;
	}
}
