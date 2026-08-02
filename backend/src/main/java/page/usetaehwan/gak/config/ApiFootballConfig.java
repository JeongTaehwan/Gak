package page.usetaehwan.gak.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 외부 API·동기화 관련 설정 빈.
 *
 * <p>{@link RestClient}는 {@code mode=real} 일 때만 만든다. 재생 모드에서는
 * 키가 없어도 앱이 떠야 하고, 실 호출 통로 자체가 존재하지 않는 편이 안전하다.
 */
@Configuration
@EnableConfigurationProperties({ApiFootballProperties.class, SyncProperties.class})
public class ApiFootballConfig {

	@Bean
	@ConditionalOnProperty(prefix = "gak.api-football", name = "mode", havingValue = "real")
	public RestClient apiFootballRestClient(ApiFootballProperties properties) {
		if (properties.key() == null || properties.key().isBlank()) {
			throw new IllegalStateException(
					"gak.api-football.mode=real 인데 API_FOOTBALL_KEY 가 비어 있습니다.");
		}

		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		// 타임아웃이 없으면 응답 없는 커넥션 하나가 스케줄러 스레드를 무한정 붙잡는다.
		requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
		requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

		return RestClient.builder()
				.baseUrl(properties.baseUrl())
				.requestFactory(requestFactory)
				.defaultHeader("x-apisports-key", properties.key())
				.defaultHeader("Accept", "application/json")
				.build();
	}
}
