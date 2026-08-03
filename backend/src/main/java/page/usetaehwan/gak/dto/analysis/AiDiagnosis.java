package page.usetaehwan.gak.dto.analysis;

import java.util.List;

/**
 * AI가 쓴 진단 서술. <b>이 앱에서 유일하게 결정론적이지 않은 응답이다.</b>
 *
 * <h2>왜 진단 응답에 섞지 않고 따로 있나</h2>
 * <p>{@code /diagnostics}는 우리 DB만 읽으므로 수십 ms에 끝난다. AI 호출은 수 초다.
 * 한 응답으로 합치면 <b>타임라인 전체가 AI를 기다린다</b> — 밀집도 브래킷을 보려고 온
 * 사용자가 모델의 사고가 끝날 때까지 빈 화면을 본다. 갈라 두면 화면은 규칙 기반 문장으로
 * 즉시 완성되고, AI 문장은 도착하는 대로 그 자리를 갈아 끼운다.
 *
 * <h2>규칙 기반 문장은 여기 없다</h2>
 * <p>폴백은 프론트가 이미 갖고 있다({@code lib/diagnosis/summarize.ts}). 같은 문장을
 * 백엔드에도 두면 둘이 갈라지는 날이 오고, 그날부터 "AI가 실패했을 때와 성공했을 때
 * 화면이 말하는 사실"이 달라진다. 그래서 이 응답은 <b>AI 층만</b> 담는다.
 *
 * @param available         AI 서술을 만들었는가. false면 나머지 필드는 비어 있다
 * @param unavailableReason 못 만든 이유. <b>사용자에게 보여줄 수 있는 한국어</b>여야 한다
 * @param headline          한 줄 결론
 * @param sub               뒷받침 문장
 * @param evidence          결론의 근거가 된 지표. 스키마가 필수로 강제한다
 * @param unknowns          이 결론을 더 확실히 하려면 필요하지만 우리에게 없는 정보
 */
public record AiDiagnosis(
		boolean available,
		String unavailableReason,
		String headline,
		String sub,
		List<Evidence> evidence,
		List<String> unknowns
) {

	/**
	 * 주장 하나와 그 근거 수치.
	 *
	 * <p>이 레코드가 존재하는 것 자체가 장치다. 응답 스키마에서 {@code evidence}를
	 * 필수 배열로 두면 <b>근거 없는 서술이 구조적으로 불가능해진다</b> — 모델이
	 * "일정이 빡빡해 보인다"만 쓰고 끝낼 수가 없다.
	 *
	 * @param claim  주장
	 * @param metric 근거가 된 지표 이름 ("밀집 구간 내 최단 간격")
	 * @param value  그 값 ("2일")
	 */
	public record Evidence(String claim, String metric, String value) {
	}

	public static AiDiagnosis unavailable(String reason) {
		return new AiDiagnosis(false, reason, null, null, List.of(), List.of());
	}

	public static AiDiagnosis of(
			String headline, String sub, List<Evidence> evidence, List<String> unknowns) {
		return new AiDiagnosis(true, null, headline, sub, evidence, unknowns);
	}
}
