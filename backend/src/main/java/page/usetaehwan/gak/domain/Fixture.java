package page.usetaehwan.gak.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 한 경기. API-Football의 fixture에 대응하며, 리그·컵·유럽대항전을 가로질러
 * 팀의 "실제 일정"을 이루는 원자 단위다.
 *
 * <p>사실만 저장한다. 경기 간격/밀집도/폼/승점률/이동거리 같은 파생값은 저장하지 않고,
 * 이 표의 kickoff·결과·(연결된 Venue 좌표)로부터 그때그때 계산한다.
 */
@Entity
@Table(name = "fixture")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Fixture {

	/** API-Football fixture id를 PK로 사용. */
	@Id
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "competition_id")
	private Competition competition;

	/** 시즌(연도). API-Football은 시작연도 정수로 준다(예 2024). */
	private Integer season;

	/** 라운드 문자열(예 "Regular Season - 5", "Round of 16"). */
	private String round;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "home_team_id")
	private Team homeTeam;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "away_team_id")
	private Team awayTeam;

	/** 경기장. 미정/미제공일 수 있어 nullable. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "venue_id")
	private Venue venue;

	/** 킥오프(UTC). API의 timestamp/date를 Instant로 저장. */
	@Column(nullable = false)
	private Instant kickoff;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private FixtureStatus status;

	/** 진행 분(status.elapsed). 종료/미시작이면 null일 수 있음. */
	private Integer elapsed;

	/** 현재/최종 득점(API goals). 미시작이면 null. */
	private Integer goalsHome;
	private Integer goalsAway;

	// --- 스코어 상세(국면별) : Score 값 타입을 네 번 임베드 ---

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "home", column = @Column(name = "halftime_home")),
			@AttributeOverride(name = "away", column = @Column(name = "halftime_away"))
	})
	private Score halftime;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "home", column = @Column(name = "fulltime_home")),
			@AttributeOverride(name = "away", column = @Column(name = "fulltime_away"))
	})
	private Score fulltime;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "home", column = @Column(name = "extratime_home")),
			@AttributeOverride(name = "away", column = @Column(name = "extratime_away"))
	})
	private Score extratime;

	@Embedded
	@AttributeOverrides({
			@AttributeOverride(name = "home", column = @Column(name = "penalty_home")),
			@AttributeOverride(name = "away", column = @Column(name = "penalty_away"))
	})
	private Score penalty;

	@Builder
	private Fixture(Long id, Competition competition, Integer season, String round,
	               Team homeTeam, Team awayTeam, Venue venue, Instant kickoff,
	               FixtureStatus status, Integer elapsed,
	               Integer goalsHome, Integer goalsAway,
	               Score halftime, Score fulltime, Score extratime, Score penalty) {
		this.id = id;
		this.competition = competition;
		this.season = season;
		this.round = round;
		this.homeTeam = homeTeam;
		this.awayTeam = awayTeam;
		this.venue = venue;
		this.kickoff = kickoff;
		this.status = status;
		this.elapsed = elapsed;
		this.goalsHome = goalsHome;
		this.goalsAway = goalsAway;
		this.halftime = halftime;
		this.fulltime = fulltime;
		this.extratime = extratime;
		this.penalty = penalty;
	}

	/** 결과가 확정된 경기인가(예측 채점 가능 여부). */
	public boolean isFinished() {
		return status != null && status.isFinished();
	}

	/**
	 * 특정 팀 관점의 실제 결과(승/무/패) 계산 — 파생값이라 저장하지 않는다.
	 * 종료 전이거나 득점 정보가 없으면 null.
	 *
	 * @param teamId 진단 대상 팀 id(homeTeam 또는 awayTeam)
	 */
	public Pick resultFor(Long teamId) {
		if (!isFinished() || goalsHome == null || goalsAway == null) {
			return null;
		}
		int diff = goalsHome - goalsAway;
		boolean isHome = homeTeam != null && homeTeam.getId().equals(teamId);
		boolean isAway = awayTeam != null && awayTeam.getId().equals(teamId);
		if (!isHome && !isAway) {
			return null;
		}
		if (diff == 0) {
			return Pick.D;
		}
		boolean homeWon = diff > 0;
		return (homeWon == isHome) ? Pick.W : Pick.L;
	}
}
