package page.usetaehwan.gak.domain;

/**
 * 소식의 갈래. <b>다섯 개뿐이고, 여기서 늘지 않는다.</b>
 *
 * <p>이 닫힌 집합이 LLM을 "분류기"로 묶어 두는 장치다. 태거가 아무리 이상하게 굴어도
 * 뱉을 수 있는 값이 다섯 개라, 최악의 실패가 <b>배지 하나가 잘못 붙는 것</b>으로 끝난다.
 * 자유 문장을 받는 순간(=판단자) 그 상한이 사라진다.
 *
 * <p>AI 진단({@code AiDiagnosisService})과 대조해 보면 차이가 분명하다. 저쪽은 자유 문장을
 * 받으므로 재료 통제·코드 게이트·응답 검증 세 겹과 배지가 필요하다. 여기는 그게 필요 없다 —
 * 출력 공간이 애초에 닫혀 있기 때문이다.
 *
 * <p>갈래는 <b>축구 팬이 실제로 구분하는 단위</b>로 잡았다. 기사 성격의 분류학이 아니라
 * "지금 이적 소식만 보고 싶다"는 화면 요구에서 나온 것이다.
 */
public enum NewsCategory {

	/** 이적·영입·방출·계약. 성사된 것과 소문 난 것을 구분하지 않는다 — 우리는 판정하지 않는다. */
	TRANSFER,

	/** 선수단 — 훈련·복귀·부상 소식·라인업 예상·선수 발언. */
	SQUAD,

	/** 경기 — 프리뷰·리포트·하이라이트. */
	MATCH,

	/** 구단 운영 — 감독 선임/경질, 구단주, 경기장, 재정, 티켓, 팬. */
	CLUB,

	/**
	 * 위 어디에도 안 들어가는 것.
	 *
	 * <p><b>이게 있어야 태거가 억지로 끼워 맞추지 않는다.</b> {@code AbsenceReason.OTHER}와
	 * 같은 역할이다 — 모르는 걸 아는 갈래로 밀어 넣느니 "기타"로 남기는 편이 낫다.
	 */
	OTHER;

	/** 태거가 준 문자열을 갈래로 바꾼다. 모르는 값이면 null(= 태그 없음, 오류 아님). */
	public static NewsCategory parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
