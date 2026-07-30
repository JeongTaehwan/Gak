package page.usetaehwan.gak.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시간 소스를 빈으로 분리한다. "예측은 킥오프 이전" 규칙이 이 앱의 핵심이라,
 * 현재 시각을 직접 {@code Instant.now()}로 읽지 않고 주입받은 Clock으로 읽어
 * 테스트에서 시간을 고정할 수 있게 한다.
 */
@Configuration
public class TimeConfig {

	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}
}
