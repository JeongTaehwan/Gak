package page.usetaehwan.gak.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 순위표 한 줄 — <b>API가 준 사실</b>로서의 순위.
 *
 * <h2>이건 "지금 순위"다. 경기 시점 순위가 아니다</h2>
 * <p>{@code /standings} 는 호출 시점의 표 하나만 준다. 날짜를 지정할 수 없어서 지난
 * 시즌을 물으면 최종 표가 온다. 그래서 이 엔티티는 <b>덮어쓰기</b>다 — 동기화할 때마다
 * 그 시점 값으로 갱신되고, 과거 이력을 쌓지 않는다.
 *
 * <p>경기 시점 순위는 우리가 경기 결과로 계산한다
 * ({@link page.usetaehwan.gak.service.analysis.LeagueTable}). 이건 이 프로젝트의 기존 원칙
 * 그대로다 — <b>사실만 저장하고 파생값은 계산한다.</b>
 *
 * <h2>그럼 이걸 왜 저장하나 — 두 가지 때문이다</h2>
 * <ol>
 *   <li><b>순위표 화면.</b> 사용자가 보는 "지금 몇 위인가"는 API의 권위 있는 값이 맞다.</li>
 *   <li><b>승점 삭감 추출.</b> 우리 계산은 경기 결과만 보므로 삭감을 모른다. 같은 경기 수에서
 *       우리 승점과 이 {@code points} 의 차이가 곧 삭감분이다. 2023-24 프리미어리그에서
 *       이 방법으로 에버턴 −8, 노팅엄 포레스트 −4 를 정확히 짚어 냈다(나머지 18팀은 완전 일치).
 *       삭감을 반영하지 않으면 에버턴이 12위로 나와 실제 15위와 세 계단 어긋난다.</li>
 * </ol>
 *
 * @see page.usetaehwan.gak.service.analysis.LeagueTable 경기 시점 순위 계산
 */
@Entity
@Table(
		name = "standing",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_standing_competition_season_team",
				columnNames = {"competition_id", "season", "team_id"}),
		indexes = @Index(
				name = "idx_standing_competition_season",
				columnList = "competition_id, season"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Standing {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "competition_id", nullable = false)
	private Competition competition;

	@Column(nullable = false)
	private Integer season;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "team_id", nullable = false)
	private Team team;

	@Column(nullable = false)
	private Integer rank;

	/** 승점. <b>승점 삭감이 이미 반영된 값</b>이다. */
	@Column(nullable = false)
	private Integer points;

	@Column(nullable = false)
	private Integer played;

	@Column(nullable = false)
	private Integer goalsFor;

	@Column(nullable = false)
	private Integer goalsAgainst;

	/**
	 * 조 이름. 정규 리그는 리그명이 그대로 온다.
	 *
	 * <p>조별리그가 있는 대회는 표가 조마다 하나씩이라, 이 값이 없으면 서로 다른 조의
	 * 1위 둘이 같은 표에 있는 것처럼 보인다.
	 */
	private String groupName;

	/** API가 붙인 순위의 의미(예: {@code "Relegation - Championship"}). 없을 수 있다. */
	private String description;

	/** 이 줄을 마지막으로 갱신한 시각 — 화면이 "언제 기준 순위인지" 밝힐 수 있어야 한다. */
	@Column(nullable = false)
	private Instant updatedAt;

	@Builder
	private Standing(Competition competition, Integer season, Team team, Integer rank,
	                 Integer points, Integer played, Integer goalsFor, Integer goalsAgainst,
	                 String groupName, String description, Instant updatedAt) {
		this.competition = competition;
		this.season = season;
		this.team = team;
		this.rank = rank;
		this.points = points;
		this.played = played;
		this.goalsFor = goalsFor;
		this.goalsAgainst = goalsAgainst;
		this.groupName = groupName;
		this.description = description;
		this.updatedAt = updatedAt;
	}

	/**
	 * 새 값으로 덮어쓴다.
	 *
	 * <p>순위표는 이력이 아니라 <b>현재 상태</b>다. 매 동기화마다 행을 쌓으면 "지금 몇 위"를
	 * 묻는 데 정렬과 중복 제거가 필요해지고, 그렇게 쌓은 이력도 동기화 주기만큼 듬성해서
	 * 경기 시점 순위로는 못 쓴다. 경기 시점은 계산이 답한다.
	 */
	public void refresh(Integer rank, Integer points, Integer played,
	                    Integer goalsFor, Integer goalsAgainst,
	                    String groupName, String description, Instant updatedAt) {
		this.rank = rank;
		this.points = points;
		this.played = played;
		this.goalsFor = goalsFor;
		this.goalsAgainst = goalsAgainst;
		this.groupName = groupName;
		this.description = description;
		this.updatedAt = updatedAt;
	}

	public int goalsDiff() {
		return goalsFor - goalsAgainst;
	}
}
