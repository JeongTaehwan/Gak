// requirements.md 2장 — 출시 단계와 팀 선택
package page.usetaehwan.gak.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** {@link TeamAccessProperties} 를 설정에서 읽어 오게 한다. */
@Configuration
@EnableConfigurationProperties(TeamAccessProperties.class)
public class TeamAccessConfig {
}
