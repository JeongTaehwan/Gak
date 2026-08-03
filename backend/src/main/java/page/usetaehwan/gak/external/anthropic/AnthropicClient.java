package page.usetaehwan.gak.external.anthropic;

import java.util.Map;

/**
 * Anthropic Messages API 호출 통로.
 *
 * <p>인터페이스로 갈라 둔 이유는 <b>키가 없는 환경</b>이 정상 상태이기 때문이다. 테스트,
 * CI, 키를 아직 안 넣은 로컬에서 AI 진단은 그냥 꺼져 있어야 하고, 그 판단이 서비스 로직
 * 곳곳에 if 문으로 흩어지면 안 된다. 구현체를 갈아 끼우는 것으로 끝낸다.
 *
 * @see RestAnthropicClient  실제 호출
 * @see DisabledAnthropicClient  키가 없을 때 — 항상 "못 부른다"고 답한다
 */
public interface AnthropicClient {

	/**
	 * JSON 스키마에 맞춘 응답 하나를 받아 온다.
	 *
	 * @param system  시스템 프롬프트(요청마다 동일 — 프롬프트 캐시가 붙는다)
	 * @param user    사용자 프롬프트(계산된 지표)
	 * @param schema  응답이 따라야 할 JSON 스키마
	 * @return 성공/실패와 사유를 함께 담은 결과. <b>실패를 예외로 던지지 않는다</b> —
	 *         AI 실패는 이 앱에서 예외 상황이 아니라 예상된 분기이고, 호출부는 그때
	 *         규칙 기반으로 되돌아가면 그만이다
	 */
	AnthropicResult complete(String system, String user, Map<String, Object> schema);

	/** 지금 이 클라이언트로 AI를 부를 수 있는가. */
	boolean available();
}
