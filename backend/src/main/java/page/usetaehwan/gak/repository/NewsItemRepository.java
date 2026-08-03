package page.usetaehwan.gak.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.NewsItem;

public interface NewsItemRepository extends JpaRepository<NewsItem, Long> {

	/**
	 * 이미 저장된 기사인가 — 수집이 중복을 거르는 통로.
	 *
	 * <p>건마다 묻지 않고 이번 회차의 키를 통째로 넘겨 한 번에 확인한다. 피드 4개면
	 * 100건 남짓이라 건별 조회는 그대로 100쿼리가 된다({@code AbsenceRepository}와 같은 이유).
	 */
	@Query("select n.dedupKey from NewsItem n where n.dedupKey in :keys")
	List<String> findExistingKeys(@Param("keys") Collection<String> keys);

	/**
	 * 화면이 읽는 유일한 질의 — 한 팀의 최신 소식.
	 *
	 * <p><b>꺼진 소스는 여기서 빠진다.</b> 수집만 멈추고 저장분을 계속 보여 주면
	 * 스위치를 내렸다고 할 수 없다({@code NewsQueryService} 주석 참고).
	 */
	@Query("""
			select n from NewsItem n
			where n.teamId = :teamId and n.sourceKey in :sourceKeys
			order by n.publishedAt desc
			""")
	List<NewsItem> findTeamNews(@Param("teamId") Long teamId,
	                            @Param("sourceKeys") Collection<String> sourceKeys,
	                            Limit limit);

	/**
	 * 아직 갈래가 없는 것들 — 태거가 집어 갈 대상.
	 *
	 * <p>최신순인 이유: 태깅이 밀리거나 실패해도 <b>사용자가 실제로 보는 화면 위쪽부터</b>
	 * 채워진다. 오래된 것부터 채우면 밀린 만큼 첫 화면이 계속 비어 있다.
	 */
	@Query("""
			select n from NewsItem n
			where n.category is null and n.sourceKey in :sourceKeys
			order by n.publishedAt desc
			""")
	List<NewsItem> findUntagged(@Param("sourceKeys") Collection<String> sourceKeys, Limit limit);

	/**
	 * 한 소스의 저장분을 지운다 — 매체가 내려 달라고 했을 때의 마지막 단계.
	 * 설정으로 끄면 화면에서 즉시 사라지고, 이걸 부르면 DB에서도 없어진다.
	 */
	@Modifying
	@Query("delete from NewsItem n where n.sourceKey = :sourceKey")
	int deleteBySourceKey(@Param("sourceKey") String sourceKey);

	/**
	 * 보관 기간이 지난 것을 지운다.
	 *
	 * <p>전체를 읽어 메모리에서 거르지 않는다 — 소식은 계속 쌓이는 테이블이라
	 * {@code findAll()} 은 시간이 갈수록 느려지고, 결국 스케줄러를 붙잡는다.
	 */
	@Modifying
	@Query("delete from NewsItem n where n.publishedAt < :cutoff")
	int deletePublishedBefore(@Param("cutoff") java.time.Instant cutoff);

	long countBySourceKey(String sourceKey);

	long countByTeamId(Long teamId);
}
