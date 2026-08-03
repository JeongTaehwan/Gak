package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.dto.analysis.AbsenceSummary;
import page.usetaehwan.gak.dto.analysis.AiDiagnosis;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.dto.analysis.AnalysisWindow;
import page.usetaehwan.gak.dto.analysis.CongestionReport;
import page.usetaehwan.gak.dto.analysis.FormSummary;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.dto.analysis.TravelSummary;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.AnthropicResult;

/**
 * AI 진단의 <b>안전장치</b>를 검증한다.
 *
 * <p>여기서 모델의 답이 좋은지는 검증하지 않는다 — 비결정적이라 테스트할 수 없고,
 * 그게 바로 배지가 필요한 이유다. 대신 <b>모델이 무모해질 수 있는 경로를 우리가 막고
 * 있는지</b>를 검증한다: 표본이 없을 때 부르지 않는가, 근거 없는 답을 버리는가,
 * 실패했을 때 조용히 물러나는가.
 */
class AiDiagnosisServiceTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	// --- 테스트용 클라이언트 ---------------------------------------------------

	/** 부르면 기록하고 정해진 답을 돌려준다. */
	private static final class FakeClient implements AnthropicClient {
		private final AnthropicResult result;
		int calls = 0;
		String lastUserPrompt;

		FakeClient(AnthropicResult result) {
			this.result = result;
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public AnthropicResult complete(String system, String user, Map<String, Object> schema) {
			calls++;
			lastUserPrompt = user;
			return result;
		}
	}

	private AiDiagnosisService serviceWith(AnthropicClient client) {
		return new AiDiagnosisService(client, objectMapper);
	}

	private static FakeClient replying(String json) {
		return new FakeClient(AnthropicResult.ok(json));
	}

	// --- 진단 표본 만들기 -------------------------------------------------------

	/** 밀집 구간이 있고 폼 표본도 충분한, "AI를 불러도 되는" 진단. */
	private TeamDiagnostics healthy() {
		return diagnostics(6, SampleConfidence.MODERATE, true);
	}

	private TeamDiagnostics diagnostics(int formSample, SampleConfidence confidence, boolean detectable) {
		return diagnostics(formSample, confidence, detectable,
				confidence.allowsRates() ? 0.55 : null);
	}

	private TeamDiagnostics diagnostics(
			int formSample, SampleConfidence confidence, boolean detectable, Double pointsRate) {
		return new TeamDiagnostics(
				33L, "맨체스터 유나이티드", "MUN", Instant.parse("2024-05-20T00:00:00Z"),
				new AnalysisWindow(
						Instant.parse("2023-08-11T19:00:00Z"),
						Instant.parse("2024-05-19T15:00:00Z"), 42, 40, 2),
				List.of(),
				new CongestionReport(14, 5, detectable, 40, 4, 3, 3.0, List.of()),
				new FormSummary(6, formSample, recentPicks(formSample), 3, 1, 2, 10,
						formSample * 3, pointsRate, null, confidence),
				new TravelSummary(
						Instant.parse("2023-08-11T19:00:00Z"),
						Instant.parse("2024-05-19T15:00:00Z"),
						20, 18, 2, 12000.0, 666.0, 1400.0),
				AbsenceSummary.notCovered(40),
				List.of());
	}

	private static List<Pick> recentPicks(int n) {
		List<Pick> picks = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			picks.add(i % 3 == 0 ? Pick.W : i % 3 == 1 ? Pick.D : Pick.L);
		}
		return picks;
	}

	// --- 표본 게이트 -----------------------------------------------------------

	@Test
	@DisplayName("표본이 부족하면 모델을 아예 부르지 않는다")
	void doesNotCallTheModelOnThinSamples() {
		FakeClient client = replying("{}");

		AiDiagnosis result = serviceWith(client)
				.narrate(diagnostics(3, SampleConfidence.LOW, true));

		// "표본이 적으면 결론 내지 마"라고 부탁하는 대신 결론 낼 기회를 주지 않는다
		assertThat(client.calls).isZero();
		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).contains("3건");
	}

	@Test
	@DisplayName("확정된 경기가 없으면 부르지 않는다")
	void doesNotCallWhenNothingIsSettled() {
		FakeClient client = replying("{}");

		AiDiagnosis result = serviceWith(client)
				.narrate(diagnostics(0, SampleConfidence.NONE, true));

		assertThat(client.calls).isZero();
		assertThat(result.available()).isFalse();
	}

	@Test
	@DisplayName("표본 기준은 규칙 기반과 같다 — 한쪽만 결론을 내면 AI가 더 아는 것처럼 보인다")
	void usesTheSameThresholdAsTheRuleBasedPath() {
		// 4건: 비율을 감추는 경계 바로 아래 → AI도 결론 없음
		assertThat(serviceWith(replying("{}"))
				.narrate(diagnostics(4, SampleConfidence.LOW, true)).available()).isFalse();

		// 5건: 비율을 공개하는 경계 → AI도 결론 가능
		FakeClient client = replying(validResponse());
		assertThat(serviceWith(client)
				.narrate(diagnostics(5, SampleConfidence.MODERATE, true)).available()).isTrue();
		assertThat(client.calls).isEqualTo(1);
	}

	@Test
	@DisplayName("키가 없으면 부르지 않고, 그건 오류가 아니다")
	void treatsMissingKeyAsANormalState() {
		AiDiagnosis result = serviceWith(new page.usetaehwan.gak.external.anthropic
				.DisabledAnthropicClient()).narrate(healthy());

		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).isNotBlank();
	}

	// --- 근거 강제 -------------------------------------------------------------

	@Test
	@DisplayName("근거가 없는 결론은 받지 않고 버린다")
	void rejectsConclusionsWithoutEvidence() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"일정이 팀을 갉아먹고 있다","sub":"빡빡했다.",
				 "evidence":[],"unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).contains("근거");
	}

	@Test
	@DisplayName("결론이 비어 있으면 버린다")
	void rejectsEmptyHeadline() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"","sub":"...","evidence":[{"claim":"a","metric":"b","value":"c"}],
				 "unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
	}

	@Test
	@DisplayName("근거가 붙은 결론은 그대로 통과시킨다")
	void acceptsAConclusionThatCarriesItsNumbers() {
		AiDiagnosis result = serviceWith(replying(validResponse())).narrate(healthy());

		assertThat(result.available()).isTrue();
		assertThat(result.headline()).isEqualTo("2일 간격 3연전 뒤 승점이 끊겼다");
		assertThat(result.evidence()).hasSize(1);
		assertThat(result.evidence().get(0).metric()).isEqualTo("구간 내 최단 간격");
		assertThat(result.evidence().get(0).value()).isEqualTo("2일");
		assertThat(result.unknowns()).contains("선수 개개인의 기여도");
	}

	// --- 빈 껍데기 거부 ---------------------------------------------------------
	//
	// 여기가 이 파일에서 가장 중요한 구역이다. 형식이 틀린 응답은 파싱이 알아서 걸러 주지만,
	// **형식은 맞고 내용만 없는 응답**은 그냥 통과해 버린다. 그러면 화면 배지가 "AI 분석"으로
	// 바뀐 채 빈 문장이 뜬다 — 실패했으면 규칙 기반 문장이 남았을 텐데, 성공한 척하는 쪽이
	// 더 나쁘다. 아래는 전부 실제로 모델이 보냈거나 보낼 수 있는 모양이다.

	@Test
	@DisplayName("실제로 겪은 빈 껍데기 — headline/sub 가 'placeholder' 이고 근거 칸이 빈 응답")
	void rejectsTheRealWorldPlaceholderResponse() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"placeholder","sub":"placeholder",
				 "evidence":[{"claim":"시즌 전반기에 밀집 구간이 집중됐다","metric":"","value":""}],
				 "unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).isNotBlank();
	}

	@Test
	@DisplayName("required 를 만족해도 빈 값이면 통과시키지 않는다 — 스키마는 '키 존재'만 보장한다")
	void requiredKeysWithEmptyValuesAreNotEnough() {
		// 모든 키가 있고 타입도 맞다. 스키마 검증은 통과하는 응답이다.
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"","sub":"","evidence":[],"unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
	}

	@Test
	@DisplayName("근거가 required 인데 빈 배열로 와도 거부한다 (실제로 겪은 응답)")
	void rejectsEmptyEvidenceArray() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"밀집은 시즌 전반부에 몰렸다","sub":"구간 3개가 9~12월에 있다.",
				 "evidence":[],"unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).contains("근거");
	}

	@Test
	@DisplayName("자리만 채운 문자열도 빈 값으로 본다 — N/A, -, 없음 등")
	void treatsFillerStringsAsEmpty() {
		for (String filler : new String[] {"placeholder", "N/A", "-", "없음", "TBD", "...", "null"}) {
			AiDiagnosis result = serviceWith(replying("""
					{"headline":"%s","sub":"밀집 구간 3개가 전반부에 몰려 있다.",
					 "evidence":[{"claim":"a","metric":"b","value":"c"}],"unknowns":[]}
					""".formatted(filler))).narrate(healthy());

			assertThat(result.available()).as("결론이 %s", filler).isFalse();
		}
	}

	@Test
	@DisplayName("결론과 부연이 글자까지 같으면 한 문자열로 두 칸을 때운 것이다")
	void rejectsIdenticalHeadlineAndSub() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"일정이 빡빡했다","sub":"일정이 빡빡했다",
				 "evidence":[{"claim":"a","metric":"b","value":"c"}],"unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
	}

	@Test
	@DisplayName("근거 항목은 세 칸이 다 차야 한다 — 한 칸이라도 비면 그 항목을 버린다")
	void dropsEvidenceItemsWithEmptySlots() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"밀집 구간이 전반부에 몰렸다","sub":"9~12월에 3개가 집중됐다.",
				 "evidence":[
				   {"claim":"값 없는 근거","metric":"최단 간격","value":""},
				   {"claim":"지표 없는 근거","metric":"","value":"3일"},
				   {"claim":"제대로 된 근거","metric":"밀집 구간 수","value":"3개"}
				 ],"unknowns":[]}
				""")).narrate(healthy());

		// 온전한 것 하나가 남았으므로 결론은 살린다
		assertThat(result.available()).isTrue();
		assertThat(result.evidence()).hasSize(1);
		assertThat(result.evidence().get(0).metric()).isEqualTo("밀집 구간 수");
	}

	@Test
	@DisplayName("근거가 전부 반쪽이면 남는 게 없으므로 결론째 버린다")
	void rejectsWhenEveryEvidenceItemIsIncomplete() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"밀집 구간이 전반부에 몰렸다","sub":"9~12월에 3개가 집중됐다.",
				 "evidence":[
				   {"claim":"a","metric":"최단 간격","value":""},
				   {"claim":"b","metric":"","value":"3일"}
				 ],"unknowns":[]}
				""")).narrate(healthy());

		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).contains("근거");
	}

	@Test
	@DisplayName("unknowns 의 빈 항목은 조용히 걷어낸다 — 이건 결론을 무효로 만들지 않는다")
	void stripsEmptyUnknownsWithoutFailing() {
		AiDiagnosis result = serviceWith(replying("""
				{"headline":"밀집 구간이 전반부에 몰렸다","sub":"9~12월에 3개가 집중됐다.",
				 "evidence":[{"claim":"a","metric":"b","value":"c"}],
				 "unknowns":["상대 강도",""," ","N/A"]}
				""")).narrate(healthy());

		assertThat(result.available()).isTrue();
		assertThat(result.unknowns()).containsExactly("상대 강도");
	}

	// --- 실패 처리 -------------------------------------------------------------

	@Test
	@DisplayName("타임아웃·거절·전송 실패 모두 예외 없이 '사용 불가'로 돌아온다")
	void everyFailureDegradesQuietly() {
		for (AnthropicResult.Failure failure : AnthropicResult.Failure.values()) {
			AiDiagnosis result = serviceWith(new FakeClient(AnthropicResult.failed(failure)))
					.narrate(healthy());

			assertThat(result.available())
					.as("실패 사유 %s", failure).isFalse();
			// 화면에 그대로 띄울 수 있어야 한다 — 스택트레이스나 영문 예외명이 아니라
			assertThat(result.unavailableReason())
					.as("실패 사유 %s", failure).isNotBlank();
		}
	}

	@Test
	@DisplayName("응답이 JSON이 아니어도 터지지 않는다")
	void survivesGarbage() {
		AiDiagnosis result = serviceWith(replying("설명: 이 팀은...")).narrate(healthy());

		assertThat(result.available()).isFalse();
	}

	// --- 프롬프트 내용 ---------------------------------------------------------

	@Test
	@DisplayName("프롬프트에는 계산된 지표만 들어가고 경기 원본은 들어가지 않는다")
	void sendsComputedMetricsNotRawFixtures() {
		FakeClient client = replying(validResponse());
		serviceWith(client).narrate(healthy());

		String prompt = client.lastUserPrompt;
		// 계산 결과는 있다
		assertThat(prompt).contains("일정 밀집도").contains("최근 폼").contains("원정 이동거리");
		// 원본 경기 목록(킥오프 타임스탬프, fixtureId)은 없다
		assertThat(prompt).doesNotContain("fixtureId").doesNotContain("T00:00:00Z");
	}

	@Test
	@DisplayName("우리가 수집하지 않는 것을 프롬프트가 명시한다 — 지어낼 재료를 주지 않는다")
	void namesWhatWeDoNotHave() {
		FakeClient client = replying(validResponse());
		serviceWith(client).narrate(healthy());

		assertThat(client.lastUserPrompt)
				.contains("우리가 갖고 있지 않은 정보")
				.contains("선수 개개인의 기량")
				.contains("전술")
				.contains("팀 분위기");
	}

	@Test
	@DisplayName("결장 데이터가 없으면 '0명'이 아니라 '모름'이라고 넘긴다")
	void tellsTheModelThatMissingAbsenceDataIsNotZero() {
		FakeClient client = replying(validResponse());
		serviceWith(client).narrate(healthy());

		assertThat(client.lastUserPrompt).contains("'결장 0명'이 아니라 '모름'");
	}

	@Test
	@DisplayName("표본이 작아 감춘 비율은 '비율로 말하지 말라'고 함께 넘긴다")
	void warnsTheModelWhenARateWasSuppressed() {
		FakeClient client = replying(validResponse());
		// 게이트는 통과하지만 pointsRate 가 null 인 경계를 만든다
		var thin = diagnostics(5, SampleConfidence.MODERATE, true, null);

		serviceWith(client).narrate(thin);

		assertThat(client.lastUserPrompt).contains("비율로 말하지 마세요");
	}

	private static String validResponse() {
		return """
				{
				  "headline": "2일 간격 3연전 뒤 승점이 끊겼다",
				  "sub": "밀집 구간 직후 2경기에서 승점을 얻지 못했다.",
				  "evidence": [
				    {"claim": "구간이 유난히 빡빡했다", "metric": "구간 내 최단 간격", "value": "2일"}
				  ],
				  "unknowns": ["선수 개개인의 기여도"]
				}
				""";
	}
}
