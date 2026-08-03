package page.usetaehwan.gak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import page.usetaehwan.gak.external.anthropic.AnthropicClient;
import page.usetaehwan.gak.external.anthropic.DisabledAnthropicClient;
import page.usetaehwan.gak.external.anthropic.RestAnthropicClient;

/**
 * AI 진단 클라이언트 배선.
 *
 * <p>키 유무를 <b>여기 한 번만</b> 판단하고 구현체를 갈아 끼운다. 서비스 계층에
 * {@code if (key != null)} 이 흩어지면 "키 없을 때 어떻게 되는지"가 코드 여러 곳에
 * 분산되고, 그중 하나를 빠뜨리면 키 없는 환경에서 NPE가 난다.
 */
@Configuration
@EnableConfigurationProperties(AnthropicProperties.class)
public class AnthropicConfig {

	private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

	@Bean
	public AnthropicClient anthropicClient(AnthropicProperties properties, ObjectMapper objectMapper) {
		if (!properties.enabled()) {
			// 경고가 아니라 정보다 — 키 없이 도는 게 잘못된 상태가 아니다.
			log.info("AI 진단 비활성 (ANTHROPIC_API_KEY 없음). 진단은 규칙 기반으로 동작한다");
			return new DisabledAnthropicClient();
		}
		log.info("AI 진단 활성: model={}, effort={}, timeout={}",
				properties.model(), properties.effort(), properties.timeout());
		return new RestAnthropicClient(properties, objectMapper);
	}
}
