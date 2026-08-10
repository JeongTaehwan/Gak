package page.usetaehwan.gak.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
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

	/** 한 팀의 채점 완료 예측. */
	@Query("""
			select p from Prediction p
			join fetch p.fixture f
			join fetch p.team t
			where t.id = :teamId and p.isHit is not null
			order by f.kickoff asc
			""")
	List<Prediction> findScoredByTeam(@Param("teamId") Long teamId);

	/**
	 * 한 팀의 예측 전부 — 적중률 집계와 기록 목록의 입력.
	 *
	 * <p>미채점까지 함께 읽는다. 적중률의 분모에는 안 들어가지만 "채점을 기다리는 게 몇
	 * 건인지"를 화면이 알아야 한다 — 그게 0인데 예측이 있으면 채점이 멈춘 것이다.
	 *
	 * <p>화면 한 줄에 대회명·상대팀이 필요하므로 함께 읽는다. 지연 로딩에 맡기면 예측
	 * 50건에 (경기 + 대회 + 양 팀) 조회가 200번 따라붙는다.
	 */
	@EntityGraph(attributePaths = {"fixture", "fixture.competition",
			"fixture.homeTeam", "fixture.awayTeam", "team"})
	@Query("""
			select p from Prediction p
			where p.team.id = :teamId
			order by p.fixture.kickoff asc
			""")
	List<Prediction> findTeamPredictions(@Param("teamId") Long teamId);

	/**
	 * 한 팀의 <b>한 시즌</b> 예측 전부.
	 *
	 * <p>시즌을 거르지 않으면 회고에서 <b>다른 시즌 기록이 분모에 섞인다</b> — 2023-24를
	 * 보고 있는데 적중률은 전 시즌 합계인 상태다. 화면은 멀쩡하고 숫자만 틀리므로
	 * 눈치채기 어렵다.
	 *
	 * <p>시즌은 예측이 아니라 <b>경기</b>가 들고 있다. 예측 시점이 아니라 어느 시즌 경기를
	 * 맞혔는지가 이 집계의 기준이기 때문이다.
	 */
	@EntityGraph(attributePaths = {"fixture", "fixture.competition",
			"fixture.homeTeam", "fixture.awayTeam", "team"})
	@Query("""
			select p from Prediction p
			where p.team.id = :teamId and p.fixture.season = :season
			order by p.fixture.kickoff asc
			""")
	List<Prediction> findTeamPredictionsInSeason(@Param("teamId") Long teamId,
	                                             @Param("season") Integer season);
}
