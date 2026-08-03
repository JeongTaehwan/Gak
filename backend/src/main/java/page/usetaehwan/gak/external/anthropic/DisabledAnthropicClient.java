package page.usetaehwan.gak.external.anthropic;

import java.util.Map;

/**
 * 키가 없을 때 쓰는 클라이언트 — 부르지 않고 즉시 "못 부른다"고 답한다.
 *
 * <p>키 없음은 <b>정상 상태</b>다. 테스트, CI, 아직 키를 안 넣은 로컬이 전부 여기 해당하고,
 * 그때 진단 탭은 규칙 기반 문장으로 멀쩡히 동작해야 한다. 그래서 예외를 던지지 않는다.
 */
public class DisabledAnthropicClient implements AnthropicClient {

	@Override
	public boolean available() {
		return false;
	}

	@Override
	public AnthropicResult complete(String system, String user, Map<String, Object> schema) {
		return AnthropicResult.failed(AnthropicResult.Failure.DISABLED);
	}
}
