package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 경기에 한 선수가 빠졌다는 사실. API의 {@code /injuries} 응답 한 줄에 대응한다.
 *
 * <p><b>이름을 {@code Injury}로 하지 않은 이유</b>는 {@link AbsenceReason}에 적어 뒀다 —
 * 엔드포인트 이름과 달리 징계·질병·차출·감독 결정이 함께 들어온다.
 *
 * <p>사실만 저장한다. "이 팀이 이번 시즌 결장으로 몇 명·몇 경기를 잃었나" 같은 파생값은
 * 저장하지 않고 진단 계산에서 그때그때 센다(프로젝트 원칙).
 *
 * <h2>키</h2>
 * <p>API가 이 레코드에 id를 주지 않는다. 같은 경기·같은 선수에 대해 한 줄만 존재하므로
 * {@code (fixture, player)}가 자연키다. 그래서 unique 제약으로 못 박고, 동기화는 그
 * 조합으로 찾아 갱신한다(멱등). id 자체는 우리가 만든다 — API가 주는 값이 아니므로
 * PK로 쓸 게 없다.
 *
 * <h2>경기가 없으면 저장하지 않는다</h2>
 * <p>{@code fixture}는 필수다. 결장은 "그 경기에 못 나왔다"는 사실이라 경기가 없으면
 * 의미가 없고, 우리 DB에 없는 경기의 결장 기록은 아무 화면에도 닿지 못한 채 쌓이기만 한다.
 * 동기화가 그런 줄은 건너뛴다.
 */
@Entity
@Table(
		name = "absence",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_absence_fixture_player",
				columnNames = {"fixture_id", "player_id"}),
		indexes = {
				// "이 팀의 경기별 결장자" — 진단 계산의 대표 질의.
				@Index(name = "idx_absence_team_fixture", columnList = "team_id, fixture_id"),
				@Index(name = "idx_absence_fixture", columnList = "fixture_id")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Absence {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "fixture_id")
	private Fixture fixture;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "player_id")
	private Player player;

	/** 결장한 선수의 소속 팀. 경기의 양 팀 중 하나다. */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id")
	private Team team;

	/** 확정 결장인가 불투명인가. 세는 건 OUT만 센다. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AbsenceStatus status;

	/** 사유 갈래(부상·징계·질병·차출·기타). */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AbsenceReason reason;

	/**
	 * API가 준 사유 원문(예 "Knee Injury", "Suspended").
	 * 분류가 틀렸거나 새 문구가 생겼을 때 되짚을 수 있게 그대로 남긴다.
	 */
	@Column(nullable = false)
	private String reasonRaw;

	@Builder
	private Absence(Long id, Fixture fixture, Player player, Team team,
	                AbsenceStatus status, AbsenceReason reason, String reasonRaw) {
		this.id = id;
		this.fixture = fixture;
		this.player = player;
		this.team = team;
		this.status = status;
		this.reason = reason;
		this.reasonRaw = reasonRaw;
	}

	/**
	 * 동기화가 가져온 사실로 갱신한다(upsert의 "update" 쪽).
	 * 사유와 확정 여부는 경기 직전까지 바뀐다 — "Questionable"이 "Missing Fixture"가 되거나
	 * 그 반대가 된다. 그래서 매번 덮어쓴다.
	 */
	public void applyApiFacts(AbsenceStatus status, AbsenceReason reason, String reasonRaw) {
		if (status != null) {
			this.status = status;
		}
		if (reason != null) {
			this.reason = reason;
		}
		if (reasonRaw != null && !reasonRaw.isBlank()) {
			this.reasonRaw = reasonRaw;
		}
	}

	/** 결장자 수에 세는 대상인가(확정 결장만). */
	public boolean countsAsOut() {
		return status == AbsenceStatus.OUT;
	}
}
