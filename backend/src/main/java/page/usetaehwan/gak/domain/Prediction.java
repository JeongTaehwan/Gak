package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예측 기록. 이 앱이 저장하는 유일한 "우리가 만든 사실"이다.
 *
 * <p><b>핵심 불변식:</b> 반드시 kickoff 이전에만 생성된다. 사후 예측이 들어가면 적중률이
 * 의미를 잃기 때문이다. 이 시점 제약은 서비스 계층(PredictionService)에서 강제하며,
 * 엔티티는 그 규칙을 통과한 예측만 생성되도록 정적 팩터리로만 만들 수 있게 한다.
 *
 * <p>id는 API가 주는 값이 아니라 우리가 만드는 기록이므로 자체 생성(IDENTITY)한다.
 */
@Entity
@Table(name = "prediction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Prediction {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fixture_id")
	private Fixture fixture;

	/**
	 * <b>누구 관점의 예측인가.</b> {@link #pick}이 승/무/패인데 주어가 없으면 채점할 수 없다 —
	 * 같은 "W"가 홈팀 승리도 되고 원정팀 승리도 되기 때문이다.
	 *
	 * <p>이 앱은 한 팀의 일정을 보다가 그 팀의 다음 경기를 예측하는 구조라, 예측의 주어는
	 * 항상 "지금 보고 있는 팀"이고 그 팀은 홈일 수도 원정일 수도 있다. "홈팀 기준"처럼
	 * 관례로 정하면 원정 경기 예측이 통째로 뒤집힌 채 채점된다.
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id")
	private Team team;

	/** {@link #team} 관점의 예측(승/무/패). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Pick pick;

	/** 예측을 남긴 시각. 반드시 fixture.kickoff 이전이어야 한다. */
	@Column(nullable = false)
	private Instant createdAt;

	/** 채점 후 실제 결과. 미채점이면 null. */
	@Enumerated(EnumType.STRING)
	private Pick resolvedResult;

	/** 적중 여부 스냅샷(resolvedResult == pick). 미채점이면 null. */
	private Boolean isHit;

	private Prediction(Fixture fixture, Team team, Pick pick, Instant createdAt) {
		this.fixture = fixture;
		this.team = team;
		this.pick = pick;
		this.createdAt = createdAt;
	}

	/**
	 * 예측 생성용 정적 팩터리. kickoff 이전인지 검증한 뒤에만 생성된다.
	 * 시간 규칙 강제의 1차 방어선이며, 서비스 계층이 이 팩터리를 통해서만 예측을 만든다.
	 *
	 * <p>예측 대상 팀이 그 경기에 <b>실제로 뛰는지</b>도 함께 본다. 안 뛰는 팀에 대한
	 * 예측은 영원히 채점되지 않는 기록으로 남는데, 그건 적중률의 분모만 갉아먹는다.
	 *
	 * @throws IllegalArgumentException createdAt이 kickoff 이후(같은 시각 포함)이거나,
	 *                                  team이 이 경기의 양 팀 중 하나가 아니면
	 */
	public static Prediction create(Fixture fixture, Team team, Pick pick, Instant createdAt) {
		if (fixture == null || team == null || pick == null || createdAt == null) {
			throw new IllegalArgumentException("fixture, team, pick, createdAt 는 필수입니다.");
		}
		if (!fixture.involves(team.getId())) {
			throw new IllegalArgumentException(
					"이 경기에 뛰지 않는 팀입니다. (fixtureId=%s, teamId=%s)"
							.formatted(fixture.getId(), team.getId()));
		}
		if (!createdAt.isBefore(fixture.getKickoff())) {
			throw new IllegalArgumentException(
					"예측은 킥오프 이전에만 생성할 수 있습니다. (createdAt=%s, kickoff=%s)"
							.formatted(createdAt, fixture.getKickoff()));
		}
		return new Prediction(fixture, team, pick, createdAt);
	}

	/**
	 * 경기 종료 후 실제 결과로 채점한다.
	 * 적중 여부는 파생값이지만, "언제 무엇으로 채점했는지"를 못박기 위해 스냅샷으로 남긴다.
	 *
	 * @throws IllegalArgumentException actualResult가 null이면. 결과를 모르는 채로 채점하면
	 *                                  {@code isHit=false}가 되어 "틀렸다"로 기록된다 —
	 *                                  모르는 것과 틀린 것은 다르다
	 */
	public void resolve(Pick actualResult) {
		if (actualResult == null) {
			throw new IllegalArgumentException("결과 없이 채점할 수 없습니다. predictionId=" + id);
		}
		this.resolvedResult = actualResult;
		this.isHit = actualResult == pick;
	}

	/** 이미 채점된 예측인가. 채점은 한 번뿐이다(아래 {@code PredictionScoringService} 참고). */
	public boolean isScored() {
		return isHit != null;
	}
}
