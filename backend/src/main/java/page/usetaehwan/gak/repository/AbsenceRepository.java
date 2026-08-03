package page.usetaehwan.gak.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.Absence;

public interface AbsenceRepository extends JpaRepository<Absence, Long> {

	/**
	 * 한 팀의 결장 기록 전체 — 진단 계산의 입력.
	 *
	 * <p>선수 이름을 함께 읽는다. 경기별로 "누가 빠졌나"를 보여 주려면 어차피 전부
	 * 필요한데, 지연 로딩에 맡기면 결장 300건에 선수 조회 300번이 따라붙는다.
	 */
	@EntityGraph(attributePaths = {"player", "fixture"})
	@Query("""
			select a from Absence a
			where a.team.id = :teamId
			order by a.fixture.kickoff asc
			""")
	List<Absence> findTeamAbsencesWithPlayer(@Param("teamId") Long teamId);

	/**
	 * 동기화가 기존 행을 찾을 때 쓴다. {@code (fixture, player)}가 자연키라
	 * 경기 id 목록으로 한 번에 끌어와 메모리에서 맞춘다 — 건마다 조회하면 300 쿼리다.
	 */
	@Query("""
			select a from Absence a
			where a.team.id = :teamId and a.fixture.id in :fixtureIds
			""")
	List<Absence> findByTeamAndFixtureIds(@Param("teamId") Long teamId,
	                                      @Param("fixtureIds") Collection<Long> fixtureIds);

	long countByTeamId(Long teamId);
}
