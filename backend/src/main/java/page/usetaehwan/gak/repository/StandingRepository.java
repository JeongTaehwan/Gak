package page.usetaehwan.gak.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.Standing;

public interface StandingRepository extends JpaRepository<Standing, Long> {

	/**
	 * 한 대회·시즌의 순위표 전체.
	 *
	 * <p>{@code @EntityGraph} 로 팀을 함께 끌어온다 — 20줄짜리 표에서 팀 이름을 읽으면
	 * 그것만으로 쿼리가 20번 더 나간다.
	 */
	@EntityGraph(attributePaths = {"team"})
	@Query("""
			select s from Standing s
			where s.competition.id = :competitionId and s.season = :season
			order by s.rank asc
			""")
	List<Standing> findTable(@Param("competitionId") Long competitionId, @Param("season") Integer season);

	List<Standing> findByCompetitionIdAndSeason(Long competitionId, Integer season);
}
