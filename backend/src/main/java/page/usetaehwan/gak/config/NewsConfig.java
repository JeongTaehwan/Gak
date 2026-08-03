package page.usetaehwan.gak.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import page.usetaehwan.gak.external.anthropic.RestAnthropicClient;
import page.usetaehwan.gak.external.rss.RestRssFeedClient;
import page.usetaehwan.gak.external.rss.RssFeedClient;
import page.usetaehwan.gak.service.news.DisabledNewsCategoryTagger;
import page.usetaehwan.gak.service.news.LlmNewsCategoryTagger;
import page.usetaehwan.gak.service.news.NewsCategoryTagger;

/**
 * 뉴스 피드 배선.
 *
 * <h2>태거는 진단과 다른 모델을 쓴다</h2>
 * <p>진단({@code gak.anthropic.model}, 기본 Opus)은 계산된 지표를 읽고 <b>문장</b>을 쓴다.
 * 뉴스 태거는 헤드라인을 읽고 <b>라벨 하나</b>를 고른다. 같은 모델을 쓸 이유가 없어
 * {@code gak.news.classifier.model} 로 따로 잡는다(기본 Haiku).
 *
 * <p>키({@code gak.anthropic.key})는 공유한다 — 같은 계정이니 두 벌 둘 이유가 없다.
 * 그래서 <b>진단이 꺼져 있으면 태거도 꺼진다.</b> 이건 의도한 동작이다: 키가 없다는 건
 * 이 앱에서 "AI를 안 쓴다"는 뜻이고, 한쪽만 켜지는 상태를 만들 이유가 없다.
 */
@Configuration
@EnableConfigurationProperties(NewsProperties.class)
public class NewsConfig {

	private static final Logger log = LoggerFactory.getLogger(NewsConfig.class);

	/** 피드가 느릴 때 스케줄러를 오래 붙잡지 않게. 뉴스는 늦어도 되는 데이터다. */
	private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
	private static final Duration READ_TIMEOUT = Duration.ofSeconds(15);

	@Bean
	public RssFeedClient rssFeedClient() {
		return new RestRssFeedClient(CONNECT_TIMEOUT, READ_TIMEOUT);
	}

	/**
	 * 갈래 태거.
	 *
	 * <p>끄는 조건이 둘이다 — 설정으로 껐거나({@code classifier.enabled: false}),
	 * 키가 없거나. 어느 쪽이든 <b>정상 상태</b>이고 소식은 배지 없이 그대로 뜬다.
	 */
	@Bean
	public NewsCategoryTagger newsCategoryTagger(NewsProperties newsProperties,
	                                             AnthropicProperties anthropicProperties,
	                                             ObjectMapper objectMapper) {
		if (!newsProperties.classifierEnabled()) {
			log.info("뉴스 갈래 태깅 비활성(설정). 소식은 배지 없이 표시됩니다.");
			return new DisabledNewsCategoryTagger();
		}
		if (!anthropicProperties.enabled()) {
			log.info("뉴스 갈래 태깅 비활성(ANTHROPIC_API_KEY 없음). 소식은 배지 없이 표시됩니다.");
			return new DisabledNewsCategoryTagger();
		}

		NewsProperties.Classifier classifier = newsProperties.classifier();
		// 진단용 설정을 그대로 쓰지 않고 모델·상한만 갈아 끼운 사본을 만든다.
		// baseUrl 과 key 는 공유한다.
		AnthropicProperties taggerProperties = new AnthropicProperties(
				anthropicProperties.baseUrl(),
				anthropicProperties.key(),
				classifier.model(),
				classifier.effort(),      // "none" 이면 RestAnthropicClient 가 필드를 생략한다
				classifier.maxTokens(),
				anthropicProperties.timeout());

		log.info("뉴스 갈래 태깅 활성: model={}, batchSize={}",
				classifier.model(), classifier.batchSize());
		return new LlmNewsCategoryTagger(
				new RestAnthropicClient(taggerProperties, objectMapper),
				objectMapper,
				classifier.batchSize());
	}
}
