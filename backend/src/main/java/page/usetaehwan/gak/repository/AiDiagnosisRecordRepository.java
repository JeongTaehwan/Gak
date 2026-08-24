// requirements.md DG 7절
package page.usetaehwan.gak.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import page.usetaehwan.gak.domain.AiDiagnosisRecord;

public interface AiDiagnosisRecordRepository extends JpaRepository<AiDiagnosisRecord, Long> {

	/**
	 * 저장 키 조회 — (팀, 시즌, 밀집 기준) 조합당 한 행이 유니크 제약으로 보장된다.
	 *
	 * <p>파생 쿼리가 아니라 JPQL 인 이유: 메서드 이름 파생은 {@code MinMatches}의
	 * {@code Matches}를 정규식 연산 키워드로 읽어 "min 속성 없음"으로 깨진다.
	 */
	@Query("""
			select r from AiDiagnosisRecord r
			where r.teamId = :teamId and r.season = :season
			and r.windowDays = :windowDays and r.minMatches = :minMatches
			""")
	Optional<AiDiagnosisRecord> findByTeamIdAndSeasonAndWindowDaysAndMinMatches(
			@Param("teamId") long teamId, @Param("season") int season,
			@Param("windowDays") int windowDays, @Param("minMatches") int minMatches);
}
