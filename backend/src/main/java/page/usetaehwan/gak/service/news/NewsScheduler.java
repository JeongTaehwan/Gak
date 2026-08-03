package page.usetaehwan.gak.service.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 주기 수집.
 *
 * <h2>API-Football 스케줄러와 예산을 나눠 쓰지 않는다</h2>
 * <p>{@code gak.sync} 의 하루 100요청 제약은 API-Football 것이다. RSS 는 무료이고 인증도
 * 없으므로 <b>{@code RequestBudget} 을 건드리지 않는다.</b> 두 스케줄러가 같은 예산을
 * 공유하면, 뉴스를 켜는 것만으로 경기 동기화가 굶는 일이 생긴다.
 *
 * <p>대신 <b>정각을 피해</b> 5분에 돈다 — 동기화(10분)·채점(30분)과 겹치지 않게 어긋나
 * 있으면, 어느 작업이 느릴 때 서로 밀어내지 않는다.
 *
 * <h2>수집 → 태깅 → 정리 순서</h2>
 * <p>한 회차 안에서 순서대로 돈다. 태깅 대상은 <b>이번에 들어온 것만이 아니라</b> 갈래가
 * 없는 것 전부이므로, 지난번에 실패한 것도 여기서 다시 집힌다.
 *
 * <p>어느 단계에서 예외가 나도 다음 단계는 돈다. 태깅이 죽었다고 오래된 소식 정리까지
 * 멈출 이유는 없다.
 */
@Component
@ConditionalOnProperty(prefix = "gak.news", name = "enabled", havingValue = "true")
public class NewsScheduler {

	private static final Logger log = LoggerFactory.getLogger(NewsScheduler.class);

	private final NewsIngestService ingestService;
	private final NewsTaggingService taggingService;

	public NewsScheduler(NewsIngestService ingestService, NewsTaggingService taggingService) {
		this.ingestService = ingestService;
		this.taggingService = taggingService;
	}

	@Scheduled(cron = "${gak.news.cron:0 5 * * * *}")
	public void run() {
		try {
			ingestService.ingest();
		} catch (Exception e) {
			log.error("뉴스 수집 중 오류", e);
		}
		try {
			taggingService.tagPending();
		} catch (Exception e) {
			log.error("뉴스 갈래 태깅 중 오류", e);
		}
		try {
			ingestService.purgeExpired();
		} catch (Exception e) {
			log.error("오래된 소식 정리 중 오류", e);
		}
	}
}
