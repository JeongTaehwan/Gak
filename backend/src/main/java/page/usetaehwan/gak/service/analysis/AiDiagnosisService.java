package page.usetaehwan.gak.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.dto.analysis.AiDiagnosis;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.AnthropicResult;

/**
 * 계산된 지표를 AI에게 넘겨 진단 문장을 받아 온다.
 *
 * <h2>세 겹의 방어</h2>
 * <p>"지어내지 마라"고 프롬프트에 쓰는 건 <b>가장 약한 층</b>이다. 이 서비스는 그 위에
 * 두 겹을 더 얹는다.
 * <ol>
 *   <li><b>재료 통제</b> — 프롬프트에 계산된 지표만 넣는다. 선수 기여도·전술·라커룸은
 *       애초에 프롬프트에 없으므로 모델이 참조할 것이 없다. (→ {@link DiagnosisPromptFactory})</li>
 *   <li><b>스키마 강제</b> — 응답에 {@code evidence} 배열을 필수로 둔다. 근거 없는
 *       한 줄짜리 서술이 <b>구조적으로 반환 불가능</b>해진다.</li>
 *   <li><b>코드 게이트</b> — 표본이 부족하면 <b>아예 호출하지 않는다</b>. "표본이 적으면
 *       결론을 내지 마라"고 부탁하는 대신, 결론 낼 기회를 주지 않는다.</li>
 * </ol>
 *
 * <h2>남는 한계</h2>
 * <p>이걸 다 해도 막지 못하는 게 있다. 모델은 <b>주어진 숫자를 옳게 인용하면서 그
 * 인과를 틀리게 말할 수</b> 있다 — "2일 간격 3연전 뒤 2패"는 사실이지만 "그래서 졌다"는
 * 주장이고, 우리 데이터는 그 인과를 증명하지 못한다. 프롬프트에서 상관과 인과를 나누라고
 * 지시하지만 그건 부탁이지 보장이 아니다. 그래서 배지가 필요하다 — 사용자가 "이 문장은
 * 모델이 썼다"는 걸 알아야 그만큼만 믿는다.
 */
@Service
public class AiDiagnosisService {

	private static final Logger log = LoggerFactory.getLogger(AiDiagnosisService.class);

	private final AnthropicClient client;
	private final ObjectMapper objectMapper;

	public AiDiagnosisService(AnthropicClient client, ObjectMapper objectMapper) {
		this.client = client;
		this.objectMapper = objectMapper;
	}

	/**
	 * 이 진단 결과에 대한 AI 서술을 만든다.
	 *
	 * <p><b>예외를 던지지 않는다.</b> 어떤 실패든 {@link AiDiagnosis#unavailable(String)}로
	 * 돌아오고, 화면은 규칙 기반 문장을 그대로 유지한다.
	 */
	public AiDiagnosis narrate(TeamDiagnostics diagnostics) {
		if (!client.available()) {
			return AiDiagnosis.unavailable("AI 진단이 설정되지 않았습니다");
		}

		String gate = insufficientSample(diagnostics);
		if (gate != null) {
			// 호출 자체를 안 한다. 표본이 부족할 때 모델이 결론을 내지 않게 하는 가장
			// 확실한 방법은 물어보지 않는 것이다.
			return AiDiagnosis.unavailable(gate);
		}

		AnthropicResult result = client.complete(
				DiagnosisPromptFactory.SYSTEM,
				DiagnosisPromptFactory.userPrompt(diagnostics),
				RESPONSE_SCHEMA);

		if (!result.succeeded()) {
			return AiDiagnosis.unavailable(reasonFor(result.failure()));
		}
		return parse(result.json());
	}

	/**
	 * 표본 게이트 — 규칙 기반과 <b>같은 기준</b>을 쓴다.
	 *
	 * <p>기준이 갈리면 "규칙 기반은 결론을 안 내는데 AI는 낸다"는 상태가 생기고, 그건
	 * 사용자에게 "AI가 더 많이 안다"로 읽힌다. 사실은 같은 데이터를 보고 있는데 한쪽이
	 * 더 무모한 것뿐이다.
	 *
	 * @return 부족하면 사용자에게 보여줄 사유, 충분하면 null
	 */
	private String insufficientSample(TeamDiagnostics d) {
		var form = d.form();
		if (form.sampleSize() == 0) {
			return "결과가 확정된 경기가 없어 진단할 수 없습니다";
		}
		if (!form.confidence().allowsRates()) {
			return "확정된 경기가 %d건뿐이라 결론을 내지 않습니다 (%d건 이상 필요)"
					.formatted(form.sampleSize(), page.usetaehwan.gak.dto.analysis.SampleConfidence.MIN_SAMPLE_FOR_RATE);
		}
		if (!d.congestion().detectable() && d.travel().measuredMatches() == 0) {
			// 폼만 있고 부하 지표가 하나도 없으면 "왜"를 말할 재료가 없다.
			return "일정 부하를 판정할 데이터가 부족합니다";
		}
		return null;
	}

	/** 실패 사유를 사용자에게 보여줄 한국어로. 기술 용어를 화면에 올리지 않는다. */
	private String reasonFor(AnthropicResult.Failure failure) {
		return switch (failure) {
			case DISABLED -> "AI 진단이 설정되지 않았습니다";
			case TIMEOUT -> "AI 응답이 늦어 규칙 기반 결론을 유지합니다";
			case TRANSPORT -> "AI 진단을 불러오지 못했습니다";
			case REFUSED -> "AI가 이 요청에 답하지 않았습니다";
			case MALFORMED -> "AI 응답을 읽지 못했습니다";
		};
	}

	/**
	 * 응답 JSON을 DTO로.
	 *
	 * <p>스키마를 걸었어도 파싱은 방어적으로 한다. 스키마 강제는 서버가 지켜 주지만,
	 * 우리가 스키마를 잘못 쓴 경우까지 막아 주진 않는다.
	 */
	private AiDiagnosis parse(String json) {
		try {
			JsonNode root = objectMapper.readTree(json);
			String headline = root.path("headline").asText("");
			String sub = root.path("sub").asText("");

			List<AiDiagnosis.Evidence> evidence = new ArrayList<>();
			for (JsonNode e : root.path("evidence")) {
				evidence.add(new AiDiagnosis.Evidence(
						e.path("claim").asText(""),
						e.path("metric").asText(""),
						e.path("value").asText("")));
			}

			List<String> unknowns = new ArrayList<>();
			for (JsonNode u : root.path("unknowns")) {
				unknowns.add(u.asText());
			}

			if (headline.isBlank() || evidence.isEmpty()) {
				// 근거 없는 결론은 받지 않는다 — 스키마가 막았어야 할 것이 새어 나온 경우다.
				log.warn("AI 진단에 결론 또는 근거가 없어 폐기: headline={}, evidence={}",
						headline.isBlank() ? "(없음)" : "있음", evidence.size());
				return AiDiagnosis.unavailable("AI 응답에 근거가 없어 사용하지 않습니다");
			}
			return AiDiagnosis.of(headline, sub, evidence, unknowns);
		} catch (Exception e) {
			log.warn("AI 진단 응답 파싱 실패: {}", e.toString());
			return AiDiagnosis.unavailable("AI 응답을 읽지 못했습니다");
		}
	}

	/**
	 * 응답 스키마.
	 *
	 * <p>{@code evidence}가 {@code required}에 들어 있는 게 핵심이다 — 이 한 줄이
	 * "결론에 근거 수치가 반드시 붙는다"를 <b>부탁이 아니라 계약으로</b> 만든다.
	 * {@code additionalProperties: false}는 모델이 우리가 모르는 필드를 만들어 붙이는 걸
	 * 막는다.
	 */
	private static final Map<String, Object> RESPONSE_SCHEMA = Map.of(
			"type", "object",
			"properties", Map.of(
					"headline", Map.of(
							"type", "string",
							"description", "한 줄 결론. 30자 안팎"),
					"sub", Map.of(
							"type", "string",
							"description", "결론을 뒷받침하는 2~3문장"),
					"evidence", Map.of(
							"type", "array",
							"description", "결론의 근거가 된 지표. 최소 1개",
							"items", Map.of(
									"type", "object",
									"properties", Map.of(
											"claim", Map.of("type", "string", "description", "주장"),
											"metric", Map.of("type", "string", "description", "근거가 된 지표 이름"),
											"value", Map.of("type", "string", "description", "그 값. 받은 숫자를 그대로")),
									"required", List.of("claim", "metric", "value"),
									"additionalProperties", false)),
					"unknowns", Map.of(
							"type", "array",
							"description", "이 결론을 더 확실히 하려면 필요하지만 갖고 있지 않은 정보",
							"items", Map.of("type", "string"))),
			"required", List.of("headline", "sub", "evidence", "unknowns"),
			"additionalProperties", false);
}
