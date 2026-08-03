package page.usetaehwan.gak.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.Prediction;

public interface PredictionRepository extends JpaRepository<Prediction, Long> {

	List<Prediction> findByFixtureId(Long fixtureId);

	/** 채점 완료된 예측만(적중률 집계용). */
	List<Prediction> findByIsHitNotNull();

	/**
	 * 아직 채점되지 않았고 경기는 끝난 예측 — 채점 배치의 입력.
	 *
	 * <p>여기 걸린 상태 조건은 <b>싼 사전 필터</b>다. 최종 판정은 서비스가
	 * {@code SchedulePolicy.countsForForm}으로 한 번 더 한다. 포함/제외 규칙을 JPQL에
	 * 복사해 두면 폼 집계와 채점이 서로 다른 답을 내기 시작하는데, 그 어긋남은 적중률이
	 * 조용히 틀어지는 방식으로만 드러난다.
	 *
	 * <p>{@code join fetch}로 경기·팀을 함께 읽는다. 예측 100건을 채점하면서 경기와 팀을
	 * 지연 로딩하면 그것만 200번 더 나간다.
	 */
	@Query("""
			select p from Prediction p
			join fetch p.fixture f
			join fetch p.team
			where p.isHit is null
			  and f.status in (page.usetaehwan.gak.domain.FixtureStatus.FT,
			                   page.usetaehwan.gak.domain.FixtureStatus.AET,
			                   page.usetaehwan.gak.domain.FixtureStatus.PEN)
			order by f.kickoff asc
			""")
	List<Prediction> findPendingScoring();

	/** 한 팀의 채점 완료 예측(적중률 집계용). */
	@Query("""
			select p from Prediction p
			join fetch p.fixture f
			join fetch p.team t
			where t.id = :teamId and p.isHit is not null
			order by f.kickoff asc
			""")
	List<Prediction> findScoredByTeam(@Param("teamId") Long teamId);
}
