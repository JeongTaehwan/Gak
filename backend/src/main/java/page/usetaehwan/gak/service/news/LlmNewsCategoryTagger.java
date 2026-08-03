package page.usetaehwan.gak.service.news;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import page.usetaehwan.gak.domain.NewsCategory;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.AnthropicResult;

/**
 * 헤드라인에 갈래를 붙인다. <b>이 앱에서 LLM이 닿는 유일한 뉴스 지점이다.</b>
 *
 * <h2>왜 여기는 규칙이 아닌가</h2>
 * <p>같은 137건으로 재 봤다. 게이트를 통과한 36건에 대해 키워드 태거는 <b>69.4%</b>였고,
 * 틀리는 이유가 어휘가 아니라 <b>의미</b>였다.
 *
 * <pre>
 *  "MU think they can beat City to £100m-rated Elliot Anderson"
 *        → 키워드는 MATCH. 'beat' 이 비유다(영입 경쟁이지 경기가 아니다)
 *
 *  "MU consider Camavinga and Berge as midfield overhaul continues"
 *        → 키워드는 OTHER. 'consider' 를 이적 어휘에 넣으면 다른 데서 오탐이 터진다
 *
 *  "Four Manchester United players to return for pre-season training"
 *        → SQUAD 갈래는 8건 중 0건 맞혔다
 * </pre>
 *
 * <p>게이트의 실패는 단어를 더하면 고쳐지지만, 이건 단어 목록으로 표현할 수 없다.
 *
 * <h2>지어내지 못하게 하는 것</h2>
 * <ol>
 *   <li><b>출력 스키마</b>가 {@code enum} 다섯 개로 닫혀 있다. 여섯 번째 값이 나올 수 없다</li>
 *   <li><b>넘긴 id 밖의 항목은 버린다</b>. 모델이 없는 기사를 만들어 내도 반영되지 않는다</li>
 *   <li><b>제목만 보낸다</b>. 기사 본문이 프롬프트에 없으므로 내용을 요약할 재료가 없다</li>
 * </ol>
 *
 * <p>이 세 겹이 {@code AiDiagnosisService} 의 세 겹보다 훨씬 얇아도 되는 이유는, 저쪽은
 * 자유 문장을 받고 여기는 라벨 하나를 받기 때문이다.
 */
public class LlmNewsCategoryTagger implements NewsCategoryTagger {

	private static final Logger log = LoggerFactory.getLogger(LlmNewsCategoryTagger.class);

	private final AnthropicClient client;
	private final ObjectMapper objectMapper;
	private final int batchSize;

	public LlmNewsCategoryTagger(AnthropicClient client, ObjectMapper objectMapper, int batchSize) {
		this.client = client;
		this.objectMapper = objectMapper;
		this.batchSize = Math.max(1, batchSize);
	}

	@Override
	public boolean available() {
		return client.available();
	}

	@Override
	public Map<Long, NewsCategory> tag(List<NewsItem> items) {
		if (items == null || items.isEmpty() || !client.available()) {
			return Map.of();
		}
		Map<Long, NewsCategory> result = new LinkedHashMap<>();
		for (int from = 0; from < items.size(); from += batchSize) {
			List<NewsItem> batch = items.subList(from, Math.min(from + batchSize, items.size()));
			result.putAll(tagBatch(batch));
		}
		return result;
	}

	private Map<Long, NewsCategory> tagBatch(List<NewsItem> batch) {
		// 넘긴 id 집합. 응답을 이 집합으로 거른다 — 모델이 만들어 낸 id 는 들어올 수 없다.
		Set<Long> allowed = new LinkedHashSet<>();
		batch.forEach(item -> allowed.add(item.getId()));

		AnthropicResult response = client.complete(SYSTEM_PROMPT, userPrompt(batch), schema());
		if (!response.succeeded()) {
			// 실패는 정상 분기다. 다음 회차에 다시 집어 간다(태그가 null 로 남아 있으므로).
			log.info("뉴스 갈래 태깅 건너뜀({}건): {}", batch.size(), response.failure());
			return Map.of();
		}
		return parse(response.json(), allowed);
	}

	private Map<Long, NewsCategory> parse(String json, Set<Long> allowed) {
		JsonNode root;
		try {
			root = objectMapper.readTree(json);
		} catch (Exception e) {
			log.warn("뉴스 갈래 응답을 읽지 못했습니다: {}", e.toString());
			return Map.of();
		}
		Map<Long, NewsCategory> parsed = new LinkedHashMap<>();
		for (JsonNode node : root.path("items")) {
			if (!node.hasNonNull("id")) {
				continue;
			}
			long id = node.path("id").asLong(-1);
			// 우리가 물어본 것만 받는다.
			if (!allowed.contains(id)) {
				log.debug("응답에 넘기지 않은 id 가 있어 무시합니다: {}", id);
				continue;
			}
			NewsCategory category = NewsCategory.parse(node.path("category").asText(null));
			if (category != null) {
				parsed.put(id, category);
			}
		}
		return parsed;
	}

	/**
	 * 사용자 프롬프트 — 번호와 제목뿐이다.
	 *
	 * <p>DB의 실제 id 를 그대로 쓴다. 배치 안 순번(1,2,3…)을 쓰면 응답이 밀렸을 때
	 * <b>조용히 어긋난 채로 저장</b>된다 — 1번 기사에 3번 갈래가 붙어도 아무도 모른다.
	 * 실제 id 를 쓰면 어긋난 값은 위의 {@code allowed} 필터에 걸려 버려진다.
	 */
	private String userPrompt(List<NewsItem> batch) {
		StringBuilder sb = new StringBuilder();
		sb.append("아래 축구 헤드라인 각각에 갈래를 하나씩 붙여라.\n\n");
		for (NewsItem item : batch) {
			sb.append("id=").append(item.getId())
					.append('\t').append(item.getTitle()).append('\n');
		}
		return sb.toString();
	}

	/**
	 * 시스템 프롬프트. 요청마다 동일하므로 프롬프트 캐시가 붙는다
	 * ({@code RestAnthropicClient} 가 {@code cache_control} 을 단다).
	 */
	private static final String SYSTEM_PROMPT = """
			너는 축구 뉴스 헤드라인 분류기다. 판단자가 아니다.

			하는 일: 주어진 헤드라인 각각에 아래 다섯 갈래 중 하나를 붙인다.
			하지 않는 일: 기사 내용을 추측하거나, 소문의 사실 여부를 판정하거나,
			              팀의 상태를 평가하거나, 헤드라인에 없는 정보를 더한다.

			갈래:
			- TRANSFER : 이적·영입·방출·계약·이적 소문·영입 경쟁
			- SQUAD    : 훈련·복귀·부상 소식·라인업 예상·선수 발언·선수단 분위기
			- MATCH    : 경기 프리뷰·경기 결과·하이라이트
			- CLUB     : 감독 선임/경질·구단주·경기장·재정·티켓·팬·구단 운영
			- OTHER    : 위 어디에도 분명히 들어가지 않는 것

			규칙:
			1. 제목에 적힌 것만 근거로 삼는다. 배경 지식으로 내용을 보충하지 않는다.
			2. 애매하면 OTHER 를 쓴다. 억지로 끼워 맞추지 않는다.
			3. 비유를 문자 그대로 읽지 않는다.
			   "beat City to a signing" 은 영입 경쟁(TRANSFER)이지 경기(MATCH)가 아니다.
			4. 갈래가 여럿에 걸치면 헤드라인의 주된 사안 하나만 고른다.
			   "새 영입 선수가 프리시즌 경기에 선발 출전" 은 그 경기가 주어이므로 MATCH.
			5. 받은 id 에 대해서만 답한다. 새 id 를 만들지 않는다.
			""";

	/**
	 * 응답 스키마.
	 *
	 * <p>{@code id} 를 {@code category} 보다 <b>앞에</b> 둔다. 모델은 스키마에 적힌 순서대로
	 * 필드를 생성하므로, 먼저 어느 기사인지 확정하고 그다음 갈래를 고르게 된다.
	 * ({@code AiDiagnosisService} 가 {@code evidence} 를 앞에 두는 것과 같은 이유이고,
	 * 그래서 여기도 {@link LinkedHashMap} 을 쓴다 — {@code Map.of()} 는 순서를 보장하지 않는다.)
	 */
	private Map<String, Object> schema() {
		Map<String, Object> itemProps = new LinkedHashMap<>();
		itemProps.put("id", Map.of("type", "integer"));
		itemProps.put("category", new LinkedHashMap<>(Map.of(
				"type", "string",
				"enum", List.of("TRANSFER", "SQUAD", "MATCH", "CLUB", "OTHER"))));

		Map<String, Object> itemSchema = new LinkedHashMap<>();
		itemSchema.put("type", "object");
		itemSchema.put("properties", itemProps);
		itemSchema.put("required", List.of("id", "category"));
		itemSchema.put("additionalProperties", false);

		Map<String, Object> rootProps = new LinkedHashMap<>();
		rootProps.put("items", new LinkedHashMap<>(Map.of(
				"type", "array",
				"items", itemSchema)));

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("type", "object");
		root.put("properties", rootProps);
		root.put("required", List.of("items"));
		root.put("additionalProperties", false);
		return root;
	}

	/** 테스트가 프롬프트 본문을 확인할 때. */
	static String systemPrompt() {
		return SYSTEM_PROMPT;
	}

	/** 배치 분할 결과를 테스트에서 확인할 때. */
	static List<List<NewsItem>> split(List<NewsItem> items, int size) {
		List<List<NewsItem>> batches = new ArrayList<>();
		for (int from = 0; from < items.size(); from += size) {
			batches.add(items.subList(from, Math.min(from + size, items.size())));
		}
		return batches;
	}
}
