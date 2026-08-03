package page.usetaehwan.gak.dto.prediction;

import java.time.Instant;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;

/**
 * 예측 한 건 — 화면의 기록 목록 한 줄.
 *
 * <p>{@link #leadTimeMinutes}를 함께 준다. 이 앱은 "킥오프 이전에 남긴 예측만 센다"는
 * 규칙 위에 서 있는데, 그 규칙은 <b>보이지 않으면 믿을 이유가 없다.</b> "3일 전에 남김"이
 * 줄마다 찍혀 있으면 사용자가 규칙이 지켜졌다는 걸 눈으로 확인한다. 서버가 스스로
 * "우리는 정직합니다"라고 말하는 것보다 이쪽이 낫다.
 *
 * @param predictionId    예측 id
 * @param fixtureId       경기 id
 * @param kickoff         킥오프(UTC)
 * @param competitionName 대회 표기명(한글 우선)
 * @param competitionShortName 뱃지용 짧은 표기명
 * @param opponentName    상대 팀 표기명
 * @param home            예측 대상 팀이 홈이면 true
 * @param status          경기 상태
 * @param pick            예측(대상 팀 관점)
 * @param resolvedResult  실제 결과. 미채점이면 null
 * @param isHit           적중 여부. 미채점이면 null
 * @param createdAt       예측을 남긴 시각
 * @param leadTimeMinutes 킥오프까지 남았던 시간(분). 항상 양수다 — 그게 이 앱의 규칙이다
 */
public record PredictionRecord(
		long predictionId,
		long fixtureId,
		Instant kickoff,
		String competitionName,
		String competitionShortName,
		String opponentName,
		boolean home,
		FixtureStatus status,
		Pick pick,
		Pick resolvedResult,
		Boolean isHit,
		Instant createdAt,
		long leadTimeMinutes
) {

	public static PredictionRecord from(Prediction p) {
		var fixture = p.getFixture();
		boolean home = fixture.getHomeTeam().getId().equals(p.getTeam().getId());
		var opponent = home ? fixture.getAwayTeam() : fixture.getHomeTeam();

		return new PredictionRecord(
				p.getId(),
				fixture.getId(),
				fixture.getKickoff(),
				fixture.getCompetition().displayName(),
				fixture.getCompetition().displayShortName(),
				opponent.displayName(),
				home,
				fixture.getStatus(),
				p.getPick(),
				p.getResolvedResult(),
				p.getIsHit(),
				p.getCreatedAt(),
				java.time.Duration.between(p.getCreatedAt(), fixture.getKickoff()).toMinutes());
	}
}
