package page.usetaehwan.gak.external.rss;

import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 실제 HTTP 로 피드를 받아 온다.
 *
 * <h2>신원을 밝히고 요청한다</h2>
 * <p>User-Agent 에 우리가 누구인지 적는다. 브라우저인 척하지 않는다 —
 * <b>차단하고 싶은 쪽이 차단할 수 있어야</b> 우리가 정직하게 수집하고 있다고 말할 수 있다.
 * 위장한 UA 로 받아 온 데이터는 robots.txt 를 지켰다고 주장할 근거가 없다.
 *
 * <h2>느린 피드가 스케줄러를 붙잡지 않게</h2>
 * <p>타임아웃을 짧게 둔다. 뉴스는 늦게 들어와도 되는 데이터라, 한 피드를 오래 기다리느니
 * 이번 회차를 건너뛰고 다음 정각에 다시 받는 편이 낫다.
 */
public class RestRssFeedClient implements RssFeedClient {

	private static final Logger log = LoggerFactory.getLogger(RestRssFeedClient.class);

	/** 피드 하나가 이보다 크면 무언가 잘못된 것이다(본문을 통째로 싣는 피드 등). */
	private static final int MAX_BYTES = 8 * 1024 * 1024;

	private final RestClient restClient;

	public RestRssFeedClient(Duration connectTimeout, Duration readTimeout) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory();
		factory.setReadTimeout(readTimeout);
		this.restClient = RestClient.builder()
				.requestFactory((ClientHttpRequestFactory) factory)
				.build();
	}

	@Override
	public FetchResult fetch(String url, String userAgent) {
		byte[] body;
		try {
			body = restClient.get()
					.uri(url)
					.header("User-Agent", userAgent)
					.header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
					.retrieve()
					.body(byte[].class);
		} catch (Exception e) {
			// 4xx/5xx·타임아웃·DNS 실패가 전부 여기로 온다. 어느 쪽이든 대응은 같다 —
			// 이번엔 건너뛰고 다음 회차에 다시 본다.
			log.warn("피드를 받지 못했습니다({}): {}", url, e.toString());
			return FetchResult.failed(FetchResult.Failure.TRANSPORT);
		}

		if (body == null || body.length == 0) {
			log.warn("피드 응답이 비어 있습니다: {}", url);
			return FetchResult.failed(FetchResult.Failure.MALFORMED);
		}
		if (body.length > MAX_BYTES) {
			log.warn("피드가 지나치게 큽니다({} bytes): {}", body.length, url);
			return FetchResult.failed(FetchResult.Failure.MALFORMED);
		}

		List<RssItem> items = RssParser.parse(body);
		if (items.isEmpty()) {
			log.warn("피드에서 읽어 낸 항목이 없습니다: {}", url);
			return FetchResult.failed(FetchResult.Failure.MALFORMED);
		}
		return FetchResult.ok(items);
	}
}
