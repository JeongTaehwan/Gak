package page.usetaehwan.gak.dto;

import java.time.Instant;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;

/**
 * 예측 응답. 엔티티를 그대로 노출하지 않고 필요한 사실만 평평하게 내려준다.
 */
public record PredictionResponse(
		Long id,
		Long fixtureId,
		Long teamId,
		Pick pick,
		Instant createdAt,
		Pick resolvedResult,
		Boolean isHit
) {
	public static PredictionResponse from(Prediction p) {
		return new PredictionResponse(
				p.getId(),
				p.getFixture().getId(),
				p.getTeam().getId(),
				p.getPick(),
				p.getCreatedAt(),
				p.getResolvedResult(),
				p.getIsHit()
		);
	}
}
