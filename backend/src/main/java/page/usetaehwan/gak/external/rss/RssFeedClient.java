package page.usetaehwan.gak.external.rss;

import java.util.List;

/**
 * RSS 피드를 받아 오는 통로.
 *
 * <p>인터페이스로 갈라 둔 이유는 {@code AnthropicClient}와 같다 — 테스트가 네트워크를
 * 타면 안 되고, 그 판단이 서비스 로직에 if 로 흩어지면 안 된다. 테스트는 저장해 둔
 * 응답({@code src/test/resources/news/raw-*.xml})을 돌려주는 구현으로 갈아 끼운다.
 */
public interface RssFeedClient {

	/**
	 * 피드 하나를 받아 파싱한다.
	 *
	 * <p><b>실패를 예외로 던지지 않는다.</b> 피드가 죽어 있는 건 이 앱에서 예외 상황이
	 * 아니라 예상된 분기다 — 매체 쪽 장애, 일시적 429, URL 변경이 전부 여기 해당하고,
	 * 그때 다른 소스는 계속 돌아야 한다.
	 *
	 * @param url        피드 주소
	 * @param userAgent  우리 신원. 밝히고 요청한다
	 */
	FetchResult fetch(String url, String userAgent);

	/**
	 * @param items   읽어 낸 항목들. 실패면 빈 목록
	 * @param failure 실패 사유. 성공이면 null
	 */
	record FetchResult(List<RssItem> items, Failure failure) {

		public enum Failure {
			/** 네트워크·타임아웃·HTTP 오류 */
			TRANSPORT,
			/** 응답은 왔는데 XML 로 읽히지 않거나 항목이 하나도 없다 */
			MALFORMED
		}

		public static FetchResult ok(List<RssItem> items) {
			return new FetchResult(List.copyOf(items), null);
		}

		public static FetchResult failed(Failure failure) {
			return new FetchResult(List.of(), failure);
		}

		public boolean succeeded() {
			return failure == null;
		}
	}
}
