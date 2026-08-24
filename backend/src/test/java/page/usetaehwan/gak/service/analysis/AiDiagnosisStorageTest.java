// requirements.md DG 7절
package page.usetaehwan.gak.service.analysis;

import java.time.Clock;
import java.time.ZoneOffset;
import page.usetaehwan.gak.config.AiRateLimitProperties;
import page.usetaehwan.gak.config.AiRateLimiter;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import page.usetaehwan.gak.domain.AiDiagnosisRecord;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.dto.analysis.AbsenceSummary;
import page.usetaehwan.gak.dto.analysis.AiDiagnosis;
import page.usetaehwan.gak.dto.analysis.AnalysisWindow;
import page.usetaehwan.gak.dto.analysis.CongestionReport;
import page.usetaehwan.gak.dto.analysis.FormSummary;
import page.usetaehwan.gak.dto.analysis.OpponentStrength;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.dto.analysis.TravelSummary;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.AnthropicResult;
import page.usetaehwan.gak.repository.AiDiagnosisRecordRepository;

/**
 * AI 진단 <b>저장·재사용</b>을 실제 DB(H2)로 태운다 (DG 7절).
 *
 * <p>{@link AiDiagnosisServiceTest}가 안전장치(게이트·검증·실패 처리)를 보고, 이 파일은
 * 그 위의 저장 계층을 본다: 검증을 통과한 서술이 저장되는가, 분모가 그대로면 모델 없이
 * 재사용되는가, 분모가 변하면 교체되는가, 그리고 <b>낡은 저장분이 실패의 폴백으로
 * 쓰이지 않는가</b>. 유니크 제약과 insert 경합 해소는 진짜 DB 가 있어야만 검증된다.
 */
@SpringBootTest
@Import(AiDiagnosisStorageTest.ScriptedClientConfig.class)
@ActiveProfiles("test")
class AiDiagnosisStorageTest {

	private static final long MAN_UTD = 33L;
	private static final int SEASON = 2023;
	private static final int WINDOW_DAYS = 14;
	private static final int MIN_MATCHES = 5;

	@Autowired AiDiagnosisService service;
	@Autowired com.fasterxml.jackson.databind.ObjectMapper objectMapper;
	@Autowired AiDiagnosisArchive archive;
	@Autowired AiDiagnosisRecordRepository repository;
	@Autowired ScriptedClient client;

	@BeforeEach
	void reset() {
		repository.deleteAll();
		client.reset();
	}

	// --- 테스트용 클라이언트 ---------------------------------------------------

	/** 정해 둔 답을 돌려주고 호출 횟수를 센다 — "모델을 다시 불렀는가"가 이 파일의 관심사다. */
	static final class ScriptedClient implements AnthropicClient {
		private AnthropicResult next = AnthropicResult.failed(AnthropicResult.Failure.DISABLED);
		int calls;

		void reply(String json) {
			next = AnthropicResult.ok(json);
		}

		void fail(AnthropicResult.Failure failure) {
			next = AnthropicResult.failed(failure);
		}

		void reset() {
			next = AnthropicResult.failed(AnthropicResult.Failure.DISABLED);
			calls = 0;
		}

		@Override
		public boolean available() {
			return true;
		}

		@Override
		public AnthropicResult complete(String system, String user, Map<String, Object> schema) {
			calls++;
			return next;
		}
	}

	@TestConfiguration
	static class ScriptedClientConfig {
		@Bean
		@Primary
		ScriptedClient scriptedClient() {
			return new ScriptedClient();
		}
	}

	// --- 저장과 재사용 ----------------------------------------------------------

	@Test
	@DisplayName("첫 성공은 저장되고, 같은 분모의 재방문은 모델 없이 같은 내용을 돌려받는다")
	void reusesTheStoredNarrationWhenTheDenominatorIsUnchanged() {
		client.reply(RESPONSE_A);
		AiDiagnosis first = service.narrate(diagnostics(40), "ip-1");

		assertThat(first.available()).isTrue();
		assertThat(client.calls).isEqualTo(1);

		AiDiagnosisRecord stored = findStored();
		assertThat(stored.getAnalyzedFixtures()).isEqualTo(40);
		assertThat(stored.getHeadline()).isEqualTo("2일 간격 3연전 뒤 승점이 끊겼다");

		// 재방문 — 분모가 그대로면 수 초의 대기가 없어야 한다.
		AiDiagnosis second = service.narrate(diagnostics(40), "ip-1");

		assertThat(client.calls).as("모델을 다시 부르지 않는다").isEqualTo(1);
		assertThat(second.available()).isTrue();
		assertThat(second.headline()).isEqualTo(first.headline());
		assertThat(second.sub()).isEqualTo(first.sub());
		// 근거는 내용과 순서까지 그대로 돌아와야 한다 — 모델은 중요한 것부터 적고,
		// 저장을 거치며 순서가 섞이면 재방문 화면이 다른 얘기를 한다.
		assertThat(second.evidence()).containsExactlyElementsOf(first.evidence());
		assertThat(second.unknowns()).containsExactlyElementsOf(first.unknowns());
	}

	@Test
	@DisplayName("분모가 변하면 다시 부르고, 새 행이 아니라 기존 행을 교체한다")
	void recallsAndReplacesWhenTheDenominatorChanges() {
		client.reply(RESPONSE_A);
		service.narrate(diagnostics(40), "ip-1");
		Long originalId = findStored().getId();

		// 경기가 하나 더 치러져 분모가 40 → 41 로 변했다.
		client.reply(RESPONSE_B);
		AiDiagnosis result = service.narrate(diagnostics(41), "ip-1");

		assertThat(client.calls).isEqualTo(2);
		assertThat(result.available()).isTrue();
		assertThat(result.headline()).isEqualTo("일정 부담이 후반기에 몰렸다");

		// 조합당 한 행 — 쌓이지 않고 교체된다.
		assertThat(repository.count()).isEqualTo(1);
		AiDiagnosisRecord replaced = findStored();
		assertThat(replaced.getId()).as("행을 지웠다 새로 넣은 게 아니다").isEqualTo(originalId);
		assertThat(replaced.getAnalyzedFixtures()).isEqualTo(41);
		assertThat(replaced.getHeadline()).isEqualTo("일정 부담이 후반기에 몰렸다");
	}

	@Test
	@DisplayName("분모가 변했는데 새 호출이 실패하면 낡은 저장분이 아니라 '사용 불가'다")
	void neverServesAStaleNarrationWhenTheFreshCallFails() {
		client.reply(RESPONSE_A);
		service.narrate(diagnostics(40), "ip-1");

		client.fail(AnthropicResult.Failure.TIMEOUT);
		AiDiagnosis result = service.narrate(diagnostics(41), "ip-1");

		// 최신 지표 옆에 옛 분모로 쓴 문장이 서는 것이 이 저장 기능의 금지 상태다 —
		// unavailable 로 떨어지면 화면은 규칙 기반으로 동작한다(기존 계약).
		assertThat(result.available()).isFalse();
		assertThat(result.headline()).isNull();
		assertThat(result.unavailableReason()).isNotBlank();

		// 실패는 저장분을 건드리지 않는다 — 분모 40 시절 기록이 그대로 남는다.
		AiDiagnosisRecord stored = findStored();
		assertThat(stored.getAnalyzedFixtures()).isEqualTo(40);
		assertThat(stored.getHeadline()).isEqualTo("2일 간격 3연전 뒤 승점이 끊겼다");
	}

	@Test
	@DisplayName("표본이 부족하면 모델도 저장소도 쓰지 않는다 — 게이트가 저장 조회보다 먼저다")
	void thinSamplesBypassBothTheModelAndTheStore() {
		// 분모까지 일치하는 저장분이 있어도, 표본 부족이면 그걸 꺼내 주지 않아야 한다.
		repository.save(AiDiagnosisRecord.create(MAN_UTD, SEASON, WINDOW_DAYS, MIN_MATCHES, 40,
				"저장돼 있던 결론", "저장돼 있던 부연 문장.",
				List.of(AiDiagnosisRecord.Evidence.of("지표", "값", "주장")),
				List.of(), Instant.parse("2024-05-01T00:00:00Z")));

		AiDiagnosis result = service.narrate(
				diagnostics(40, 3, SampleConfidence.LOW), "ip-1");

		assertThat(client.calls).isZero();
		assertThat(result.available()).isFalse();
		assertThat(result.unavailableReason()).contains("3건");
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("검증에서 버린 응답은 저장되지 않는다 — 껍데기가 기록이 되면 재방문마다 뜬다")
	void rejectedResponsesAreNeverStored() {
		client.reply("""
				{"headline":"placeholder","sub":"placeholder","evidence":[],"unknowns":[]}
				""");

		AiDiagnosis result = service.narrate(diagnostics(40), "ip-1");

		assertThat(result.available()).isFalse();
		assertThat(repository.count()).isZero();
	}

	@Test
	@DisplayName("키가 없어도(클라이언트 비활성) 분모가 일치하는 저장분은 재사용된다")
	void storedNarrationsSurviveAMissingApiKey() {
		repository.save(record("키 없이도 살아남을 결론"));

		// 항상 비활성인 클라이언트 — available() 검사가 저장 조회를 가리면 안 된다.
		AnthropicClient disabled = new AnthropicClient() {
			@Override
			public boolean available() {
				return false;
			}

			@Override
			public AnthropicResult complete(String system, String user, Map<String, Object> schema) {
				throw new AssertionError("비활성 클라이언트는 호출되면 안 된다");
			}
		};
		AiDiagnosisService keyless = new AiDiagnosisService(
				disabled, objectMapper, archive,
				Clock.fixed(Instant.parse("2024-05-20T12:00:00Z"), ZoneOffset.UTC),
				new AiRateLimiter(new AiRateLimitProperties(true, 1, 1, null),
						Clock.fixed(Instant.parse("2024-05-20T12:00:00Z"), ZoneOffset.UTC)));

		AiDiagnosis result = keyless.narrate(diagnostics(40), "ip-1");

		// 저장분은 검증을 통과한 실제 응답이다 — 배지 계약("실제 응답으로만")과 어긋나지 않는다.
		assertThat(result.available()).isTrue();
		assertThat(result.headline()).isEqualTo("키 없이도 살아남을 결론");

		// 분모가 달라 새 호출이 필요해지면 그때는 설정 없음으로 떨어진다.
		assertThat(keyless.narrate(diagnostics(41), "ip-1").available()).isFalse();
	}

	// --- 한도와 저장의 상호작용 (DG 7·8절) --------------------------------------

	@Test
	@DisplayName("저장 히트는 한도를 소모하지 않는다 — 예산이 바닥나도 저장분은 계속 나온다")
	void storageHitsDoNotConsumeTheBudget() {
		client.reply(RESPONSE_A);
		// 전역 예산 1 — 최초 생성이 전부 소진한다.
		AiRateLimiter tight = new AiRateLimiter(
				new AiRateLimitProperties(true, 1, 1, null),
				Clock.fixed(Instant.parse("2024-05-20T12:00:00Z"), ZoneOffset.UTC));
		AiDiagnosisService limited = new AiDiagnosisService(
				client, objectMapper, archive,
				Clock.fixed(Instant.parse("2024-05-20T12:00:00Z"), ZoneOffset.UTC), tight);

		assertThat(limited.narrate(diagnostics(40), "ip-1").available()).isTrue();

		// 같은 분모 재방문 — 모델 호출 없이 저장분. 한도가 0이어도 막히면 안 된다.
		AiDiagnosis revisit = limited.narrate(diagnostics(40), "ip-2");
		assertThat(revisit.available()).isTrue();
		assertThat(client.calls).isEqualTo(1);

		// 반대로 분모가 변해 새 호출이 필요해지면 그때는 한도에 걸린다.
		AiDiagnosis blocked = limited.narrate(diagnostics(41), "ip-3");
		assertThat(blocked.available()).isFalse();
		assertThat(blocked.unavailableReason())
				.isEqualTo(AiRateLimiter.Decision.GLOBAL_EXCEEDED.message());
		assertThat(client.calls).isEqualTo(1);
	}

	// --- 동시 insert 경합 -------------------------------------------------------

	@Test
	@DisplayName("동시 miss→insert 경합 — 진 쪽은 유니크 위반을 받아 이긴 쪽 행을 재사용한다")
	void resolvesConcurrentDuplicateInsertsByRequery() {
		// 두 요청이 동시에 "저장분 없음"을 보고 각자 insert 하는 상황.
		AiDiagnosisRecord winner = archive.save(record("먼저 저장된 결론"));
		AiDiagnosisRecord loser = archive.save(record("나중에 저장을 시도한 결론"));

		// 예외가 새어 나오지 않고, 두 행이 되지도 않는다.
		assertThat(loser.getId()).isEqualTo(winner.getId());
		assertThat(loser.getHeadline()).isEqualTo("먼저 저장된 결론");
		assertThat(repository.count()).isEqualTo(1);
	}

	// --- 만들기 헬퍼 ------------------------------------------------------------

	private AiDiagnosisRecord findStored() {
		return repository.findByTeamIdAndSeasonAndWindowDaysAndMinMatches(
				MAN_UTD, SEASON, WINDOW_DAYS, MIN_MATCHES).orElseThrow();
	}

	private AiDiagnosisRecord record(String headline) {
		return AiDiagnosisRecord.create(MAN_UTD, SEASON, WINDOW_DAYS, MIN_MATCHES, 40,
				headline, "부연 문장.",
				List.of(AiDiagnosisRecord.Evidence.of("밀집 구간 수", "4개", "밀집이 반복됐다")),
				List.of("선수 개개인의 기여도"), Instant.parse("2024-05-20T00:00:00Z"));
	}

	/** "AI를 불러도 되는" 진단 — 분모만 바꿔 가며 쓴다. */
	private TeamDiagnostics diagnostics(int analyzedFixtures) {
		return diagnostics(analyzedFixtures, 6, SampleConfidence.MODERATE);
	}

	private TeamDiagnostics diagnostics(
			int analyzedFixtures, int formSample, SampleConfidence confidence) {
		return new TeamDiagnostics(
				MAN_UTD, "맨체스터 유나이티드", "MUN", Instant.parse("2024-05-20T00:00:00Z"),
				new AnalysisWindow(
						SEASON, false,
						Instant.parse("2023-08-11T19:00:00Z"),
						Instant.parse("2024-05-19T15:00:00Z"),
						Instant.parse("2024-05-20T00:00:00Z"),
						Instant.parse("2023-08-11T19:00:00Z"),
						Instant.parse("2024-05-19T15:00:00Z"),
						42, analyzedFixtures, 0, 2, 0, 38, 0),
				List.of(),
				new CongestionReport(WINDOW_DAYS, MIN_MATCHES, true, analyzedFixtures, 4, 3, 3.0, List.of()),
				new CongestionReport(WINDOW_DAYS, MIN_MATCHES, true, 30, 3, 4, 5.0, List.of()),
				new FormSummary(formSample, recentPicks(formSample), 3, 1, 2, 10,
						formSample * 3, confidence.allowsRates() ? 0.55 : null, null, confidence),
				OpponentStrength.unmeasured(formSample),
				new TravelSummary(
						Instant.parse("2023-08-11T19:00:00Z"),
						Instant.parse("2024-05-19T15:00:00Z"),
						20, 18, 2, 12000.0, 666.0, 1400.0),
				AbsenceSummary.notCovered(analyzedFixtures, null),
				List.of(),
				List.of());
	}

	private static List<Pick> recentPicks(int n) {
		List<Pick> picks = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			picks.add(i % 3 == 0 ? Pick.W : i % 3 == 1 ? Pick.D : Pick.L);
		}
		return picks;
	}

	private static final String RESPONSE_A = """
			{
			  "headline": "2일 간격 3연전 뒤 승점이 끊겼다",
			  "sub": "밀집 구간 직후 2경기에서 승점을 얻지 못했다.",
			  "evidence": [
			    {"claim": "구간이 유난히 빡빡했다", "metric": "구간 내 최단 간격", "value": "2일"},
			    {"claim": "밀집이 반복됐다", "metric": "밀집 구간 수", "value": "4개"}
			  ],
			  "unknowns": ["선수 개개인의 기여도"]
			}
			""";

	private static final String RESPONSE_B = """
			{
			  "headline": "일정 부담이 후반기에 몰렸다",
			  "sub": "추가된 경기로 간격 중앙값이 3일로 줄었다.",
			  "evidence": [
			    {"claim": "간격이 줄었다", "metric": "간격 중앙값", "value": "3일"}
			  ],
			  "unknowns": []
			}
			""";
}
