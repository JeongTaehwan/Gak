package page.usetaehwan.gak.service.news;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 중복 제거 키.
 *
 * <p>여기 있는 예시는 전부 <b>실제로 받은 응답</b>에서 가져왔다. 만들어 낸 케이스가 아니다.
 */
class ArticleKeyTest {

	@Test
	@DisplayName("BBC — 같은 기사를 한 피드에 두 번 싣는다. guid 는 다르고 link 는 같다")
	void bbcDuplicatesShareTheSameLink() {
		// 실측: 피드 안 #01 과 #53 이 같은 기사인데 guid 가
		//   .../cddjm88m4vno#0   과   .../cddjm88m4vno#14  로 다르다.
		// guid 를 키로 쓰면 이 둘이 다른 기사가 되어 두 번 저장된다.
		String guidA = "https://www.bbc.co.uk/sport/football/articles/cddjm88m4vno#0";
		String guidB = "https://www.bbc.co.uk/sport/football/articles/cddjm88m4vno#14";
		assertThat(guidA).isNotEqualTo(guidB);
		assertThat(ArticleKey.sameArticle(guidA, guidB))
				.as("프래그먼트를 떼면 같은 기사로 보여야 한다")
				.isTrue();

		// link 는 두 줄 다 완전히 같다 — 그래서 link 를 키로 쓴다.
		String link = "https://www.bbc.co.uk/sport/football/articles/cddjm88m4vno?at_medium=RSS&at_campaign=rss";
		assertThat(ArticleKey.of(link))
				.isEqualTo("https://bbc.co.uk/sport/football/articles/cddjm88m4vno");
	}

	@Test
	@DisplayName("추적 파라미터를 뗀다 — 붙어 오는 값이 달라도 같은 기사다")
	void stripsTrackingParameters() {
		assertThat(ArticleKey.sameArticle(
				"https://example.com/a?utm_source=rss&utm_campaign=x",
				"https://example.com/a?utm_source=twitter")).isTrue();

		assertThat(ArticleKey.sameArticle(
				"https://www.manchestereveningnews.co.uk/sport/x?service=rss",
				"https://www.manchestereveningnews.co.uk/sport/x")).isTrue();
	}

	@Test
	@DisplayName("의미 있는 파라미터는 남긴다 — 모르는 건 지우지 않는다")
	void keepsMeaningfulParameters() {
		// ?id=123 은 기사를 가리키는 값일 수 있다. 화이트리스트로 지웠다면 두 기사가 하나가 된다.
		assertThat(ArticleKey.sameArticle(
				"https://example.com/read?id=1",
				"https://example.com/read?id=2")).isFalse();

		// 순서만 다른 건 같은 것으로 본다.
		assertThat(ArticleKey.sameArticle(
				"https://example.com/read?a=1&b=2",
				"https://example.com/read?b=2&a=1")).isTrue();
	}

	@Test
	@DisplayName("호스트 대소문자·www·끝 슬래시·기본 포트를 정규화한다")
	void normalisesHostAndPath() {
		assertThat(ArticleKey.sameArticle(
				"https://WWW.Example.com/a/", "https://example.com/a")).isTrue();
		assertThat(ArticleKey.sameArticle(
				"https://example.com:443/a", "https://example.com/a")).isTrue();
	}

	@Test
	@DisplayName("경로가 다르면 다른 기사다 — 억지로 접지 않는다")
	void differentPathsStayDifferent() {
		assertThat(ArticleKey.sameArticle(
				"https://example.com/a", "https://example.com/b")).isFalse();
		// 서브도메인이 다르면 다른 사이트다.
		assertThat(ArticleKey.sameArticle(
				"https://a.example.com/x", "https://b.example.com/x")).isFalse();
	}

	@Test
	@DisplayName("URL 로 안 읽히면 문자열로라도 접는다 — 키를 못 만들어 버리지는 않는다")
	void fallsBackForBrokenLinks() {
		assertThat(ArticleKey.of("not a url at all")).isNotNull();
		assertThat(ArticleKey.sameArticle("Not A URL#1", "not a url#2")).isTrue();
	}

	@Test
	@DisplayName("빈 링크는 키가 없다 — 호출부가 그 항목을 버린다")
	void blankLinkHasNoKey() {
		assertThat(ArticleKey.of(null)).isNull();
		assertThat(ArticleKey.of("  ")).isNull();
	}

	@Test
	@DisplayName("교차 매체 병합은 하지 않는다 — 다른 매체의 같은 사안은 따로 남는다")
	void doesNotMergeAcrossPublishers() {
		// 같은 사안을 BBC 와 Sky 가 각자 쓴 실제 사례.
		// 제목 유사도로 접으려 하면 '웨일스 FA' 와 '잉글랜드 FA' 같은 다른 사안까지 접힌다.
		// 그래서 URL 이 다르면 다른 항목으로 둔다 — 두 매체가 다뤘다는 사실 자체가 정보다.
		assertThat(ArticleKey.sameArticle(
				"https://bbc.co.uk/sport/football/articles/abc",
				"https://skysports.com/football/news/12345")).isFalse();
	}
}
