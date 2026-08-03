package page.usetaehwan.gak.service.news;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import page.usetaehwan.gak.config.NewsProperties;
import page.usetaehwan.gak.domain.NewsCategory;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.repository.NewsItemRepository;

/**
 * 갈래 태깅 단계 — 수집과 <b>분리된 별도 패스</b>다.
 *
 * <h2>왜 수집 안에 넣지 않았나</h2>
 * <p>수집이 끝나면 화면에 필요한 건 이미 다 있다(제목·링크·시각·출처). 태깅은 그 위에
 * 덧칠하는 것이라, 같은 트랜잭션에 묶으면 <b>모델이 느리거나 죽었을 때 소식 저장까지
 * 같이 실패한다.</b> 분리해 두면 태깅이 실패해도 소식은 이미 들어와 있고, 다음 회차에
 * 다시 집어 간다({@code category is null} 로 남아 있으므로).
 *
 * <p>{@code /diagnosis} 가 {@code /diagnostics} 와 엔드포인트를 나눈 것과 같은 판단이다 —
 * 수십 ms 짜리 작업과 수 초짜리 작업을 한 줄에 묶지 않는다.
 *
 * <h2>한 번에 다 하지 않는다</h2>
 * <p>회차당 상한을 둔다. 처음 켰을 때 밀린 물량이 한꺼번에 나가지 않게 하려는 것이고,
 * 태깅이 밀려도 <b>최신순으로</b> 채우므로 사용자가 실제로 보는 화면 위쪽부터 배지가 붙는다.
 */
@Service
public class NewsTaggingService {

	private static final Logger log = LoggerFactory.getLogger(NewsTaggingService.class);

	/** 한 회차에 태깅할 최대 건수. 배치 20건 기준 5요청. */
	private static final int MAX_PER_RUN = 100;

	private final NewsProperties properties;
	private final NewsItemRepository repository;
	private final NewsCategoryTagger tagger;
	private final NewsItemWriter writer;

	public NewsTaggingService(NewsProperties properties,
	                          NewsItemRepository repository,
	                          NewsCategoryTagger tagger,
	                          NewsItemWriter writer) {
		this.properties = properties;
		this.repository = repository;
		this.tagger = tagger;
		this.writer = writer;
	}

	/**
	 * 갈래가 없는 소식에 갈래를 붙인다.
	 *
	 * @return 실제로 붙은 건수. 0 은 정상일 수 있다(대상이 없거나, 태거가 꺼져 있거나)
	 */
	public int tagPending() {
		if (!tagger.available()) {
			// 경고가 아니라 debug 다 — 태거가 없는 건 잘못된 상태가 아니다.
			log.debug("갈래 태거가 꺼져 있습니다. 소식은 배지 없이 표시됩니다.");
			return 0;
		}
		List<String> sourceKeys = properties.enabledSources().stream()
				.map(NewsProperties.Source::key)
				.toList();
		if (sourceKeys.isEmpty()) {
			return 0;
		}

		// 꺼진 소스의 소식에는 태그를 붙이지 않는다 — 화면에 안 나올 것에 돈을 쓸 이유가 없다.
		List<NewsItem> pending = repository.findUntagged(sourceKeys, Limit.of(MAX_PER_RUN));
		if (pending.isEmpty()) {
			return 0;
		}

		Map<Long, NewsCategory> categories = tagger.tag(pending);
		if (categories.isEmpty()) {
			return 0;
		}
		int applied = writer.applyCategories(categories);
		log.info("뉴스 갈래 태깅: 대상 {}건 → {}건에 반영", pending.size(), applied);
		return applied;
	}
}
