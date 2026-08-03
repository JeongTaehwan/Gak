package page.usetaehwan.gak.external.rss;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 파서를 <b>실제로 받은 피드 원문</b>으로 검증한다
 * ({@code src/test/resources/news/raw-*.xml}).
 */
class RssParserTest {

	private static byte[] fixture(String name) throws IOException {
		try (InputStream in = RssParserTest.class.getResourceAsStream("/news/" + name)) {
			assertThat(in).as(name + " 이 있어야 한다").isNotNull();
			return in.readAllBytes();
		}
	}

	@Test
	@DisplayName("BBC 피드에서 72건을 읽는다")
	void parsesBbc() throws IOException {
		List<RssItem> items = RssParser.parse(fixture("raw-bbc-football.xml"));
		assertThat(items).hasSize(72);
		assertThat(items).allSatisfy(item -> {
			assertThat(item.title()).isNotBlank();
			assertThat(item.link()).startsWith("http");
			assertThat(item.publishedAt()).isNotNull();
		});
	}

	@Test
	@DisplayName("Guardian·MEN·Sky 도 읽는다")
	void parsesOtherFeeds() throws IOException {
		assertThat(RssParser.parse(fixture("raw-guardian-manutd.xml"))).hasSize(20);
		assertThat(RssParser.parse(fixture("raw-men-manutd.xml"))).hasSize(25);
		assertThat(RssParser.parse(fixture("raw-sky-football.xml"))).hasSize(20);
	}

	@Test
	@DisplayName("BST 같은 약어 시간대를 읽는다 — 이걸 못 읽어 Sky 20건이 통째로 사라졌었다")
	void parsesAbbreviatedTimezones() {
		// Sky 는 숫자 오프셋 대신 "BST" 를 보낸다. RFC_1123 은 이걸 모른다.
		String xml = """
				<rss version="2.0"><channel>
				  <item>
				    <title>Sky style date</title>
				    <link>https://example.com/a</link>
				    <pubDate>Mon, 03 Aug 2026 13:45:00 BST</pubDate>
				  </item>
				</channel></rss>
				""";
		List<RssItem> items = RssParser.parse(xml);
		assertThat(items).hasSize(1);
		// BST 는 +01:00 이므로 UTC 로는 12:45.
		assertThat(items.get(0).publishedAt())
				.isEqualTo(Instant.parse("2026-08-03T12:45:00Z"));
	}

	@Test
	@DisplayName("모호한 약어는 추측하지 않는다 — 잘못 고르면 몇 시간씩 어긋난다")
	void doesNotGuessAmbiguousTimezones() {
		// IST 는 아일랜드(+01)·인도(+05:30)·이스라엘(+02) 셋 다다. 고르지 않고 버린다.
		String xml = """
				<rss version="2.0"><channel>
				  <item>
				    <title>Ambiguous zone</title>
				    <link>https://example.com/a</link>
				    <pubDate>Mon, 03 Aug 2026 13:45:00 IST</pubDate>
				  </item>
				</channel></rss>
				""";
		assertThat(RssParser.parse(xml)).isEmpty();
	}

	@Test
	@DisplayName("본문·요약을 읽어 오지 않는다 — RssItem 에 담을 자리가 없다")
	void neverCarriesArticleBody() throws IOException {
		// 이건 구현 세부가 아니라 설계 제약이다. 필드가 없으면 나중에 누가
		// "이미 받아 왔으니 써 볼까" 할 수 없다.
		assertThat(RssItem.class.getRecordComponents())
				.extracting(java.lang.reflect.RecordComponent::getName)
				.containsExactlyInAnyOrder("title", "link", "publishedAt");

		// 원문에는 description 이 분명히 들어 있다 — 우리가 안 읽을 뿐이다.
		String raw = new String(fixture("raw-men-manutd.xml"), StandardCharsets.UTF_8);
		assertThat(raw).contains("<description>");
	}

	@Test
	@DisplayName("제목의 HTML 태그와 개행을 정리한다 — 글자는 바꾸지 않는다")
	void cleansTitleMarkup() {
		String xml = """
				<rss version="2.0"><channel>
				  <item>
				    <title>Man Utd &lt;b&gt;sign&lt;/b&gt;
				      Sesko</title>
				    <link>https://example.com/a</link>
				    <pubDate>Mon, 03 Aug 2026 12:47:00 +0000</pubDate>
				  </item>
				</channel></rss>
				""";
		List<RssItem> items = RssParser.parse(xml);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).title()).isEqualTo("Man Utd sign Sesko");
	}

	@Test
	@DisplayName("발행 시각을 못 읽으면 그 줄을 버린다 — now 로 채우지 않는다")
	void dropsItemsWithoutDate() {
		String xml = """
				<rss version="2.0"><channel>
				  <item>
				    <title>No date here</title>
				    <link>https://example.com/a</link>
				  </item>
				  <item>
				    <title>Has a date</title>
				    <link>https://example.com/b</link>
				    <pubDate>Mon, 03 Aug 2026 12:47:00 +0000</pubDate>
				  </item>
				</channel></rss>
				""";
		List<RssItem> items = RssParser.parse(xml);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).title()).isEqualTo("Has a date");
		assertThat(items.get(0).publishedAt())
				.isEqualTo(Instant.parse("2026-08-03T12:47:00Z"));
	}

	@Test
	@DisplayName("Atom 피드도 읽는다")
	void parsesAtom() {
		String xml = """
				<feed xmlns="http://www.w3.org/2005/Atom">
				  <entry>
				    <title>Atom headline</title>
				    <link rel="alternate" href="https://example.com/atom"/>
				    <published>2026-08-03T12:47:00Z</published>
				  </entry>
				</feed>
				""";
		List<RssItem> items = RssParser.parse(xml);
		assertThat(items).hasSize(1);
		assertThat(items.get(0).link()).isEqualTo("https://example.com/atom");
	}

	@Test
	@DisplayName("외부 엔티티(XXE)를 처리하지 않는다 — 피드는 우리가 통제하지 않는 XML 이다")
	void rejectsExternalEntities() {
		String xml = """
				<?xml version="1.0"?>
				<!DOCTYPE rss [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
				<rss version="2.0"><channel>
				  <item>
				    <title>&xxe;</title>
				    <link>https://example.com/a</link>
				    <pubDate>Mon, 03 Aug 2026 12:47:00 +0000</pubDate>
				  </item>
				</channel></rss>
				""";
		// DOCTYPE 자체를 거부하므로 파싱이 실패하고 빈 목록이 나온다.
		// 파일 내용이 제목에 실려 나오는 일이 없어야 한다.
		assertThat(RssParser.parse(xml)).isEmpty();
	}

	@Test
	@DisplayName("깨진 입력에 예외를 던지지 않는다 — 피드 하나가 수집 전체를 멈추지 않는다")
	void survivesGarbage() {
		assertThat(RssParser.parse("not xml at all")).isEmpty();
		assertThat(RssParser.parse(new byte[0])).isEmpty();
		assertThat(RssParser.parse((byte[]) null)).isEmpty();
	}
}
