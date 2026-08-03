package page.usetaehwan.gak.service.news;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
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
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.domain.SourceTier;
import page.usetaehwan.gak.external.rss.RssFeedClient;
import page.usetaehwan.gak.external.rss.RssParser;
import page.usetaehwan.gak.repository.NewsItemRepository;
import page.usetaehwan.gak.support.DatabaseCleaner;

/**
 * 수집 파이프라인 전체를 <b>저장해 둔 실제 피드 원문</b>으로 태운다.
 *
 * <p>네트워크를 타지 않는다 — {@link StubFeedClient} 가 {@code raw-*.xml} 을 돌려준다
 * (동기화 테스트가 replay 파일을 쓰는 것과 같은 방식).
 */
@SpringBootTest
@Import({DatabaseCleaner.class, NewsIngestServiceTest.StubFeedConfig.class})
@ActiveProfiles("test")
class NewsIngestServiceTest {

	private static final long MAN_UTD = 33L;

	@Autowired DatabaseCleaner databaseCleaner;
	@Autowired NewsIngestService ingestService;
	@Autowired NewsQueryService queryService;
	@Autowired NewsItemRepository repository;

	@BeforeEach
	void reset() {
		databaseCleaner.clearAllButCompetitions();
	}

	@Test
	@DisplayName("켜진 소스 3개에서 맨유 1군 기사만 들어온다")
	void ingestsOnlyTargetTeamNews() {
		NewsIngestService.IngestReport report = ingestService.ingest();

		// 꺼 둔 Sky 는 아예 요청되지 않는다.
		assertThat(report.sources().keySet())
				.containsExactlyInAnyOrder("bbc-football", "guardian-manutd", "men-manutd");

		List<NewsItem> stored = repository.findAll();
		assertThat(stored).isNotEmpty();
		assertThat(stored).allSatisfy(item -> {
			assertThat(item.getTeamId()).isEqualTo(MAN_UTD);
			assertThat(item.getTier()).isEqualTo(SourceTier.MEDIA);
			// 태거가 꺼져 있으므로 갈래는 전부 비어 있다 — 그래도 소식은 멀쩡히 저장된다.
			assertThat(item.getCategory()).isNull();
		});

		// 정답 라벨 기준 맨유 1군 기사는 BBC 0 + Guardian 16 + MEN 20 = 36건,
		// 그중 게이트가 놓치는 1건(제목에 구단명 없음)을 빼면 35건.
		assertThat(stored).hasSize(35);
	}

	@Test
	@DisplayName("BBC 피드 안의 중복이 걸러진다 — 같은 기사가 두 번 실려 온다")
	void dropsDuplicatesWithinAFeed() {
		ingestService.ingest();

		List<String> keys = repository.findAll().stream().map(NewsItem::getDedupKey).toList();
		assertThat(keys).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("두 번 돌려도 늘지 않는다 — 멱등")
	void ingestIsIdempotent() {
		int first = ingestService.ingest().stored();
		int second = ingestService.ingest().stored();

		assertThat(first).isPositive();
		assertThat(second).as("두 번째 회차엔 새로 저장할 게 없다").isZero();
		assertThat(repository.count()).isEqualTo(first);
	}

	@Test
	@DisplayName("꺼진 소스는 조회에서도 빠진다 — 수집만 멈추는 건 스위치를 내린 게 아니다")
	void disabledSourcesDisappearFromQueries() {
		ingestService.ingest();
		List<NewsItem> visible = queryService.forTeam(MAN_UTD, null);

		assertThat(visible).isNotEmpty();
		assertThat(visible).extracting(NewsItem::getSourceKey)
				.doesNotContain("sky-football");
	}

	@Test
	@DisplayName("최신순으로 내려준다")
	void ordersByPublishedDesc() {
		ingestService.ingest();
		List<NewsItem> visible = queryService.forTeam(MAN_UTD, null);

		assertThat(visible).isSortedAccordingTo(
				(a, b) -> b.getPublishedAt().compareTo(a.getPublishedAt()));
	}

	@Test
	@DisplayName("본문·요약을 저장하지 않는다 — 엔티티에 담을 자리가 없다")
	void storesNoArticleBody() {
		ingestService.ingest();
		NewsItem any = repository.findAll().get(0);

		assertThat(any.getTitle()).isNotBlank();
		assertThat(any.getLink()).startsWith("http");
		// 링크에서 추적 파라미터도 떼고 저장한다.
		assertThat(any.getLink()).doesNotContain("at_medium").doesNotContain("utm_");
	}

	@Test
	@DisplayName("다른 팀을 물어보면 빈 목록 — 오류가 아니다")
	void unknownTeamReturnsEmpty() {
		ingestService.ingest();
		assertThat(queryService.forTeam(50L, null)).isEmpty();
	}

	// ---------------------------------------------------------------- 스텁

	@TestConfiguration
	static class StubFeedConfig {
		@Bean
		@Primary
		RssFeedClient stubFeedClient() {
			return new StubFeedClient();
		}
	}

	/** URL 로 저장해 둔 원문을 골라 돌려준다. 네트워크를 타지 않는다. */
	static class StubFeedClient implements RssFeedClient {

		private static final Map<String, String> FIXTURES = Map.of(
				"feeds.bbci.co.uk", "raw-bbc-football.xml",
				"theguardian.com", "raw-guardian-manutd.xml",
				"manchestereveningnews.co.uk", "raw-men-manutd.xml",
				"skysports.com", "raw-sky-football.xml");

		@Override
		public FetchResult fetch(String url, String userAgent) {
			// 신원을 밝히지 않고 부르는 일이 없어야 한다.
			if (userAgent == null || userAgent.isBlank()) {
				return FetchResult.failed(FetchResult.Failure.TRANSPORT);
			}
			return FIXTURES.entrySet().stream()
					.filter(entry -> url.contains(entry.getKey()))
					.findFirst()
					.map(entry -> FetchResult.ok(RssParser.parse(read(entry.getValue()))))
					.orElseGet(() -> FetchResult.failed(FetchResult.Failure.TRANSPORT));
		}

		private static byte[] read(String name) {
			try (InputStream in = StubFeedClient.class.getResourceAsStream("/news/" + name)) {
				if (in == null) {
					throw new IllegalStateException("표본 없음: " + name);
				}
				return in.readAllBytes();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}
}
