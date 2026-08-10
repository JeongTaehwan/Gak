package page.usetaehwan.gak.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.Team;

public interface TeamRepository extends JpaRepository<Team, Long> {

	Optional<Team> findByCode(String code);

	/**
	 * 그 시즌 <b>선택 가능한 팀</b> — 저장된 목록이 아니라 경기에서 파생 계산한다
	 * (requirements.md 2장 · domain.md "팀 선택").
	 *
	 * <p>{@code team} 테이블에는 805개가 들어 있다. FA컵 예선까지 동기화하면서 비리그
	 * 클럽 711개가 따라 들어왔기 때문인데, 그걸 그대로 드롭다운에 올리면 8부 리그가 섞인다.
	 * <b>선택 기준 대회에 경기가 있는 팀</b>만 세면 그 711개가 별도 필터 없이 자연히 빠진다.
	 *
	 * <p>시즌이 인자인 것이 핵심이다. "지금 1부인 팀"으로 고정하면 강등된 팀의 과거를
	 * 영영 볼 수 없다 — 회고를 지원하기로 한 이상 성립하지 않는다. 2023-24를 조회하면
	 * <b>그때</b> 1부였던 팀이 나온다.
	 *
	 * <p>정렬은 영문 원본명 기준이다. 표기는 한글이 우선이지만(그건 파생값이라 DB가 모른다)
	 * 정렬만은 어느 DB에서나 같은 순서가 나오는 컬럼으로 한다.
	 */
	@Query("""
			select distinct t from Team t
			where exists (
			    select 1 from Fixture f
			    where f.competition.selectable = true and f.season = :season
			      and (f.homeTeam.id = t.id or f.awayTeam.id = t.id)
			)
			order by t.name asc
			""")
	List<Team> findSelectableInSeason(@Param("season") Integer season);
}
