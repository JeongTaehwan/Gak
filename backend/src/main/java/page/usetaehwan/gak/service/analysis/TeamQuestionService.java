// requirements.md 1·4·5장 — 자유 질문을 teamId + season 과 함께 진단 경로로 보낸다
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
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.config.AiRateLimiter;
import page.usetaehwan.gak.dto.analysis.AnalysisWindow;
import page.usetaehwan.gak.dto.analysis.TeamAnswer;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.AnthropicResult;

/**
 * 자유 질문 하나를 <b>그 팀·그 시즌의 계산된 진단 데이터</b>로 답한다.
 *
 * <h2>분기하지 않는다</h2>
 * <p>질문을 읽고 예측 화면이나 순위표로 보내는 분류기를 두지 않았다. 분류기는 틀리고,
 * 틀리면 사용자는 자기가 묻지 않은 화면에 가 있다. 모든 자유 질문은 진단 경로 하나로
 * 간다 — 답할 수 없으면 다른 데로 보내는 대신 <b>답할 수 없다고 말한다.</b>
 *
 * <h2>teamId 와 season 을 함께 받는다</h2>
 * <p>season 이 없으면 회고 중에 던진 질문이 현재 시즌 데이터로 답해진다. 타임라인은
 * 2023-24를 그리고 있는데 대화만 다른 해를 말하는 상태가 되고, 둘 다 화면상 멀쩡하다.
 *
 * <h2>세 겹은 AI 진단과 같다</h2>
 * <ol>
 *   <li><b>재료 통제</b> — 프롬프트에 계산된 지표만 넣는다. 뉴스·이적·전술은 애초에 없다.</li>
 *   <li><b>코드 게이트</b> — 그 시즌에 잴 것이 없으면 <b>부르지 않는다.</b> "데이터 없으면
 *       답하지 마세요"라고 부탁하는 대신 답할 기회를 주지 않는다.</li>
 *   <li><b>응답 검증</b> — 형식은 맞는데 내용이 빈 응답을 버린다. 근거 없는 ANSWERED 는
 *       ANSWERED 로 두지 않는다 — 성공한 척이 실패보다 나쁘다.</li>
 * </ol>
 *
 * <h2>분모는 우리가 적는다</h2>
 * <p>{@link TeamAnswer.Basis}는 계산된 {@link AnalysisWindow} 에서 그대로 옮긴다. 모델에게
 * 맡기면 대개는 밝히지만 가끔 빠뜨리고, 빠뜨린 날 화면이 부분합을 전체처럼 말한다.
 */
@Service
public class TeamQuestionService {

	private static final Logger log = LoggerFactory.getLogger(TeamQuestionService.class);

	/** 답 하나에 실을 근거의 최대 개수. 많은 것보다 결정적인 게 낫다. */
	private static final int MAX_EVIDENCE = 5;

	private final TeamDiagnosticsService diagnosticsService;
	private final TeamSelectionService selectionService;
	private final AnthropicClient client;
	private final ObjectMapper objectMapper;
	private final AiRateLimiter limiter;

	public TeamQuestionService(TeamDiagnosticsService diagnosticsService,
	                           TeamSelectionService selectionService,
	                           AnthropicClient client,
	                           ObjectMapper objectMapper,
	                           AiRateLimiter limiter) {
		this.diagnosticsService = diagnosticsService;
		this.selectionService = selectionService;
		this.client = client;
		this.objectMapper = objectMapper;
		this.limiter = limiter;
	}

	/**
	 * <p><b>예외를 던지지 않는다</b>(팀이 없을 때 진단 서비스가 던지는 것 제외). 어떤 실패든
	 * 상태를 가진 {@link TeamAnswer} 로 돌아온다 — 실패는 이 경로에서 예외 상황이 아니라
	 * 화면이 말해야 할 네 가지 중 하나다.
	 *
	 * @param teamId   질문 대상 팀
	 * @param season   질문 대상 시즌. 이 시즌 데이터로만 답한다
	 * @param question 사용자가 입력한 질문 전문
	 */
	@Transactional(readOnly = true)
	public TeamAnswer answer(Long teamId, Integer season, String question, String clientIp) {
		TeamDiagnostics diagnostics = diagnosticsService.diagnose(teamId,
				DiagnosticsOptions.DEFAULTS.withSeason(season));

		boolean leagueRecord = selectionService.eligible(teamId, season);
		TeamAnswer.Basis basis = basis(diagnostics.window(), leagueRecord);

		String gate = gate(diagnostics, leagueRecord);
		if (gate != null) {
			return TeamAnswer.unanswered(TeamAnswer.Status.INSUFFICIENT_DATA, gate, basis);
		}
		if (!client.available()) {
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, AnswerMessages.NOT_CONFIGURED, basis);
		}

		// 한도 소모는 실제 유료 호출 직전 — 게이트·키 부재로 끝난 요청은 세지 않는다.
		// 상태 어휘 5종은 승인된 계약이라 새 상태를 만들지 않는다. 한도 초과는
		// ANALYSIS_FAILED 에 사유 문구로만 가른다 (잠정 — DG-OQ-21 미정).
		AiRateLimiter.Decision decision = limiter.tryConsume(clientIp);
		if (!decision.allowed()) {
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, decision.message(), basis);
		}

		AnthropicResult result = client.complete(
				QuestionPromptFactory.SYSTEM,
				QuestionPromptFactory.userPrompt(diagnostics, question),
				RESPONSE_SCHEMA);

		if (!result.succeeded()) {
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, reasonFor(result.failure()), basis);
		}
		return parse(result.json(), basis);
	}

	/**
	 * 부르기 전에 잡는 것 — <b>그 시즌에 잴 것이 있는가.</b>
	 *
	 * <p>표본 5건 미만도 여기서 막는다 — 진단 블록의 AI 게이트와 같은 기준
	 * ({@code SampleConfidence.allowsRates}, 단일 출처)이다. 원래는 "질문마다 필요한
	 * 표본이 다르다"는 이유로 막지 않았지만, 프론트 차단만으로는 API 직접 호출이
	 * 얇은 표본으로 모델 답변을 만들 수 있어 [9] 리뷰 판단으로 서버에서도 막기로 했다.
	 * "다음 경기 언제야"처럼 소표본으로도 답되는 질문까지 함께 막힌다는 트레이드오프를
	 * 오너가 수용했다.
	 *
	 * @return 막아야 하면 사용자에게 보여줄 사유, 통과면 null
	 */
	private String gate(TeamDiagnostics d, boolean leagueRecord) {
		AnalysisWindow w = d.window();
		if (w.season() == null || w.seasonFixtures() == 0) {
			return AnswerMessages.NO_SEASON_DATA;
		}
		if (!leagueRecord) {
			// 컵 경기만 있는 시즌. 컵 몇 경기로 시즌 전체를 진단하지 않는다.
			return AnswerMessages.NO_LEAGUE_RECORD;
		}
		if (w.analyzedFixtures() == 0) {
			return AnswerMessages.NOTHING_PLAYED;
		}
		if (!d.form().confidence().allowsRates()) {
			return AnswerMessages.INSUFFICIENT_SAMPLE.formatted(
					d.form().sampleSize(),
					page.usetaehwan.gak.dto.analysis.SampleConfidence.MIN_SAMPLE_FOR_RATE);
		}
		return null;
	}

	/** 실패 사유를 화면에 올릴 한국어로. 기술 용어를 사용자에게 보이지 않는다. */
	private String reasonFor(AnthropicResult.Failure failure) {
		return switch (failure) {
			case DISABLED -> AnswerMessages.NOT_CONFIGURED;
			case TIMEOUT -> AnswerMessages.TIMEOUT;
			case TRANSPORT -> AnswerMessages.TRANSPORT;
			case REFUSED -> AnswerMessages.REFUSED;
			case MALFORMED -> AnswerMessages.MALFORMED;
		};
	}

	/**
	 * 응답을 옮기고 <b>내용이 있는지</b> 검증한다.
	 *
	 * <p>구조화 출력의 {@code required} 는 "키가 있어야 한다"는 뜻이지 "값이 비면 안 된다"가
	 * 아니다. 형식이 완벽하고 내용만 없는 응답이 실제로 온다. 그걸 통과시키면 화면에
	 * <b>빈 답이 답인 척</b> 뜬다 — 실패했으면 "답하지 못했다"고 말했을 자리다.
	 */
	private TeamAnswer parse(String json, TeamAnswer.Basis basis) {
		JsonNode root;
		try {
			root = objectMapper.readTree(json);
		} catch (Exception e) {
			log.warn("질문 응답 파싱 실패: {}", e.toString());
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, AnswerMessages.MALFORMED, basis);
		}

		TeamAnswer.Status status = statusOf(root.path("status").asText(""));
		if (status == null) {
			log.warn("질문 응답의 status 를 알 수 없어 폐기: {}", quote(root.path("status").asText("")));
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, AnswerMessages.MALFORMED, basis);
		}
		if (status != TeamAnswer.Status.ANSWERED) {
			return TeamAnswer.unanswered(status, AnswerMessages.of(status), basis);
		}

		String answer = root.path("answer").asText("").trim();
		List<TeamAnswer.Evidence> evidence = evidence(root);

		// 답했다고 말하면서 내용이나 근거가 없으면, 답한 게 아니라 실패한 것이다.
		if (isFiller(answer) || evidence.isEmpty()) {
			log.warn("근거 없는 ANSWERED 라 폐기: answer={}, 근거 {}건", quote(answer), evidence.size());
			return TeamAnswer.unanswered(
					TeamAnswer.Status.ANALYSIS_FAILED, AnswerMessages.EMPTY_ANSWER, basis);
		}
		if (evidence.size() > MAX_EVIDENCE) {
			evidence = evidence.subList(0, MAX_EVIDENCE);
		}

		List<String> unknowns = new ArrayList<>();
		for (JsonNode u : root.path("unknowns")) {
			String text = u.asText("").trim();
			if (!isFiller(text)) {
				unknowns.add(text);
			}
		}
		return TeamAnswer.answered(answer, evidence, unknowns, basis);
	}

	/** 세 칸이 다 찬 것만 근거로 남긴다. 한 칸이라도 비면 검증할 수 없는 문장일 뿐이다. */
	private List<TeamAnswer.Evidence> evidence(JsonNode root) {
		List<TeamAnswer.Evidence> evidence = new ArrayList<>();
		int dropped = 0;
		for (JsonNode e : root.path("evidence")) {
			String metric = e.path("metric").asText("").trim();
			String value = e.path("value").asText("").trim();
			String claim = e.path("claim").asText("").trim();
			if (isFiller(metric) || isFiller(value) || isFiller(claim)) {
				dropped++;
				continue;
			}
			evidence.add(new TeamAnswer.Evidence(metric, value, claim));
		}
		if (dropped > 0) {
			log.warn("질문 응답 근거 {}건이 비어 있어 버림 (남은 {}건)", dropped, evidence.size());
		}
		return evidence;
	}

	private static TeamAnswer.Status statusOf(String raw) {
		for (TeamAnswer.Status status : TeamAnswer.Status.values()) {
			if (status.name().equals(raw)) {
				return status;
			}
		}
		return null;
	}

	/** 계산 결과를 그대로 옮긴다 — 분모는 모델이 아니라 우리가 적는다. */
	private static TeamAnswer.Basis basis(AnalysisWindow w, boolean leagueRecord) {
		return new TeamAnswer.Basis(
				w.season(), w.calendarSeason(),
				w.analyzedFixtures(), w.seasonFixtures(),
				w.upcomingFixtures(), w.excludedFixtures(),
				w.from(), w.to(), leagueRecord);
	}

	/**
	 * 값이 비었거나 <b>자리만 채운 문자열</b>인가. 모델은 required 를 만족시키려고
	 * {@code "placeholder"}·{@code "N/A"} 같은 걸 넣는다 — 형식 검사는 통과하고 사람만 속는다.
	 */
	private static boolean isFiller(String value) {
		if (value == null || value.isBlank()) {
			return true;
		}
		String v = value.trim().toLowerCase();
		return FILLER_VALUES.contains(v) || v.chars().allMatch(c -> c == '-' || c == '.' || c == '?');
	}

	private static final Set<String> FILLER_VALUES = Set.of(
			"placeholder", "n/a", "na", "tbd", "todo", "none", "null", "unknown",
			"string", "example", "example value", "value", "metric", "claim",
			"없음", "미정", "해당없음", "알수없음", "알 수 없음", "미상");

	private static String quote(String s) {
		String t = s.length() > 40 ? s.substring(0, 40) + "…" : s;
		return "\"" + t + "\"";
	}

	/**
	 * 응답 스키마.
	 *
	 * <p>모델은 스키마에 적힌 <b>순서대로</b> 필드를 생성한다. 그래서 {@code evidence} 가
	 * 맨 앞이다 — 쓸 수 있는 근거를 먼저 모아 놓고, 그걸 보며 "답할 수 있는가"를 정하고,
	 * 그다음 답을 쓴다. 반대로 하면 답을 정해 놓고 근거를 끼워 맞춘다.
	 * {@code Map.of()} 는 순서를 보장하지 않으므로({@code LinkedHashMap} 필수) 이 순서가
	 * 조용히 뒤집히지 않게 한다.
	 */
	private static final Map<String, Object> RESPONSE_SCHEMA = schema();

	private static Map<String, Object> schema() {
		Map<String, Object> properties = new LinkedHashMap<>();
		properties.put("evidence", field("array",
				"답의 근거가 될 지표. **가장 먼저 채운다.** 받은 지표에서 그대로 인용하며 "
						+ "세 칸을 모두 채운다. 답할 수 없는 질문이면 빈 배열로 둔다",
				Map.entry("items", evidenceItem())));
		properties.put("status", statusField());
		properties.put("answer", field("string",
				"status 가 ANSWERED 일 때의 답변. 2~5문장. 위 evidence 의 수치를 문장 안에 녹인다. "
						+ "다른 상태에서는 빈 문자열"));
		properties.put("unknowns", field("array",
				"이 답을 더 확실히 하려면 필요하지만 우리가 갖고 있지 않은 정보",
				Map.entry("items", Map.of("type", "string"))));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		root.put("properties", properties);
		root.put("required", List.of("evidence", "status", "answer", "unknowns"));
		root.put("additionalProperties", false);
		return root;
	}

	/** 상태는 자유 문장이 아니라 닫힌 값이다 — 출력 공간을 닫으면 오분류만 남고 창작이 사라진다. */
	private static Map<String, Object> statusField() {
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("type", "string");
		f.put("description", "ANSWERED / INSUFFICIENT_DATA / OUT_OF_SCOPE / UNINTELLIGIBLE 중 하나");
		f.put("enum", List.of("ANSWERED", "INSUFFICIENT_DATA", "OUT_OF_SCOPE", "UNINTELLIGIBLE"));
		return f;
	}

	private static Map<String, Object> evidenceItem() {
		Map<String, Object> props = new LinkedHashMap<>();
		props.put("metric", field("string", "근거가 된 지표 이름. 예: \"구간 내 최단 간격\""));
		props.put("value", field("string", "그 값. 받은 숫자를 그대로. 예: \"3일\""));
		props.put("claim", field("string", "이 수치가 무엇을 말하는지 한 문장"));

		Map<String, Object> item = new LinkedHashMap<>();
		item.put("type", "object");
		item.put("properties", props);
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
