package page.usetaehwan.gak.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.service.analysis.DiagnosticsOptions;
import page.usetaehwan.gak.service.analysis.TeamDiagnosticsService;

/**
 * 한 팀의 통합 일정 + 진단 조회. 프론트 타임라인 화면이 읽는 <b>단 하나의</b> 엔드포인트다.
 *
 * <h2>왜 경기 목록과 진단을 나누지 않았나</h2>
 * <p>"경기 목록"과 "밀집도"를 따로 부르면 두 응답이 서로 다른 스냅샷을 볼 수 있다.
 * 그 사이에 동기화가 끼어들어 경기 하나가 연기되면, 화면에는 5경기가 그려지는데
 * 밀집 구간은 "14일 6경기"라고 말하는 상태가 된다. 한 번에 계산해 한 번에 내려보내면
 * 그런 어긋남 자체가 생기지 않는다. (서비스도 질의를 한 번만 한다 — N+1 방지)
 *
 * <h2>계산 기준을 쿼리 파라미터로 여는 이유</h2>
 * <p>"14일 5경기 말고 10일 4경기면 어떻게 보이나"는 이 앱에서 흔한 조작이다. 기준을
 * 서버 상수로 굳히면 그때마다 배포해야 한다. 다만 값은 그대로 믿지 않고
 * {@link DiagnosticsOptions}의 생성자가 검증한다 — 위반은
 * {@link IllegalArgumentException} → HTTP 400으로 나간다.
 *
 * <p>팀이 없으면 서비스가 {@code NoSuchElementException}을 던지고, 공통 핸들러가
 * 404로 옮긴다. 컨트롤러는 HTTP 관심사만 다루고 판단하지 않는다.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamDiagnosticsController {

	private final TeamDiagnosticsService diagnosticsService;

	public TeamDiagnosticsController(TeamDiagnosticsService diagnosticsService) {
		this.diagnosticsService = diagnosticsService;
	}

	/**
	 * @param teamId     API-Football 팀 id (맨유 = 33)
	 * @param windowDays 밀집 판정 창 폭(일). 기본 14
	 * @param minMatches 그 창 안에 몇 경기부터 밀집으로 볼지. 기본 5
	 * @param formSize   최근 폼을 몇 경기로 볼지. 기본 6
	 */
	@GetMapping("/{teamId}/diagnostics")
	public TeamDiagnostics diagnostics(
			@PathVariable Long teamId,
			@RequestParam(defaultValue = "14") int windowDays,
			@RequestParam(defaultValue = "5") int minMatches,
			@RequestParam(defaultValue = "6") int formSize) {
		return diagnosticsService.diagnose(teamId,
				new DiagnosticsOptions(windowDays, minMatches, formSize, null, null));
	}
}
