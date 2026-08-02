package page.usetaehwan.gak.repository;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.SyncLog;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

	/**
	 * 특정 시각 이후로 소모한 API 요청 수 합. 남은 일일 예산을 여기서 얻는다.
	 *
	 * <p>메모리에 카운터를 두지 않고 매번 DB에서 세는 이유: 앱을 재시작해도 오늘 쓴 양이
	 * 유지돼야 한다. 개발 중 재시작이 잦은데 카운터가 리셋되면 예산 제어가 무의미해진다.
	 */
	@Query("select coalesce(sum(s.requestCount), 0) from SyncLog s where s.startedAt >= :from")
	int sumRequestCountSince(@Param("from") Instant from);

	/** 대회별 마지막 성공 시각. 다음 동기화 대상 선정의 입력. */
	@Query("""
			select s.competitionId as competitionId, max(s.startedAt) as lastSyncedAt
			from SyncLog s
			where s.status = page.usetaehwan.gak.domain.SyncStatus.SUCCESS
			group by s.competitionId
			""")
	List<LastSyncView> findLastSuccessPerCompetition();

	/** 최근 이력(운영 확인용). */
	List<SyncLog> findTop50ByOrderByStartedAtDesc();

	List<SyncLog> findByCompetitionIdOrderByStartedAtDesc(Long competitionId);

	/** 프로젝션 — 엔티티 전체를 끌어오지 않고 필요한 두 값만 읽는다. */
	interface LastSyncView {
		Long getCompetitionId();

		Instant getLastSyncedAt();
	}
}
