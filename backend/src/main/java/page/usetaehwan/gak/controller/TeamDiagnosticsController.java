package page.usetaehwan.gak.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.dto.analysis.AiDiagnosis;
import page.usetaehwan.gak.dto.analysis.TeamDiagnostics;
import page.usetaehwan.gak.dto.prediction.PredictionAccuracy;
import page.usetaehwan.gak.service.PredictionAccuracyService;
import page.usetaehwan.gak.service.analysis.AiDiagnosisService;
import page.usetaehwan.gak.service.analysis.DiagnosticsOptions;
import page.usetaehwan.gak.service.analysis.TeamDiagnosticsService;

/**
 * 한 팀을 보는 화면들이 읽는 엔드포인트 — 통합 일정 + 진단, 그리고 예측 적중률.
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
	private final PredictionAccuracyService accuracyService;
	private final AiDiagnosisService aiDiagnosisService;

	public TeamDiagnosticsController(TeamDiagnosticsService diagnosticsService,
	                                 PredictionAccuracyService accuracyService,
	                                 AiDiagnosisService aiDiagnosisService) {
		this.diagnosticsService = diagnosticsService;
		this.accuracyService = accuracyService;
		this.aiDiagnosisService = aiDiagnosisService;
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

	/**
	 * 같은 진단을 AI가 문장으로 옮긴 것.
	 *
	 * <h2>왜 {@code /diagnostics}에 합치지 않았나</h2>
	 * <p>둘의 속도가 두 자릿수 배 다르다. {@code /diagnostics}는 우리 DB만 읽어 수십 ms,
	 * 이건 모델을 기다려 수 초다. 합치면 <b>밀집도 브래킷을 보러 온 사용자가 모델의
	 * 사고가 끝날 때까지 빈 화면을 본다.</b> 갈라 두면 화면은 규칙 기반 문장으로 즉시
	 * 완성되고, AI 문장은 도착하는 대로 갈아 끼운다.
	 *
	 * <p>그래서 <b>이 엔드포인트는 실패해도 화면을 깨지 않는다.</b> 200 + {@code
	 * available: false}로 답하고(에러 상태코드가 아니다 — AI가 없는 건 정상 상태다),
	 * 프론트는 이미 그려 둔 규칙 기반 문장을 그대로 둔다.
	 *
	 * <p>기준 파라미터를 {@code /diagnostics}와 똑같이 받는 이유: 사용자가 창 폭을
	 * 10일로 바꿔 놓고 AI에게 물으면, AI도 그 10일짜리 지표를 봐야 한다. 기본값으로
	 * 다시 계산하면 화면의 브래킷과 AI 문장이 다른 것을 가리킨다.
	 */
	@GetMapping("/{teamId}/diagnosis")
	public AiDiagnosis diagnosis(
			@PathVariable Long teamId,
			@RequestParam(defaultValue = "14") int windowDays,
			@RequestParam(defaultValue = "5") int minMatches,
			@RequestParam(defaultValue = "6") int formSize) {
		TeamDiagnostics diagnostics = diagnosticsService.diagnose(teamId,
				new DiagnosticsOptions(windowDays, minMatches, formSize, null, null));
		return aiDiagnosisService.narrate(diagnostics);
	}

	/**
	 * 이 팀에 대한 예측 적중률 + 최근 기록.
	 *
	 * <p>진단과 <b>따로</b> 부른다. 진단은 매 화면 진입마다 필요하지만 적중률은 예측 탭을
	 * 열었을 때만 필요하고, 둘은 서로 다른 속도로 변한다(진단은 동기화마다, 적중률은
	 * 채점마다). 한 응답으로 묶으면 타임라인을 볼 때마다 예측 기록까지 읽게 된다.
	 *
	 * @param recentLimit 기록 목록에 몇 건까지 실을지
	 */
	@GetMapping("/{teamId}/predictions")
	public PredictionAccuracy predictions(
			@PathVariable Long teamId,
			@RequestParam(defaultValue = "20") int recentLimit) {
		return accuracyService.of(teamId, recentLimit);
	}
}
