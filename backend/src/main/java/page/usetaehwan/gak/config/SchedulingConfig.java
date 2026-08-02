package page.usetaehwan.gak.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @Scheduled} 활성화. 동기화 스케줄러 자체의 on/off는
 * {@code gak.sync.enabled} 로 따로 잡는다(테스트에서는 꺼 둔다).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
