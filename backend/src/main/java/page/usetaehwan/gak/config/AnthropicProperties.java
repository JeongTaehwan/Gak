package page.usetaehwan.gak.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 진단(Anthropic Messages API) 설정.
 *
 * <p><b>키가 비어 있으면 AI 진단을 끈다.</b> 키가 없다고 앱이 안 뜨거나 진단 탭이 깨지면
 * 안 된다 — 규칙 기반 결론이 이미 있고, AI는 그 위에 얹는 것이지 대체하는 게 아니다.
 *
 * @param baseUrl   API 베이스 URL
 * @param key       API 키. <b>비어 있으면 AI 진단 비활성</b>
 * @param model     모델 id
 * @param effort    추론 강도(low/medium/high/xhigh/max). 진단 문장은 짧은 서술이라 낮게 잡는다
 * @param maxTokens 응답 상한. <b>사고(thinking) + 응답 텍스트를 합친 한도</b>라 넉넉해야 한다
 * @param timeout   응답 대기 상한. 넘기면 규칙 기반으로 되돌아간다
 */
@ConfigurationProperties(prefix = "gak.anthropic")
public record AnthropicProperties(
		String baseUrl,
		String key,
		String model,
		String effort,
		Integer maxTokens,
		Duration timeout
) {

	public AnthropicProperties {
		baseUrl = orDefault(baseUrl, "https://api.anthropic.com");
		model = orDefault(model, "claude-opus-5");
		effort = orDefault(effort, "low");
		// 이 모델은 사고가 기본으로 켜져 있고, maxTokens 는 사고와 응답을 함께 덮는다.
		// 짧은 진단 문장이라도 사고에 쓸 자리를 남기지 않으면 답이 중간에 잘린다.
		maxTokens = (maxTokens == null || maxTokens < 1024) ? 4096 : maxTokens;
		timeout = timeout == null ? Duration.ofSeconds(30) : timeout;
	}

	/** AI 진단을 쓸 수 있는 상태인가. */
	public boolean enabled() {
		return key != null && !key.isBlank();
	}

	private static String orDefault(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
