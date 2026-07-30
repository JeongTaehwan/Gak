package page.usetaehwan.gak.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import page.usetaehwan.gak.domain.Prediction;
import page.usetaehwan.gak.dto.PredictionRequest;
import page.usetaehwan.gak.dto.PredictionResponse;
import page.usetaehwan.gak.service.PredictionService;

/**
 * 예측 API. 컨트롤러는 요청/응답 변환과 HTTP 관심사만 다루고, 규칙 판단은
 * 서비스에 위임한다(controller → service → repository → domain 단방향).
 *
 * <p>이 초안에는 이 앱의 핵심인 "예측 생성"만 둔다. 일정 통합·진단·적중률 조회 등
 * 나머지 엔드포인트는 동기화 파이프라인과 함께 다음 단계에서 붙인다.
 */
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

	private final PredictionService predictionService;

	public PredictionController(PredictionService predictionService) {
		this.predictionService = predictionService;
	}

	@PostMapping
	public ResponseEntity<PredictionResponse> create(@Valid @RequestBody PredictionRequest request) {
		Prediction saved = predictionService.createPrediction(request.fixtureId(), request.pick());
		return ResponseEntity.status(HttpStatus.CREATED).body(PredictionResponse.from(saved));
	}
}
