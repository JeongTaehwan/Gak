package page.usetaehwan.gak.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import page.usetaehwan.gak.domain.SourceTier;

/**
 * 뉴스 피드 설정.
 *
 * <h2>소스별 on/off 가 여기 있는 이유</h2>
 * <p>우리는 남의 저작물을 옮긴다. 매체가 "빼 달라"고 하면 <b>그 소스만</b> 즉시 멈출 수
 * 있어야 하고, 그것 때문에 다른 소스나 앱 전체가 멈추면 안 된다. 전역 스위치 하나로는
 * 그게 안 된다.
 *
 * <p>스위치를 내리면 두 가지가 함께 일어난다 — <b>둘 다 필요하다.</b>
 * <ol>
 *   <li>수집이 그 소스를 건너뛴다</li>
 *   <li><b>조회에서도 빠진다</b> — 이미 저장된 것도 화면에 안 나온다</li>
 * </ol>
 * 수집만 멈추면 어제까지 받은 기사가 계속 떠 있고, 그건 스위치를 내린 게 아니다.
 * 저장분까지 지우려면 {@code DELETE /api/admin/news/sources/{key}} 를 부른다.
 *
 * @param enabled  뉴스 수집 전체 on/off (스케줄러 포함)
 * @param cron     수집 주기. 외부 API 예산({@code gak.sync})과 무관하다 — RSS 는 무료다
 * @param userAgent 우리 신원. 밝히고 요청한다. 차단하고 싶은 쪽이 차단할 수 있어야 한다
 * @param retention 이보다 오래된 소식은 지운다. 뉴스는 이력이 아니라 "지금 무슨 일이 있나"다
 * @param maxPerTeam 화면에 내려 줄 최대 건수
 * @param classifier 갈래 태거(LLM) 설정
 * @param sources  소스 목록
 */
@ConfigurationProperties(prefix = "gak.news")
public record NewsProperties(
		boolean enabled,
		String cron,
		String userAgent,
		Duration retention,
		Integer maxPerTeam,
		Classifier classifier,
		List<Source> sources
) {

	public NewsProperties {
		cron = orDefault(cron, "0 5 * * * *");
		userAgent = orDefault(userAgent,
				"GakNewsBot/1.0 (football schedule app; contact via repository)");
		retention = retention == null ? Duration.ofDays(30) : retention;
		maxPerTeam = (maxPerTeam == null || maxPerTeam < 1) ? 40 : maxPerTeam;
		classifier = classifier == null ? new Classifier(true, null, null, null, 0) : classifier;
		sources = sources == null ? List.of() : List.copyOf(sources);
	}

	/**
	 * 소스 하나.
	 *
	 * @param key         식별자. 저장·삭제·스위치의 단위다. 한 번 정하면 바꾸지 않는다
	 *                    (바꾸면 이미 저장된 기사가 고아가 된다)
	 * @param name        화면에 표시할 출처명
	 * @param url         RSS URL
	 * @param tier        공식/언론
	 * @param teamId      이 피드가 어느 팀을 위한 것인가. <b>null 이면 범용 피드</b>로
	 *                    보고 게이트가 모든 팀 별칭에 대조한다. 값이 있으면 그 팀 별칭만 본다
	 * @param enabled     <b>이 소스의 kill switch.</b> false 면 수집도 조회도 안 된다
	 * @param crawlDelay  같은 호스트에 연속 요청할 때 사이에 둘 간격.
	 *                    Manchester Evening News 의 robots.txt 가 {@code Crawl-delay: 10} 을 요구한다
	 */
	public record Source(
			String key,
			String name,
			String url,
			SourceTier tier,
			Long teamId,
			Boolean enabled,
			Duration crawlDelay
	) {
		public Source {
			tier = tier == null ? SourceTier.MEDIA : tier;
			enabled = enabled == null || enabled;
			crawlDelay = crawlDelay == null ? Duration.ZERO : crawlDelay;
		}

		public boolean isEnabled() {
			return Boolean.TRUE.equals(enabled);
		}

		/** 설정이 쓸 만한가. 셋 중 하나라도 비면 그 소스는 버린다(앱은 계속 뜬다). */
		public boolean valid() {
			return key != null && !key.isBlank()
					&& name != null && !name.isBlank()
					&& url != null && !url.isBlank();
		}
	}

	/**
	 * 갈래 태거.
	 *
	 * @param enabled   false 면 태깅을 아예 안 한다. 소식은 배지 없이 그대로 뜬다
	 * @param model     모델 id. 분류기이므로 작은 모델로 충분하다
	 * @param effort    추론 강도. <b>{@code none} 이면 파라미터를 보내지 않는다</b> —
	 *                  Haiku 4.5 는 {@code effort} 를 받지 않고 400 을 낸다
	 * @param maxTokens 응답 상한
	 * @param batchSize 한 요청에 묶어 보낼 헤드라인 수. 0 이면 기본값
	 */
	public record Classifier(
			boolean enabled,
			String model,
			String effort,
			Integer maxTokens,
			int batchSize
	) {
		public Classifier {
			model = orDefault(model, "claude-haiku-4-5");
			// Haiku 4.5 에는 effort 파라미터가 없다. 기본을 "none"으로 두어
			// RestAnthropicClient 가 이 필드를 통째로 생략하게 한다.
			effort = orDefault(effort, "none");
			maxTokens = (maxTokens == null || maxTokens < 256) ? 2048 : maxTokens;
			batchSize = batchSize > 0 ? batchSize : 20;
		}
	}

	/** 켜져 있고 설정이 온전한 소스만. */
	public List<Source> enabledSources() {
		return sources.stream().filter(Source::valid).filter(Source::isEnabled).toList();
	}

	/** 설정에 적힌 전부(꺼진 것 포함) — 관리 화면이 상태를 보여 줄 때 쓴다. */
	public List<Source> allSources() {
		return sources.stream().filter(Source::valid).toList();
	}

	public boolean classifierEnabled() {
		return classifier != null && classifier.enabled();
	}

	private static String orDefault(String value, String fallback) {
		return (value == null || value.isBlank()) ? fallback : value;
	}
}
