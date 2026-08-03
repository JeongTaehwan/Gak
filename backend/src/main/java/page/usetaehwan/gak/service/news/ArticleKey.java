package page.usetaehwan.gak.service.news;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 같은 기사인지 판단하는 키를 만든다. <b>분류보다 먼저 도는 단계다.</b>
 *
 * <h2>왜 guid 를 안 쓰나 — 실측</h2>
 * <p>RSS 규격상 {@code guid}가 기사의 고유 식별자다. 그런데 실제 응답 137건을 받아 보니
 * 둘 다 깨져 있었다.
 *
 * <pre>
 * BBC  같은 기사가 한 피드에 두 번 실리는데 guid 가 다르다:
 *        .../articles/cdewr8w0r80o#0
 *        .../articles/cdewr8w0r80o#14      ← 피드 내 위치가 붙는다
 *      link 는 두 줄 다 완전히 같다.
 *
 * Sky  guid 를 아예 주지 않는다 (null).
 * </pre>
 *
 * <p>그래서 <b>링크를 정규화한 값</b>을 키로 쓴다. 위 두 경우를 모두 처리한다.
 *
 * <h2>정규화 규칙</h2>
 * <ol>
 *   <li>스킴·호스트를 소문자로 (같은 URL을 대소문자만 바꿔 보내는 발행자가 있다)</li>
 *   <li>{@code #fragment} 제거 — BBC 문제가 여기서 풀린다</li>
 *   <li>추적 파라미터 제거 ({@code at_medium}, {@code utm_*} …) — BBC 링크에
 *       {@code ?at_medium=RSS&at_campaign=rss}가 늘 붙어 온다</li>
 *   <li>끝의 {@code /} 제거</li>
 * </ol>
 *
 * <h2>여기서 멈춘다 — 교차 매체 병합은 하지 않는다</h2>
 * <p>"같은 사안을 여러 매체가 다루는 경우"는 <b>일부러 합치지 않는다.</b> 실제 데이터로
 * 제목 유사도(자카드)를 재 봤더니 임계값을 어디에 두든 깨졌다.
 *
 * <pre>
 * 0.50  FA set to withdraw support for Fifa president Infantino          (BBC)
 *       FA set to follow Welsh FA and withdraw support for Infantino     (Sky)   ← 같은 사안 ✅
 *
 * 0.30  Wales first home nation to withdraw support for Infantino        (BBC)
 *       FA set to follow Welsh FA and withdraw support for Infantino     (Sky)   ← 다른 사안 ❌
 *                                                                 웨일스 FA 와 잉글랜드 FA 는 다른 조직이다
 *
 * 0.16  Tottenham win race to sign West Ham's Mateus Fernandes           (Guardian)
 *       Mateus Fernandes drops real reason he snubbed Man United         (MEN)   ← 같은 사안인데 못 잡는다
 * </pre>
 *
 * <p>임계값을 0.30으로 낮추면 서로 다른 두 사안이 하나로 접히고, 0.16까지 낮추면 무엇이든
 * 접힌다. <b>이건 좌표 매칭에서 이미 내린 결론과 같은 결론이다</b> —
 * {@code SeedCatalog} 주석: "억지 유사도 매칭은 하지 않는다. Manchester-by-the-Sea 를
 * Manchester 로 접으면 엉뚱한 좌표로 이동거리를 재는데, 그건 좌표가 없는 것보다 나쁘다."
 *
 * <p>그리고 더 근본적으로: <b>"이 둘은 같은 사안이다"는 우리의 주장이다.</b> 옮기기만
 * 하기로 한 층에서 할 말이 아니다. 두 매체가 같은 걸 다뤘다는 사실 자체가 정보이므로,
 * 둘 다 출처를 달아 보여 준다.
 */
public final class ArticleKey {

	/**
	 * 지워도 되는 파라미터. <b>화이트리스트가 아니라 블랙리스트인 게 중요하다</b> —
	 * 모르는 파라미터는 기사를 가리키는 데 필요한 값일 수 있으므로 남긴다
	 * (예 {@code ?id=123}, {@code ?page=2}).
	 */
	private static final Set<String> TRACKING_PARAMS = Set.of(
			"at_medium", "at_campaign", "at_campaign_type", "at_custom1", "at_custom2",
			"at_custom3", "at_custom4", "at_bbc_team", "at_link_id", "at_link_type",
			"at_link_origin", "at_ptr_name", "at_format",
			"utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "utm_id",
			"ito", "cmp", "ref", "src", "fbclid", "gclid", "mc_cid", "mc_eid",
			"service", "spref", "__twitter_impression", "s");

	private ArticleKey() {
	}

	/**
	 * 중복 제거 키를 만든다.
	 *
	 * <p>URL로 파싱되지 않으면 원문 문자열을 소문자로 접어 그대로 쓴다. 깨진 링크라도
	 * 같은 값이면 같은 기사로 볼 수 있고, 아무 키도 못 만들어 저장을 포기하는 것보다 낫다.
	 *
	 * @param link RSS 가 준 {@code <link>}. null/공백이면 null 을 돌려준다(호출부가 건너뛴다)
	 */
	public static String of(String link) {
		if (link == null || link.isBlank()) {
			return null;
		}
		String raw = link.trim();
		try {
			URI uri = URI.create(raw);
			if (uri.getHost() == null) {
				return fold(raw);
			}
			String scheme = uri.getScheme() == null
					? "https" : uri.getScheme().toLowerCase(Locale.ROOT);
			String host = uri.getHost().toLowerCase(Locale.ROOT);
			if (host.startsWith("www.")) {
				host = host.substring(4);
			}
			String path = uri.getRawPath() == null ? "" : uri.getRawPath();
			while (path.length() > 1 && path.endsWith("/")) {
				path = path.substring(0, path.length() - 1);
			}
			String query = keepMeaningfulQuery(uri.getRawQuery());

			// 포트는 기본값이면 버린다. 남기면 같은 기사가 :443 유무로 갈린다.
			String port = (uri.getPort() == -1 || isDefaultPort(scheme, uri.getPort()))
					? "" : ":" + uri.getPort();

			return scheme + "://" + host + port + path + (query.isEmpty() ? "" : "?" + query);
		} catch (IllegalArgumentException e) {
			return fold(raw);
		}
	}

	private static boolean isDefaultPort(String scheme, int port) {
		return ("https".equals(scheme) && port == 443) || ("http".equals(scheme) && port == 80);
	}

	/**
	 * 추적 파라미터를 걷어내고 남은 것을 정렬해 돌려준다.
	 * 정렬하는 이유는 같은 파라미터가 순서만 다르게 오면 다른 키가 되기 때문이다.
	 */
	private static String keepMeaningfulQuery(String rawQuery) {
		if (rawQuery == null || rawQuery.isBlank()) {
			return "";
		}
		List<String> kept = Arrays.stream(rawQuery.split("&"))
				.filter(pair -> !pair.isBlank())
				.filter(pair -> {
					int eq = pair.indexOf('=');
					String name = (eq < 0 ? pair : pair.substring(0, eq)).toLowerCase(Locale.ROOT);
					return !TRACKING_PARAMS.contains(name);
				})
				.sorted()
				.toList();
		return String.join("&", kept);
	}

	private static String fold(String raw) {
		String lower = raw.toLowerCase(Locale.ROOT);
		int hash = lower.indexOf('#');
		return hash < 0 ? lower : lower.substring(0, hash);
	}

	/** DB 컬럼 길이를 넘지 않게 자른다. 지나치게 긴 URL은 앞부분만으로도 충분히 구분된다. */
	public static String truncate(String key, int max) {
		if (key == null) {
			return null;
		}
		return key.length() <= max ? key : key.substring(0, max);
	}

	/** 표시용으로도 링크를 정리해 둔다 — 추적 파라미터를 굳이 사용자에게 넘길 이유가 없다. */
	public static String cleanLink(String link) {
		String key = of(link);
		return key == null ? link : key;
	}

	/** 디버깅·테스트에서 두 링크가 같은 기사인지 볼 때. */
	public static boolean sameArticle(String a, String b) {
		String ka = of(a);
		String kb = of(b);
		return ka != null && ka.equals(kb);
	}

	/** 여러 링크를 한 번에 정규화(테스트 편의). */
	public static List<String> allOf(List<String> links) {
		return links.stream().map(ArticleKey::of).collect(Collectors.toList());
	}
}
