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

	/** 진단 대상 팀 관점의 예측(승/무/패). */
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

	private Prediction(Fixture fixture, Pick pick, Instant createdAt) {
		this.fixture = fixture;
		this.pick = pick;
		this.createdAt = createdAt;
	}

	/**
	 * 예측 생성용 정적 팩터리. kickoff 이전인지 검증한 뒤에만 생성된다.
	 * 시간 규칙 강제의 1차 방어선이며, 서비스 계층이 이 팩터리를 통해서만 예측을 만든다.
	 *
	 * @throws IllegalArgumentException createdAt이 kickoff 이후(같은 시각 포함)이면
	 */
	public static Prediction create(Fixture fixture, Pick pick, Instant createdAt) {
		if (fixture == null || pick == null || createdAt == null) {
			throw new IllegalArgumentException("fixture, pick, createdAt 는 필수입니다.");
		}
		if (!createdAt.isBefore(fixture.getKickoff())) {
			throw new IllegalArgumentException(
					"예측은 킥오프 이전에만 생성할 수 있습니다. (createdAt=%s, kickoff=%s)"
							.formatted(createdAt, fixture.getKickoff()));
		}
		return new Prediction(fixture, pick, createdAt);
	}

	/**
	 * 경기 종료 후 실제 결과로 채점한다.
	 * 적중 여부는 파생값이지만, "언제 무엇으로 채점했는지"를 못박기 위해 스냅샷으로 남긴다.
	 */
	public void resolve(Pick actualResult) {
		this.resolvedResult = actualResult;
		this.isHit = (actualResult != null) && (actualResult == pick);
	}
}
