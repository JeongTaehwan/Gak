package page.usetaehwan.gak.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
}
