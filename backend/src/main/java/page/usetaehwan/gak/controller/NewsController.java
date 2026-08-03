package page.usetaehwan.gak.controller;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.dto.news.NewsItemResponse;
import page.usetaehwan.gak.service.news.NewsQueryService;

/**
 * 팀 소식 조회.
 *
 * <h2>진단과 엔드포인트를 나눈 이유</h2>
 * <p>{@code /api/teams/{id}/diagnostics} 와 <b>합치지 않는다.</b> 기술적으로는 응답에
 * 필드 하나를 더하면 되지만, 그 순간 화면 코드에서 두 층이 같은 객체로 들어오고,
 * 언젠가 누군가 진단 문장에 뉴스를 인용하게 된다. <b>층을 나눈다는 건 서로를 참조하지
 * 않는다는 뜻이고, 그걸 API 모양으로 못 박아 둔다.</b>
 *
 * <p>부수적으로: 뉴스가 없거나 느려도 진단 화면이 멀쩡하다. 두 응답은 서로의 실패에
 * 영향을 주지 않는다.
 *
 * <h2>소식이 없는 것은 오류가 아니다</h2>
 * <p>빈 배열과 200 을 돌려준다. 404 를 쓰면 "이 팀이 없다"와 "이 팀 소식이 아직 없다"가
 * 구분되지 않는다. 소스를 전부 꺼 둔 상태에서도 마찬가지다.
 */
@RestController
public class NewsController {

	private final NewsQueryService newsQueryService;

	public NewsController(NewsQueryService newsQueryService) {
		this.newsQueryService = newsQueryService;
	}

	/**
	 * @param teamId 팀 id
	 * @param limit  최대 건수(설정 상한을 넘지 못한다)
	 */
	@GetMapping("/api/teams/{teamId}/news")
	public List<NewsItemResponse> teamNews(@PathVariable Long teamId,
	                                       @RequestParam(required = false) Integer limit) {
		return newsQueryService.forTeam(teamId, limit).stream()
				.map(NewsItemResponse::from)
				.toList();
	}
}
