package page.usetaehwan.gak.controller;

import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.config.NewsProperties;
import page.usetaehwan.gak.domain.SourceTier;
import page.usetaehwan.gak.repository.NewsItemRepository;
import page.usetaehwan.gak.service.news.NewsIngestService;
import page.usetaehwan.gak.service.news.NewsItemWriter;
import page.usetaehwan.gak.service.news.NewsTaggingService;

/**
 * 뉴스 수집 수동 실행·소스 상태 확인·저장분 삭제.
 *
 * <p>{@code local} 프로파일에서만 올라온다({@code SyncAdminController} 와 같은 판단) —
 * 이 앱에는 아직 인증이 없고, 인증 없는 DELETE 를 배포 환경에 열어 두는 것보다
 * <b>아예 없는 편이 확실하다.</b>
 *
 * <h2>⚠️ 그래서 운영 환경의 내리기(take-down) 절차는 이렇다</h2>
 * <ol>
 *   <li><b>설정에서 그 소스를 끈다</b> — {@code gak.news.sources[n].enabled: false}.
 *       배포되는 즉시 조회에서 빠져 <b>화면에 안 나온다.</b> 수집도 멈춘다.
 *       여기까지가 "즉시 내린다"의 실제 내용이고, 배포만으로 끝난다.</li>
 *   <li>저장분까지 지워야 하면 이 컨트롤러의 DELETE 를 <b>같은 DB에 붙은 로컬 실행</b>으로
 *       부르거나, DB에서 직접 지운다.</li>
 * </ol>
 *
 * <p>인증이 생기면 2번을 운영에서 바로 부를 수 있게 옮긴다. 지금 구조에서 그걸 열면
 * 아무나 뉴스 테이블을 비울 수 있다.
 */
@RestController
@RequestMapping("/api/admin/news")
@Profile("local")
public class NewsAdminController {

	private final NewsProperties properties;
	private final NewsIngestService ingestService;
	private final NewsTaggingService taggingService;
	private final NewsItemWriter writer;
	private final NewsItemRepository repository;

	public NewsAdminController(NewsProperties properties,
	                           NewsIngestService ingestService,
	                           NewsTaggingService taggingService,
	                           NewsItemWriter writer,
	                           NewsItemRepository repository) {
		this.properties = properties;
		this.ingestService = ingestService;
		this.taggingService = taggingService;
		this.writer = writer;
		this.repository = repository;
	}

	/**
	 * 소스별 상태 — 켜져 있는지, 저장분이 몇 건인지.
	 *
	 * <p><b>꺼진 소스도 함께 보여 준다.</b> 껐다는 사실과 그 소스에 아직 데이터가 남아
	 * 있다는 사실을 한 화면에서 봐야, "껐는데 왜 아직 있지"를 헷갈리지 않는다.
	 */
	@GetMapping("/sources")
	public List<SourceStatus> sources() {
		return properties.allSources().stream()
				.map(source -> new SourceStatus(
						source.key(),
						source.name(),
						source.url(),
						source.tier(),
						source.teamId(),
						source.isEnabled(),
						repository.countBySourceKey(source.key())))
				.toList();
	}

	/**
	 * @param enabled false 면 수집도 조회도 되지 않는다. 저장분은 남아 있다
	 * @param storedItems 남아 있는 건수. enabled=false 인데 0 이 아니면 아직 지워지지 않은 것
	 */
	public record SourceStatus(String key, String name, String url, SourceTier tier,
	                           Long teamId, boolean enabled, long storedItems) {
	}

	/** 스케줄러를 기다리지 않고 지금 한 번 수집한다. */
	@PostMapping("/ingest")
	public NewsIngestService.IngestReport ingest() {
		return ingestService.ingest();
	}

	/** 갈래가 없는 소식에 태그를 붙인다. 키가 없으면 0 을 돌려준다(오류 아님). */
	@PostMapping("/tag")
	public TagReport tag() {
		return new TagReport(taggingService.tagPending());
	}

	public record TagReport(int tagged) {
	}

	/**
	 * 한 소스의 저장분을 전부 지운다 — 내리기의 마지막 단계.
	 *
	 * <p>설정에서 끄는 것만으로 화면에서는 사라지지만, 우리 DB에는 남아 있다.
	 * 매체가 "저장분도 지워 달라"고 하면 이걸 부른다. <b>되돌릴 수 없다</b> —
	 * 다시 켜면 피드에 아직 남아 있는 항목만 다시 들어온다.
	 */
	@DeleteMapping("/sources/{sourceKey}")
	public PurgeReport purge(@PathVariable String sourceKey) {
		int deleted = writer.deleteSource(sourceKey);
		return new PurgeReport(sourceKey, deleted);
	}

	public record PurgeReport(String sourceKey, int deleted) {
	}

	/** 보관 기간이 지난 소식 정리. */
	@PostMapping("/purge-expired")
	public PurgeReport purgeExpired() {
		return new PurgeReport("(expired)", ingestService.purgeExpired());
	}
}
