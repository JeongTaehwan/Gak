package page.usetaehwan.gak.service.news;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.config.NewsProperties;
import page.usetaehwan.gak.domain.NewsItem;
import page.usetaehwan.gak.dto.news.TeamNewsResponse;
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
		List<String> sourceKeys = enabledKeys();
		if (teamId == null || sourceKeys.isEmpty()) {
			return List.of();
		}
		return repository.findTeamNews(teamId, sourceKeys, Limit.of(cap(limit)));
	}

	/**
	 * 우리가 이 팀에 대해 가진 소식의 범위.
	 *
	 * <p>화면이 <b>"왜 비어 있는지"</b>를 말할 수 있게 하는 값이다. 목록과 함께 내려간다.
	 */
	@Transactional(readOnly = true)
	public TeamNewsResponse.Coverage coverageFor(Long teamId) {
		List<String> sourceKeys = enabledKeys();
		long retentionDays = properties.retention().toDays();
		if (teamId == null || sourceKeys.isEmpty()) {
			return new TeamNewsResponse.Coverage(null, null, 0, retentionDays);
		}
		Object[] raw = repository.findCoverageRaw(teamId, sourceKeys);
		// 집계 쿼리는 행이 없어도 한 줄을 준다 — min/max 가 null 인 줄이다.
		Object[] row = (raw != null && raw.length == 1 && raw[0] instanceof Object[] inner)
				? inner : raw;
		if (row == null || row.length < 3) {
			return new TeamNewsResponse.Coverage(null, null, 0, retentionDays);
		}
		return new TeamNewsResponse.Coverage(
				(Instant) row[0],
				(Instant) row[1],
				row[2] == null ? 0L : ((Number) row[2]).longValue(),
				retentionDays);
	}

	private List<String> enabledKeys() {
		return properties.enabledSources().stream()
				.map(NewsProperties.Source::key)
				.toList();
	}

	private int cap(Integer limit) {
		return (limit == null || limit < 1)
				? properties.maxPerTeam()
				: Math.min(limit, properties.maxPerTeam());
	}
}
