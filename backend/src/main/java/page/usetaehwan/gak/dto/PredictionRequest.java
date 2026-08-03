package page.usetaehwan.gak.dto;

import jakarta.validation.constraints.NotNull;
import page.usetaehwan.gak.domain.Pick;

/**
 * 예측 생성 요청. 킥오프 이전인지 여부는 클라이언트가 보내는 게 아니라
 * 서버가 자신의 시계로 판정한다(클라이언트 시각을 신뢰하지 않는다).
 *
 * @param fixtureId 경기
 * @param teamId    <b>누구 관점의 예측인가.</b> {@code pick}이 승/무/패라 주어가 없으면
 *                  채점할 수 없다 — 같은 "W"가 홈 승리도 원정 승리도 되기 때문이다
 * @param pick      그 팀 관점의 승/무/패
 */
public record PredictionRequest(
		@NotNull Long fixtureId,
		@NotNull Long teamId,
		@NotNull Pick pick
) {
}
