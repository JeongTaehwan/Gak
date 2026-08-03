package page.usetaehwan.gak.service.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

	/**
	 * 결론 카드에 실을 근거의 최대 개수.
	 *
	 * <p>근거는 <b>많은 것보다 핵심적인 게 낫다.</b> 9건을 늘어놓으면 결론 카드가 길어져
	 * 읽히지 않고, 무엇이 결정적이었는지도 흐려진다 — 전부 중요하다고 말하는 건 아무것도
	 * 중요하지 않다고 말하는 것과 같다. 우리가 계산한 지표 전체는 바로 아래 근거 카드에
	 * 이미 다 있으므로, 여기서는 <b>결론이 무너지지 않으려면 반드시 있어야 할 것</b>만 남긴다.
	 */
	private static final int MAX_EVIDENCE = 5;

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
	 * 응답 JSON을 DTO로 옮기고 <b>내용이 있는지</b> 검증한다.
	 *
	 * <h2>왜 스키마만으로 부족한가</h2>
	 * <p>구조화 출력의 {@code required}는 <b>"키가 있어야 한다"</b>는 뜻이지
	 * <b>"값이 비어 있으면 안 된다"</b>가 아니다. {@code ""} 도, {@code []} 도 required 를
	 * 만족한다. 그리고 {@code minLength}·{@code minItems} 같은 제약은 구조화 출력이
	 * 지원하지 않으므로 <b>스키마로는 "비어 있지 않음"을 표현할 수 없다.</b>
	 *
	 * <p>실제로 모델이 이렇게 답한 적이 있다 — 형식은 완벽하고 내용만 없다.
	 * <pre>
	 * {"headline":"placeholder","sub":"placeholder",
	 *  "evidence":[{"claim":"밀집이 집중됐다","metric":"","value":""}],"unknowns":[]}
	 * </pre>
	 *
	 * <p>이걸 성공으로 치면 <b>화면 배지가 "AI 분석"으로 바뀐 채 빈 껍데기가 뜬다.</b>
	 * 실패했으면 규칙 기반 문장이 남았을 텐데, 성공한 척하는 쪽이 더 나쁘다 —
	 * 사용자는 우리가 분석했다고 믿고 그 빈 문장을 읽는다.
	 */
	private AiDiagnosis parse(String json) {
		JsonNode root;
		try {
			root = objectMapper.readTree(json);
		} catch (Exception e) {
			log.warn("AI 진단 응답 파싱 실패: {}", e.toString());
			return AiDiagnosis.unavailable("AI 응답을 읽지 못했습니다");
		}

		String headline = root.path("headline").asText("").trim();
		String sub = root.path("sub").asText("").trim();

		if (isFiller(headline) || isFiller(sub)) {
			log.warn("AI 진단이 빈 껍데기라 폐기: headline={}, sub={}", quote(headline), quote(sub));
			return AiDiagnosis.unavailable("AI가 내용 없는 응답을 보내 사용하지 않습니다");
		}
		// 결론과 부연이 글자까지 같으면 모델이 같은 문자열로 두 칸을 때운 것이다.
		if (headline.equals(sub)) {
			log.warn("AI 진단의 결론과 부연이 동일해 폐기: {}", quote(headline));
			return AiDiagnosis.unavailable("AI가 내용 없는 응답을 보내 사용하지 않습니다");
		}

		// 근거는 세 칸이 모두 차 있어야 근거다. 한 칸이라도 비면 그 항목을 버린다 —
		// "지표 이름 없는 값"이나 "값 없는 지표"는 검증할 수 없는 문장일 뿐이다.
		List<AiDiagnosis.Evidence> evidence = new ArrayList<>();
		int dropped = 0;
		for (JsonNode e : root.path("evidence")) {
			String claim = e.path("claim").asText("").trim();
			String metric = e.path("metric").asText("").trim();
			String value = e.path("value").asText("").trim();
			if (isFiller(claim) || isFiller(metric) || isFiller(value)) {
				dropped++;
				continue;
			}
			evidence.add(new AiDiagnosis.Evidence(claim, metric, value));
		}
		if (dropped > 0) {
			log.warn("AI 진단 근거 {}건이 비어 있어 버림 (남은 {}건)", dropped, evidence.size());
		}
		if (evidence.isEmpty()) {
			log.warn("AI 진단에 쓸 수 있는 근거가 없어 폐기");
			return AiDiagnosis.unavailable("AI 결론에 근거 수치가 없어 사용하지 않습니다");
		}
		if (evidence.size() > MAX_EVIDENCE) {
			// 프롬프트로 3~5개를 요청하지만 모델은 지시를 흘릴 수 있다. 화면 계약은 코드가 지킨다.
			// 앞에서 자르는 게 맞다 — 모델은 중요한 것부터 적고, 스키마 순서상 evidence 가
			// 맨 앞이라 결론을 쓰기 전에 고른 것들이다.
			log.info("AI 진단 근거 {}건 중 상위 {}건만 사용", evidence.size(), MAX_EVIDENCE);
			evidence = evidence.subList(0, MAX_EVIDENCE);
		}

		List<String> unknowns = new ArrayList<>();
		for (JsonNode u : root.path("unknowns")) {
			String text = u.asText("").trim();
			if (!isFiller(text)) {
				unknowns.add(text);
			}
		}
		return AiDiagnosis.of(headline, sub, evidence, unknowns);
	}

	/**
	 * 값이 비었거나 <b>자리만 채운 문자열</b>인가.
	 *
	 * <p>빈 문자열만 막으면 부족하다 — 모델은 required 를 만족시키려고 {@code "placeholder"},
	 * {@code "N/A"}, {@code "-"} 같은 걸 넣는다. 형식 검사는 통과하고 사람만 속는 값들이다.
	 */
	private static boolean isFiller(String value) {
		if (value == null || value.isBlank()) {
			return true;
		}
		String v = value.trim().toLowerCase();
		return FILLER_VALUES.contains(v) || v.chars().allMatch(c -> c == '-' || c == '.' || c == '?');
	}

	/** 모델이 빈칸을 때울 때 쓰는 값들. 소문자로 비교한다. */
	private static final Set<String> FILLER_VALUES = Set.of(
			"placeholder", "n/a", "na", "tbd", "todo", "none", "null", "unknown",
			"string", "example", "example value", "value", "metric", "claim",
			"없음", "미정", "해당없음", "알수없음", "알 수 없음", "미상");

	/** 로그용 — 너무 길면 자른다. */
	private static String quote(String s) {
		String t = s.length() > 40 ? s.substring(0, 40) + "…" : s;
		return "\"" + t + "\"";
	}

	/**
	 * 응답 스키마.
	 *
	 * <h2>순서가 의미를 갖는다 — 그래서 {@link LinkedHashMap} 이다</h2>
	 * <p>{@code Map.of()} 는 <b>순서를 보장하지 않는다</b>(JVM 마다 무작위로 섞인다).
	 * 구조화 출력에서 모델은 스키마에 적힌 순서대로 필드를 생성하므로, 순서가 섞이면
	 * <b>결론을 먼저 쓰고 근거를 나중에 찾는</b> 실행 순서가 나온다. 실제로 그렇게 돌았고,
	 * 그때 나온 게 근거 없는 결론이었다.
	 *
	 * <p>그래서 <b>{@code evidence} 를 맨 앞에 둔다.</b> 모델이 근거가 될 수치를 먼저
	 * 적어 놓고 그걸 보며 결론을 쓰게 하는 것 — 사람이 자료를 펼쳐 놓고 요약문을 쓰는
	 * 순서와 같다. 반대로 하면 결론에 맞는 근거를 나중에 끼워 맞추게 된다.
	 *
	 * <h2>스키마로 막을 수 없는 것</h2>
	 * <p>{@code required} 는 <b>"키가 있어야 한다"</b>는 뜻일 뿐이라 {@code ""} 와
	 * {@code []} 를 막지 못하고, {@code minLength}·{@code minItems} 는 구조화 출력이
	 * 지원하지 않는다. <b>"비어 있지 않음"은 스키마로 표현할 수 없다</b> — 그래서
	 * {@link #parse(String)} 가 코드로 검증한다.
	 */
	private static final Map<String, Object> RESPONSE_SCHEMA = schema();

	private static Map<String, Object> schema() {
		// 근거 → 결론 → 부연 → 모르는 것. 모델은 이 순서로 생성한다.
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("evidence", field("array",
				"결론의 근거가 될 지표. **가장 먼저 채운다.** "
						+ "결론을 떠받치는 **핵심 3~5개만** 고른다 — 받은 지표를 전부 옮겨 적는 게 아니라, "
						+ "이 결론이 무너지지 않으려면 반드시 있어야 할 것만 남긴다. "
						+ "받은 지표에서 그대로 인용하며, 세 칸을 모두 채운다",
				Map.entry("items", evidenceItem())));
		properties.put("headline", field("string",
				"위 evidence 를 근거로 한 줄 결론. 30자 안팎"));
		properties.put("sub", field("string",
				"결론을 뒷받침하는 2~3문장. evidence 의 수치를 문장 안에 녹인다"));
		properties.put("unknowns", field("array",
				"이 결론을 더 확실히 하려면 필요하지만 갖고 있지 않은 정보",
				Map.entry("items", Map.of("type", "string"))));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		root.put("properties", properties);
		root.put("required", List.of("evidence", "headline", "sub", "unknowns"));
		root.put("additionalProperties", false);
		return root;
	}

	private static Map<String, Object> evidenceItem() {
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("metric", field("string", "근거가 된 지표 이름. 예: \"구간 내 최단 간격\""));
		props.put("value", field("string", "그 값. 받은 숫자를 그대로. 예: \"3일\""));
		props.put("claim", field("string", "이 수치가 무엇을 말하는지 한 문장"));

		Map<String, Object> item = new LinkedHashMap<>();
		item.put("type", "object");
		item.put("properties", props);
		// metric·value 를 claim 보다 앞에 둔다 — 수치를 먼저 적고 나서 해석하게.
		item.put("required", List.of("metric", "value", "claim"));
		item.put("additionalProperties", false);
		return item;
	}

	@SafeVarargs
	private static Map<String, Object> field(
			String type, String description, Map.Entry<String, Object>... extras) {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("type", type);
		f.put("description", description);
		for (Map.Entry<String, Object> e : extras) {
			f.put(e.getKey(), e.getValue());
		}
		return f;
	}
}

