// requirements.md TL 8절 — (팀, 시즌) 결장 수집 이력 조회
package page.usetaehwan.gak.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.AbsenceSyncLog;

public interface AbsenceSyncLogRepository extends JpaRepository<AbsenceSyncLog, Long> {

	/**
	 * 이 팀·시즌의 결장 데이터를 마지막으로 성공 동기화한 시각. 비어 있으면 <b>받은 적이
	 * 없다</b>는 뜻이고, 그때의 "결장 0명"은 사실이 아니라 미수집이다.
	 */
	@Query("""
			select max(a.startedAt) from AbsenceSyncLog a
			where a.teamId = :teamId and a.season = :season
			  and a.status = page.usetaehwan.gak.domain.SyncStatus.SUCCESS
			""")
	Optional<Instant> findLastSuccessAt(@Param("teamId") Long teamId,
	                                    @Param("season") Integer season);
}
