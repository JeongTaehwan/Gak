package page.usetaehwan.gak.external.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import page.usetaehwan.gak.config.AnthropicProperties;

/**
 * Anthropic Messages API를 {@link RestClient}로 직접 호출한다.
 *
 * <p>공식 Java SDK(<code>com.anthropic:anthropic-java</code>)가 존재하지만 쓰지 않는다.
 * 이 프로젝트는 API-Football도 {@code RestClient}로 부르고 있고(→
 * {@code external/apifootball/RestApiFootballClient}), 여기서 필요한 건 <b>도구 호출도
 * 스트리밍도 없는 단발 요청 하나</b>다. 의존성을 하나 더 들이는 값이 그 하나를 위해
 * 치를 만하지 않다. 도구 호출이나 스트리밍이 필요해지면 그때 SDK로 가는 게 맞다.
 *
 * <h2>왜 스트리밍을 안 쓰나</h2>
 * <p>{@code max_tokens}가 4096이라 응답이 짧고, 화면은 문장을 한 번에 갈아 끼운다.
 * 토큰이 흘러나오는 걸 보여 줄 자리가 없으므로 스트리밍의 이득이 없다.
 * (긴 출력이 필요해지면 그때는 스트리밍을 켜야 HTTP 타임아웃을 피한다.)
 */
public class RestAnthropicClient implements AnthropicClient {

	private static final Logger log = LoggerFactory.getLogger(RestAnthropicClient.class);

	/** API 버전 헤더. 날짜 문자열이지만 이건 모델 버전이 아니라 <b>wire 포맷</b> 버전이다. */
	private static final String API_VERSION = "2023-06-01";

	private final RestClient restClient;
	private final ObjectMapper objectMapper;
	private final AnthropicProperties properties;

	public RestAnthropicClient(AnthropicProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.restClient = RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory(properties.timeout()))
				.defaultHeader("x-api-key", properties.key())
				.defaultHeader("anthropic-version", API_VERSION)
				.defaultHeader("content-type", MediaType.APPLICATION_JSON_VALUE)
				.build();
	}

	private static ClientHttpRequestFactory requestFactory(Duration timeout) {
		return ClientHttpRequestFactoryBuilderCompat.jdk(timeout);
	}

	@Override
	public boolean available() {
		return true;
	}

	@Override
	public AnthropicResult complete(String system, String user, Map<String, Object> schema) {
		Map<String, Object> body = requestBody(system, user, schema);

		JsonNode response;
		try {
			response = restClient.post()
					.uri("/v1/messages")
					.body(body)
					.retrieve()
					.body(JsonNode.class);
		} catch (ResourceAccessException e) {
			// 소켓 타임아웃도 여기로 온다 — 느린 것과 끊긴 것을 메시지로 갈라 본다.
			boolean timedOut = e.getMessage() != null
					&& e.getMessage().toLowerCase().contains("timed out");
			log.warn("AI 진단 호출 실패({}): {}", timedOut ? "타임아웃" : "연결", e.getMessage());
			return AnthropicResult.failed(
					timedOut ? AnthropicResult.Failure.TIMEOUT : AnthropicResult.Failure.TRANSPORT);
		} catch (Exception e) {
			// 4xx/5xx 포함. 키가 틀렸거나 한도를 넘겼을 수 있다.
			log.warn("AI 진단 호출 실패: {}", e.toString());
			return AnthropicResult.failed(AnthropicResult.Failure.TRANSPORT);
		}

		if (response == null) {
			return AnthropicResult.failed(AnthropicResult.Failure.MALFORMED);
		}
		// 원본을 남긴다. "형식은 맞는데 내용이 없는" 응답은 우리 DTO 만 봐서는 원인을 못 찾는다 —
		// stop_reason·usage.thinking_tokens 까지 봐야 잘린 건지, 안 생각한 건지 갈린다.
		if (log.isDebugEnabled()) {
			log.debug("Anthropic 원본 응답:\n{}", response.toPrettyString());
		}
		return parse(response);
	}

	/**
	 * 응답에서 본문 텍스트를 뽑는다.
	 *
	 * <p><b>{@code content}를 읽기 전에 {@code stop_reason}부터 본다.</b> 거절
	 * ({@code refusal})이면 HTTP는 200인데 {@code content}가 비어 있거나 잘려 있다.
	 * 첫 블록을 무조건 읽는 코드는 이때 깨진다.
	 *
	 * <p>거절을 따로 처리하되 <b>서버 측 fallbacks 파라미터는 쓰지 않는다</b> —
	 * 우리에겐 이미 규칙 기반 문장이라는 폴백이 있고, 그쪽이 더 낫다(공짜고, 즉시고,
	 * 화면이 이미 그릴 줄 안다). 축구 일정 분석이 거절당할 일도 사실상 없다.
	 */
	private AnthropicResult parse(JsonNode response) {
		String stopReason = response.path("stop_reason").asText("");
		if ("refusal".equals(stopReason)) {
			log.warn("AI 진단이 거절됨: category={}",
					response.path("stop_details").path("category").asText("(없음)"));
			return AnthropicResult.failed(AnthropicResult.Failure.REFUSED);
		}
		if ("max_tokens".equals(stopReason)) {
			// 사고 + 응답이 상한을 넘었다. 잘린 JSON을 파싱해 봐야 깨진 문장이 나온다.
			log.warn("AI 진단 응답이 max_tokens({})에서 잘림 — 상한을 올려야 한다",
					properties.maxTokens());
			return AnthropicResult.failed(AnthropicResult.Failure.MALFORMED);
		}

		for (JsonNode block : response.path("content")) {
			if ("text".equals(block.path("type").asText())) {
				String text = block.path("text").asText("");
				if (!text.isBlank()) {
					return AnthropicResult.ok(text);
				}
			}
		}
		log.warn("AI 진단 응답에 본문이 없음: stop_reason={}", stopReason);
		return AnthropicResult.failed(AnthropicResult.Failure.MALFORMED);
	}

	/**
	 * 요청 본문.
	 *
	 * <p>세 가지가 의도적이다.
	 * <ul>
	 *   <li><b>{@code output_config.format}</b> — JSON 스키마를 걸어 응답 모양을 강제한다.
	 *       "JSON으로 답해 줘"라고 부탁하는 것과 다르다. 특히 {@code evidence} 배열을
	 *       필수로 두면 <b>근거 없는 서술이 구조적으로 불가능해진다</b>. 프롬프트로
	 *       부탁하는 것보다 스키마로 막는 게 훨씬 세다.</li>
	 *   <li><b>{@code effort: low}</b> — 이미 계산된 지표를 읽고 문장으로 옮기는 일이라
	 *       깊은 추론이 필요 없다. 비용과 지연을 줄인다.</li>
	 *   <li><b>{@code thinking}을 건드리지 않는다</b> — 이 모델은 사고가 기본으로 켜져
	 *       있고, 끄면 {@code <thinking>} 태그가 본문에 새는 경우가 보고돼 있다.
	 *       effort를 낮추는 쪽이 같은 목적(비용 절감)을 더 안전하게 이룬다.</li>
	 * </ul>
	 */
	private Map<String, Object> requestBody(String system, String user, Map<String, Object> schema) {
		Map<String, Object> outputConfig = new LinkedHashMap<>();
		// effort 를 지원하지 않는 모델이 있다 — Haiku 4.5 는 이 필드가 오면 400 을 낸다.
		// 뉴스 갈래 태거가 그 모델을 쓰므로, "none" 이면 필드를 통째로 뺀다.
		// (진단은 medium 을 그대로 보낸다 — 기본값이 바뀌지 않았다.)
		if (sendsEffort(properties.effort())) {
			outputConfig.put("effort", properties.effort());
		}
		outputConfig.put("format", Map.of("type", "json_schema", "schema", schema));

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.model());
		body.put("max_tokens", properties.maxTokens());
		// 시스템 프롬프트는 매 요청 동일하다 — 캐시 표시를 붙여 두면 반복 호출에서
		// 입력 비용이 크게 준다. (최소 캐시 길이에 못 미치면 조용히 무시될 뿐 해롭지 않다)
		body.put("system", List.of(Map.of(
				"type", "text",
				"text", system,
				"cache_control", Map.of("type", "ephemeral"))));
		body.put("output_config", outputConfig);
		body.put("messages", List.of(Map.of("role", "user", "content", user)));
		return body;
	}

	/**
	 * {@code effort} 를 보내야 하는가.
	 *
	 * <p>이 파라미터는 모델마다 지원 여부가 다르다. Opus 계열은 받지만 Haiku 4.5 는 받지
	 * 않고 400 을 낸다. 설정에 {@code none} 을 적으면 보내지 않는다는 뜻이다 —
	 * "기본값으로 medium 을 쓴다"와 "이 모델엔 해당 없다"를 구분해야 해서 값 하나를 뒀다.
	 */
	static boolean sendsEffort(String effort) {
		return effort != null && !effort.isBlank()
				&& !"none".equalsIgnoreCase(effort.trim());
	}

	/** 요청 팩터리 생성만 분리 — Spring Boot 버전에 따라 설정 API가 달라서 한 곳에 가둔다. */
	private static final class ClientHttpRequestFactoryBuilderCompat {
		static ClientHttpRequestFactory jdk(Duration timeout) {
			JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
			factory.setReadTimeout(timeout);
			return factory;
		}

		private ClientHttpRequestFactoryBuilderCompat() {
		}
	}
}
