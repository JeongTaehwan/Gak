package page.usetaehwan.gak.external.anthropic;

/**
 * Anthropic 호출 결과.
 *
 * <p>실패를 예외가 아니라 값으로 돌려준다. 이 앱에서 <b>AI 실패는 예외 상황이 아니다</b> —
 * 키가 없을 수도, 느릴 수도, 거절당할 수도 있고 그 전부가 예상된 경우다. 그럴 때 화면은
 * 규칙 기반 문장으로 조용히 돌아가면 된다. try/catch로 흐름을 만들면 "정상 경로"와
 * "폴백 경로"의 구분이 흐려진다.
 *
 * @param json    성공했을 때 모델이 준 JSON 문자열. 실패면 null
 * @param failure 실패했을 때 사유. 성공이면 null
 */
public record AnthropicResult(String json, Failure failure) {

	/**
	 * 실패 사유.
	 *
	 * <p>사유를 갈래로 나누는 이유는 <b>대응이 다르기 때문</b>이다. 키가 없는 건 설정
	 * 문제라 로그를 시끄럽게 낼 필요가 없고, 타임아웃은 다음 요청에 다시 될 수 있으며,
	 * 거절은 프롬프트를 봐야 한다.
	 */
	public enum Failure {
		/** 키가 없어 아예 부르지 않았다. 정상 상태다 */
		DISABLED,
		/** 응답이 제한 시간을 넘겼다 */
		TIMEOUT,
		/** 네트워크·HTTP 오류 */
		TRANSPORT,
		/** 모델이 안전상 응답을 거절했다({@code stop_reason: "refusal"}) */
		REFUSED,
		/** 응답이 왔지만 우리가 기대한 모양이 아니었다 */
		MALFORMED
	}

	public static AnthropicResult ok(String json) {
		return new AnthropicResult(json, null);
	}

	public static AnthropicResult failed(Failure failure) {
		return new AnthropicResult(null, failure);
	}

	public boolean succeeded() {
		return json != null;
	}
}
