package page.usetaehwan.gak.service.news;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.config.NewsProperties;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.external.rss.RssFeedClient;
import page.usetaehwan.gak.external.rss.RssItem;

/**
 * 수집 — 피드를 받아 <b>중복 제거 → 게이트 → 저장</b> 순으로 흘린다.
 *
 * <h2>순서가 설계다</h2>
 * <pre>
 *   ① 중복 제거   같은 기사를 두 번 저장하지 않는다 (분류 이전)
 *   ② 게이트      우리 팀 기사인가 — 키워드, 결정론적
 *   ③ 저장        제목·링크·발행시각·출처. 본문 없음
 *   ④ 갈래 태깅   ← 별도 단계. LLM 은 여기서만 등장한다 (NewsTaggingService)
 * </pre>
 *
 * <p>③까지 끝나면 화면에 필요한 건 다 있다. ④는 <b>덧칠</b>이라 실패해도 소식은 그대로
 * 뜬다({@code /diagnosis} 의 점진적 향상과 같은 구조).
 *
 * <h2>HTTP 를 트랜잭션 안에서 하지 않는다</h2>
 * <p>피드 4개를 받는 데 수 초가 걸린다. 그 시간 동안 DB 커넥션을 붙잡고 있으면 커넥션
 * 풀이 마른다. 받는 건 트랜잭션 밖에서 하고, 저장만 짧게 연다.
 */
@Service
public class NewsIngestService {

	private static final Logger log = LoggerFactory.getLogger(NewsIngestService.class);

	/** DB 컬럼 길이에 맞춘 상한. 넘으면 자른다. */
	private static final int MAX_KEY = 500;
	private static final int MAX_TITLE = 500;
	private static final int MAX_LINK = 1000;

	private final NewsProperties properties;
	private final RssFeedClient feedClient;
	private final TeamNewsMatcher matcher;
	private final NewsItemWriter writer;
	private final Clock clock;

	public NewsIngestService(NewsProperties properties,
	                         RssFeedClient feedClient,
	                         TeamNewsMatcher matcher,
	                         NewsItemWriter writer,
	                         Clock clock) {
		this.properties = properties;
		this.feedClient = feedClient;
		this.matcher = matcher;
		this.writer = writer;
		this.clock = clock;
	}

	/**
	 * 켜져 있는 소스를 전부 한 번씩 받아 새 소식을 저장한다.
	 *
	 * @return 무엇이 몇 건 들어왔고 어디서 걸러졌는지
	 */
	public IngestReport ingest() {
		List<NewsProperties.Source> sources = properties.enabledSources();
		if (sources.isEmpty()) {
			log.info("켜져 있는 뉴스 소스가 없습니다 — 수집을 건너뜁니다.");
			return IngestReport.empty();
		}
		if (matcher.disabled()) {
			log.warn("뉴스 별칭 시드가 비어 게이트가 아무것도 통과시키지 못합니다 — 수집을 건너뜁니다.");
			return IngestReport.empty();
		}

		Instant now = clock.instant();
		List<Candidate> candidates = new ArrayList<>();
		Map<String, SourceReport> perSource = new LinkedHashMap<>();

		for (NewsProperties.Source source : sources) {
			SourceReport report = collect(source, candidates, now);
			perSource.put(source.key(), report);
		}

		int stored = writer.storeNew(candidates);
		log.info("뉴스 수집 완료: 소스 {}개, 후보 {}건, 신규 저장 {}건",
				sources.size(), candidates.size(), stored);
		return new IngestReport(perSource, stored);
	}

	/** 소스 하나를 받아 후보 목록에 담는다. 실패해도 다음 소스로 넘어간다. */
	private SourceReport collect(NewsProperties.Source source,
	                             List<Candidate> candidates,
	                             Instant now) {
		respectCrawlDelay(source);

		RssFeedClient.FetchResult result = feedClient.fetch(source.url(), properties.userAgent());
		if (!result.succeeded()) {
			return SourceReport.failed(result.failure().name());
		}

		int fetched = result.items().size();
		int duplicate = 0;
		int rejected = 0;
		// 이 피드 안에서의 중복. BBC 는 같은 기사를 한 피드에 두 번 싣는다(실측 5종 10건).
		Set<String> seenInFeed = new LinkedHashSet<>();

		for (RssItem item : result.items()) {
			String key = ArticleKey.truncate(ArticleKey.of(item.link()), MAX_KEY);
			if (key == null) {
				rejected++;
				continue;
			}
			if (!seenInFeed.add(key)) {
				duplicate++;
				continue;
			}
			// 게이트 — 우리 팀 기사가 아니면 여기서 끝난다. LLM 은 이 항목을 보지 못한다.
			Optional<Long> teamId = matcher.match(item.title(), source.teamId());
			if (teamId.isEmpty()) {
				rejected++;
				continue;
			}
			candidates.add(new Candidate(source, key, item, teamId.get(), now));
		}

		int accepted = fetched - duplicate - rejected;
		log.debug("[{}] 받음 {} · 피드 내 중복 {} · 게이트 탈락 {} · 통과 {}",
				source.key(), fetched, duplicate, rejected, accepted);
		return new SourceReport(true, null, fetched, duplicate, rejected, accepted);
	}

	/**
	 * robots.txt 가 요구하는 간격을 지킨다.
	 *
	 * <p>Manchester Evening News 가 {@code Crawl-delay: 10} 을 요구한다. 지금은 소스당
	 * 한 시간에 한 번만 부르므로 실질적으로 걸릴 일이 없지만, <b>한 매체에서 피드를 여러 개
	 * 받게 되는 날</b>을 위해 통로를 미리 둔다. 그날 이걸 잊는 게 흔한 사고다.
	 */
	private void respectCrawlDelay(NewsProperties.Source source) {
		if (source.crawlDelay() == null || source.crawlDelay().isZero()
				|| source.crawlDelay().isNegative()) {
			return;
		}
		try {
			Thread.sleep(source.crawlDelay().toMillis());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 오래된 소식을 지운다.
	 *
	 * <p>뉴스는 이력이 아니라 "지금 무슨 일이 있나"다. 3년 전 이적설을 계속 들고 있을
	 * 이유가 없고, 남겨 두면 우리가 재배포하는 분량만 늘어난다.
	 */
	public int purgeExpired() {
		Instant cutoff = clock.instant().minus(properties.retention());
		int deleted = writer.deletePublishedBefore(cutoff);
		if (deleted > 0) {
			log.info("보관 기간({})이 지난 소식 {}건을 지웠습니다.", properties.retention(), deleted);
		}
		return deleted;
	}

	/** 수집 후보 — 아직 엔티티가 아니다. */
	record Candidate(NewsProperties.Source source, String dedupKey, RssItem item,
	                 Long teamId, Instant fetchedAt) {

		NewsItem toEntity() {
			return NewsItem.builder()
					.dedupKey(dedupKey)
					.sourceKey(source.key())
					.sourceName(source.name())
					.tier(source.tier())
					.title(truncate(item.title(), MAX_TITLE))
					.link(truncate(ArticleKey.cleanLink(item.link()), MAX_LINK))
					.publishedAt(item.publishedAt())
					.teamId(teamId)
					.fetchedAt(fetchedAt)
					.build();
		}

		private static String truncate(String value, int max) {
			if (value == null) {
				return null;
			}
			return value.length() <= max ? value : value.substring(0, max);
		}
	}

	/**
	 * @param sources 소스별 결과
	 * @param stored  실제로 새로 저장된 건수
	 */
	public record IngestReport(Map<String, SourceReport> sources, int stored) {
		public static IngestReport empty() {
			return new IngestReport(Map.of(), 0);
		}
	}

	/**
	 * @param succeeded 피드를 받았는가
	 * @param failure   실패 사유(성공이면 null)
	 * @param fetched   피드가 준 항목 수
	 * @param duplicate 피드 안에서 중복이라 버린 수
	 * @param rejected  게이트에서 탈락한 수(다른 팀·여자팀·전 소속 선수 등)
	 * @param accepted  게이트를 통과한 수. 이 중 이미 저장된 건 다시 저장되지 않는다
	 */
	public record SourceReport(boolean succeeded, String failure,
	                           int fetched, int duplicate, int rejected, int accepted) {
		static SourceReport failed(String failure) {
			return new SourceReport(false, failure, 0, 0, 0, 0);
		}
	}
}
