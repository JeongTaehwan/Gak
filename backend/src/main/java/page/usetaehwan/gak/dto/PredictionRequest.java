package page.usetaehwan.gak.dto;

import jakarta.validation.constraints.NotNull;
import page.usetaehwan.gak.domain.Pick;

/**
 * 예측 생성 요청. 킥오프 이전인지 여부는 클라이언트가 보내는 게 아니라
 * 서버가 자신의 시계로 판정한다(클라이언트 시각을 신뢰하지 않는다).
 */
public record PredictionRequest(
		@NotNull Long fixtureId,
		@NotNull Pick pick
) {
}
