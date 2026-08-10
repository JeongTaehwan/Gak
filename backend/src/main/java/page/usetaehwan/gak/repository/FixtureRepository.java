package page.usetaehwan.gak.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Fixture;

public interface FixtureRepository extends JpaRepository<Fixture, Long> {

	/**
	 * 한 팀이 홈/원정 어느 쪽이든 참여한 경기를 킥오프 순으로.
	 * 리그·컵·유럽대항전을 가로지른 "통합 일정"의 기초 질의다.
	 *
	 * <p>연관 엔티티를 건드리지 않는 용도(개수·간격 계산 등)에만 쓴다. 화면에 뿌릴
	 * 목록이라면 {@link #findTeamScheduleWithDetails(Long)}를 써야 N+1이 나지 않는다.
	 */
	List<Fixture> findByHomeTeamIdOrAwayTeamIdOrderByKickoffAsc(Long homeTeamId, Long awayTeamId);

	/**
	 * 통합 일정 화면용 — 대회·양 팀·경기장을 <b>한 번의 질의로</b> 함께 읽는다.
	 *
	 * <p>{@code @EntityGraph}가 없으면 목록 1회 + 경기마다 (대회·홈·원정·경기장) 최대 4회의
	 * 지연 로딩이 더 나간다. 경기 50건이면 1 + 200회. 이게 N+1이다.
	 * 여기 연관은 전부 {@code @ManyToOne}(한 경기당 한 행)이라 조인해도 결과 행이
	 * 부풀지 않고, 페이징도 그대로 쓸 수 있다.
	 *
	 * <p>홈/원정 컬럼이 나뉘어 있어 조건이 OR이 되는데, 그 대신
	 * {@code (home_team_id, kickoff)} / {@code (away_team_id, kickoff)} 인덱스를 각각 두어
	 * 옵티마이저가 두 인덱스를 훑고 합치도록 했다.
	 */
	@EntityGraph(attributePaths = {"competition", "homeTeam", "awayTeam", "venue"})
	@Query("""
			select f from Fixture f
			where f.homeTeam.id = :teamId or f.awayTeam.id = :teamId
			order by f.kickoff asc
			""")
	List<Fixture> findTeamScheduleWithDetails(@Param("teamId") Long teamId);

	/** 기간 내 경기(밀집도·간격 계산의 입력). */
	List<Fixture> findByKickoffBetweenOrderByKickoffAsc(Instant from, Instant to);

	/** 기간 내 경기 + 연관 엔티티 동시 로딩. 오늘의 경기 목록 등에 쓴다. */
	@EntityGraph(attributePaths = {"competition", "homeTeam", "awayTeam", "venue"})
	@Query("select f from Fixture f where f.kickoff between :from and :to order by f.kickoff asc")
	List<Fixture> findByKickoffBetweenWithDetails(@Param("from") Instant from,
	                                              @Param("to") Instant to);

	/** 대회·시즌 단위 조회(동기화 결과 확인, 대회별 일정). */
	List<Fixture> findByCompetitionIdAndSeasonOrderByKickoffAsc(Long competitionId, Integer season);

	int countByCompetitionIdAndSeason(Long competitionId, Integer season);

	// ─────────────────────────────────────────────────────────────────────────
	// 입력 화면 — 시즌과 선택 가능 팀을 파생 계산한다 (requirements.md 2~3장)
	//
	// 셋 다 competition.selectable 만 본다. "선택 기준 대회"는 시드가 정하고, 팀 목록도
	// 시즌 목록도 저장하지 않는다 — 저장하는 순간 승격·강등이 일어난 해의 과거 목록이
	// 조용히 틀려진다.
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * 조회 가능 시즌 — 선택 기준 대회 6개 중 하나라도 <b>경기 데이터가 있는</b> 시즌.
	 * 내림차순(최신 먼저)이라 "지난 시즌 보기"가 목록을 그대로 훑는다.
	 *
	 * <p>여기는 치렀는지를 묻지 않는다. 일정만 들어온 시즌도 <b>조회는</b> 할 수 있어야
	 * 하고(타임라인은 예정 경기를 그린다), "지금이 어느 시즌인가"의 판정은
	 * {@link #findLatestPlayedSelectableSeason(Instant)}가 따로 한다.
	 */
	@Query("""
			select distinct f.season from Fixture f
			where f.competition.selectable = true and f.season is not null
			order by f.season desc
			""")
	List<Integer> findSelectableSeasons();

	/**
	 * 현재 시즌 자동 판정 — <b>치른 경기가 있는 시즌 중 가장 큰 season</b>.
	 *
	 * <p>포함 조건을 {@code AnalysisPeriodResolver} 와 <b>글자 그대로 맞춘다</b>
	 * (NS·LIVE·종료 + 킥오프가 지났을 것). 한쪽만 다르게 세면 입력 화면이 고른 시즌과
	 * 진단이 실제로 본 시즌이 갈리는데, 그 어긋남은 아무 에러 없이 다른 해의 숫자를
	 * 보여 주는 방식으로만 드러난다.
	 *
	 * <p>단순히 가장 큰 시즌을 고르지 않는 이유도 같다 — 다음 시즌 일정이 먼저 들어오면
	 * (프리시즌에 흔하다) 한 경기도 안 치른 시즌이 "현재"가 되어 화면이 통째로 빈다.
	 */
	@Query("""
			select max(f.season) from Fixture f
			where f.competition.selectable = true
			  and f.season is not null
			  and f.kickoff is not null
			  and f.kickoff <= :asOf
			  and f.status in (page.usetaehwan.gak.domain.FixtureStatus.NS,
			                   page.usetaehwan.gak.domain.FixtureStatus.LIVE,
			                   page.usetaehwan.gak.domain.FixtureStatus.FT,
			                   page.usetaehwan.gak.domain.FixtureStatus.AET,
			                   page.usetaehwan.gak.domain.FixtureStatus.PEN)
			""")
	Integer findLatestPlayedSelectableSeason(@Param("asOf") Instant asOf);

	/**
	 * 이 팀이 그 시즌에 선택 기준 대회의 경기를 가졌는가.
	 *
	 * <p>없다고 해서 2부 소속이라거나 동기화가 빠졌다고 <b>단정하지 않는다</b> — 우리가
	 * 아는 건 "우리 DB의 선택 기준 대회에 이 팀 경기가 없다"까지다.
	 */
	@Query("""
			select count(f) > 0 from Fixture f
			where f.competition.selectable = true and f.season = :season
			  and (f.homeTeam.id = :teamId or f.awayTeam.id = :teamId)
			""")
	boolean hasSelectableFixture(@Param("teamId") Long teamId, @Param("season") Integer season);

	/**
	 * 이 팀이 그 시즌에 뛴 선택 기준 대회들. 시즌 표기가 "2023-24"인지 "2025"인지를
	 * 가르는 {@code calendarSeason} 을 여기서 읽는다 — 날짜로 되짚으면 시즌 초에
	 * 유럽 리그가 "2026 시즌"이 된다.
	 */
	@Query("""
			select distinct f.competition from Fixture f
			where f.competition.selectable = true and f.season = :season
			  and (f.homeTeam.id = :teamId or f.awayTeam.id = :teamId)
			""")
	List<Competition> findSelectableCompetitions(@Param("teamId") Long teamId,
	                                             @Param("season") Integer season);

	/**
	 * 그 시즌 순위표를 볼 리그 — 팀이 그 시즌에 뛴 <b>LEAGUE</b> 경기에서 찾는다.
	 *
	 * <p>시즌을 인자로 받는 게 핵심이다. 예전에는 "그 팀의 가장 최근 리그 경기"로 골라서,
	 * 2023-24 타임라인 옆에 다른 시즌 순위표가 뜰 수 있었다.
	 */
	@Query("""
			select distinct f.competition from Fixture f
			where f.competition.type = page.usetaehwan.gak.domain.CompetitionType.LEAGUE
			  and f.season = :season
			  and (f.homeTeam.id = :teamId or f.awayTeam.id = :teamId)
			""")
	List<Competition> findLeaguesInSeason(@Param("teamId") Long teamId,
	                                      @Param("season") Integer season);
}
