package page.usetaehwan.gak.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 웹 계층 공통 설정 — 프론트(Next.js) 로컬 개발 서버에서의 CORS 허용.
 * 허용 오리진은 하드코딩하지 않고 환경변수/설정(application.yml)에서 주입받는다.
 *
 * <p>AI 경로 한도({@link AiRateLimitProperties})의 소모 지점은 인터셉터가 아니라
 * 각 서비스의 모델 호출 직전이다 — 이유는 {@link AiRateLimiter} 참고.
 */
@Configuration
@EnableConfigurationProperties(AiRateLimitProperties.class)
public class WebConfig implements WebMvcConfigurer {

	private final String[] allowedOrigins;

	public WebConfig(
			@Value("${gak.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
		this.allowedOrigins = allowedOrigins;
	}

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		registry.addMapping("/api/**")
				.allowedOrigins(allowedOrigins)
				.allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
	}
}
