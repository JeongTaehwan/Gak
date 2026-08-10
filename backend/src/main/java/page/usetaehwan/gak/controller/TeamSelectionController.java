// requirements.md 2~4장 — 팀 선택 · 시즌과 회고 · 선택 대상이 아닌 시즌
package page.usetaehwan.gak.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.dto.analysis.TeamSelection;
import page.usetaehwan.gak.service.analysis.TeamSelectionService;

/**
 * 입력 화면이 <b>가장 먼저</b> 부르는 엔드포인트 — 시즌과 선택 가능 팀.
 *
 * <p>다른 네 화면(통합 타임라인·진단·예측/적중·순위표)은 여기서 정해진
 * {@code teamId + season} 을 받아서 움직인다. 이 응답보다 먼저 그 화면들을 부르면
 * 어느 시즌을 물어야 할지 알 수 없다.
 *
 * <p>팀 목록을 이 앱이 어디에도 저장하지 않는다는 사실이 여기서 드러난다 —
 * {@code season} 을 바꿔 부르면 그 시즌 기준으로 목록이 다시 계산된다.
 */
@RestController
public class TeamSelectionController {

	private final TeamSelectionService selectionService;

	public TeamSelectionController(TeamSelectionService selectionService) {
		this.selectionService = selectionService;
	}

	/**
	 * @param season 볼 시즌. 생략하면 자동 판정(치른 경기가 있는 최신 시즌). 화면은 이때
	 *               내려온 값을 URL에 <b>고정</b>해, 시간이 지나도 같은 URL이 같은 시즌을
	 *               재현하게 한다
	 * @param teamId 이미 고른 팀. 생략 가능. 그 시즌의 선택 대상이 아니어도 <b>다른 팀으로
	 *               바꾸지 않고</b> {@code selected.eligible=false} 로 답한다
	 */
	@GetMapping("/api/teams/selection")
	public TeamSelection selection(@RequestParam(required = false) Integer season,
	                               @RequestParam(required = false) Long teamId) {
		return selectionService.resolve(season, teamId);
	}
}
