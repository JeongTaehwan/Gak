// requirements.md 1·5장 — 모든 자유 질문은 진단 경로로 간다
package page.usetaehwan.gak.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.config.ClientIpResolver;
import page.usetaehwan.gak.dto.analysis.QuestionRequest;
import page.usetaehwan.gak.dto.analysis.TeamAnswer;
import page.usetaehwan.gak.service.analysis.TeamQuestionService;

/**
 * 자유 질문 — {@code POST /api/teams/{teamId}/questions}.
 *
 * <h2>진단 경로 하나뿐이다</h2>
 * <p>질문을 읽고 예측이나 순위표로 보내는 분기가 없다. 분류기는 틀리고, 틀리면 사용자는
 * 자기가 묻지 않은 화면에 가 있다. 답할 수 없으면 다른 데로 보내는 대신
 * <b>답할 수 없다고 말한다.</b>
 *
 * <h2>실패해도 200이다</h2>
 * <p>"근거가 없다"·"지원 범위 밖이다"·"분석이 실패했다"·"질문을 못 알아들었다"는 전부
 * <b>정상 응답</b>이다. 화면이 각각 다른 말을 해야 하는 네 가지 상태이지 HTTP 오류가
 * 아니다. 4xx/5xx로 내려보내면 화면 코드가 넷을 구분할 수 없고 "실패"로 뭉뚱그린다.
 *
 * <p>진짜 오류만 오류다 — 없는 팀은 404(서비스가 던진 {@code NoSuchElementException}),
 * 시즌을 빠뜨렸거나 질문이 비었으면 400(요청 검증).
 *
 * <p>{@code GET} 이 아니라 {@code POST} 인 이유: 질문 전문이 URL에 실려 접근 로그와
 * 브라우저 기록에 남는 걸 피한다.
 */
@RestController
@RequestMapping("/api/teams")
public class TeamQuestionController {

	private final TeamQuestionService questionService;
	private final ClientIpResolver clientIpResolver;

	public TeamQuestionController(TeamQuestionService questionService,
	                              ClientIpResolver clientIpResolver) {
		this.questionService = questionService;
		this.clientIpResolver = clientIpResolver;
	}

	@PostMapping("/{teamId}/questions")
	public TeamAnswer ask(@PathVariable Long teamId,
	                      @Valid @RequestBody QuestionRequest request,
	                      HttpServletRequest http) {
		// IP 는 유료 호출 한도의 식별자 — HTTP 관심사라 여기서 뽑아 넘긴다 (DG 8절).
		return questionService.answer(
				teamId, request.season(), request.question(), clientIpResolver.resolve(http));
	}
}
