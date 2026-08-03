package page.usetaehwan.gak.service.news;

import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.config.NewsProperties;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.repository.NewsItemRepository;

/**
 * 화면이 읽는 통로.
 *
 * <h2>꺼진 소스는 여기서 사라진다</h2>
 * <p>조회할 때마다 <b>지금 켜져 있는 소스 목록</b>으로 거른다. 저장된 행을 지우지 않아도
 * 설정 한 줄이면 그 매체의 기사가 화면에서 즉시 빠진다.
 *
 * <p>수집만 멈추는 것으로는 부족하다 — 어제까지 받아 둔 기사가 계속 떠 있으면 스위치를
 * 내렸다고 말할 수 없다. 저장분까지 지우는 건 별도 단계다
 * ({@code DELETE /api/admin/news/sources/{key}}).
 *
 * <h2>진단과 섞이지 않는다</h2>
 * <p>이 서비스는 {@code TeamDiagnosticsService} 와 아무 관계가 없고, 서로를 부르지 않는다.
 * 화면에서 같은 페이지에 놓이더라도 <b>둘 사이에 "그래서"가 없는 것</b>이 설계의 핵심이다.
 */
@Service
public class NewsQueryService {

	private final NewsProperties properties;
	private final NewsItemRepository repository;

	public NewsQueryService(NewsProperties properties, NewsItemRepository repository) {
		this.properties = properties;
		this.repository = repository;
	}

	/**
	 * 한 팀의 최신 소식.
	 *
	 * @return 발행 시각 내림차순. 켜진 소스가 하나도 없으면 빈 목록(오류가 아니다)
	 */
	@Transactional(readOnly = true)
	public List<NewsItem> forTeam(Long teamId, Integer limit) {
		List<String> sourceKeys = properties.enabledSources().stream()
				.map(NewsProperties.Source::key)
				.toList();
		if (teamId == null || sourceKeys.isEmpty()) {
			return List.of();
		}
		int max = (limit == null || limit < 1)
				? properties.maxPerTeam()
				: Math.min(limit, properties.maxPerTeam());
		return repository.findTeamNews(teamId, sourceKeys, Limit.of(max));
	}
}
