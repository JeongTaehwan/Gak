package page.usetaehwan.gak.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * 클라이언트 IP 판별 — IP 단위 상한(DG 8절)의 식별 재료.
 * XFF 는 신뢰 프록시에서 온 것만 인정한다. 아니면 요청마다 헤더를 바꿔
 * IP 상한을 우회할 수 있다.
 */
class ClientIpResolverTest {

	private static ClientIpResolver resolver(String... trusted) {
		return new ClientIpResolver(new AiRateLimitProperties(
				true, 100, 10, trusted.length == 0 ? null : List.of(trusted)));
	}

	@Test
	@DisplayName("신뢰 프록시에서 온 요청은 XFF 첫 토큰(최초 클라이언트)을 쓴다")
	void trustsForwardedHeaderFromKnownProxies() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("127.0.0.1");
		request.addHeader("X-Forwarded-For", "203.0.113.7, 127.0.0.1");

		assertThat(resolver().resolve(request)).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("신뢰 목록 밖에서 온 XFF 는 위조로 간주하고 소켓 IP 로 센다")
	void ignoresForgedHeadersFromDirectCallers() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("198.51.100.9");
		// 직접 호출자가 매 요청 다른 값을 실어 보내는 상황.
		request.addHeader("X-Forwarded-For", "1.2.3.4");

		assertThat(resolver().resolve(request)).isEqualTo("198.51.100.9");
	}

	@Test
	@DisplayName("신뢰 프록시인데 XFF 가 없으면 식별 불가(null) — 모두를 프록시 IP 한 버킷에 묶지 않는다")
	void unidentifiableRequestsResolveToNull() {
		MockHttpServletRequest bare = new MockHttpServletRequest();
		bare.setRemoteAddr("127.0.0.1");
		assertThat(resolver().resolve(bare)).isNull();

		MockHttpServletRequest blank = new MockHttpServletRequest();
		blank.setRemoteAddr("127.0.0.1");
		blank.addHeader("X-Forwarded-For", "  ,  ");
		assertThat(resolver().resolve(blank)).isNull();
	}

	@Test
	@DisplayName("배포용 프록시 주소를 설정으로 추가할 수 있다")
	void acceptsConfiguredProxyAddresses() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("10.1.2.3");
		request.addHeader("X-Forwarded-For", "203.0.113.7");

		assertThat(resolver("10.1.2.3").resolve(request)).isEqualTo("203.0.113.7");
	}
}
