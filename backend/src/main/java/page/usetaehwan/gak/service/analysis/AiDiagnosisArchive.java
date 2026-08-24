// requirements.md DG 7절
package page.usetaehwan.gak.service.analysis;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.AiDiagnosisRecord;
import page.usetaehwan.gak.repository.AiDiagnosisRecordRepository;

/**
 * AI 진단 저장분의 쓰기 경계. 이 클래스 밖에서는 {@link AiDiagnosisRecord}를 저장하지 않는다.
 *
 * <p><b>모델 호출은 이 경계 밖에 있다.</b> 외부 호출과 DB 쓰기를 한 트랜잭션에 묶으면
 * 모델의 사고(수십 초)만큼 커넥션이 잠긴다 — 동기화가 fetch 와 upsert 를 가르는 것과
 * 같은 이유다({@code FixtureSyncService} 클래스 주석). 그래서 여기는 짧은 트랜잭션만
 * 갖고, 호출 → 검증 → 저장의 순서는 {@link AiDiagnosisService}가 트랜잭션 없이 잇는다.
 *
 * <h2>동시 miss → insert 경합은 유니크 제약이 판정한다</h2>
 * <p>같은 팀을 두 요청이 동시에 열면 둘 다 "저장분 없음"을 보고 둘 다 insert 를 시도할
 * 수 있다. "조회해서 없으면 넣기"를 코드로 다시 검사해도 그 사이에 끼어드는 걸 막을 수
 * 없으므로, 판정을 DB의 유니크 제약에 맡기고 <b>진 쪽은 위반 예외를 받아 이긴 쪽 행을
 * 재조회</b>한다({@link #save}). 두 행이 남거나 요청이 500으로 죽는 경로가 둘 다 닫힌다.
 */
@Service
public class AiDiagnosisArchive {

	private static final Logger log = LoggerFactory.getLogger(AiDiagnosisArchive.class);

	private final AiDiagnosisRecordRepository repository;

	public AiDiagnosisArchive(AiDiagnosisRecordRepository repository) {
		this.repository = repository;
	}

	/** 저장 키로 조회. 리포지토리의 짧은 읽기 트랜잭션이면 충분하다. */
	public Optional<AiDiagnosisRecord> find(long teamId, int season, int windowDays, int minMatches) {
		return repository.findByTeamIdAndSeasonAndWindowDaysAndMinMatches(
				teamId, season, windowDays, minMatches);
	}

	/**
	 * 신규 저장. 동시 요청과의 insert 경합에서 지면 이긴 쪽 행을 재조회해 돌려준다.
	 *
	 * <p><b>이 메서드에는 일부러 {@code @Transactional}이 없다.</b> 붙이면 유니크 위반
	 * 시점에 트랜잭션이 rollback-only 로 굳어 catch 안의 재조회까지 같이 죽는다.
	 * insert 는 리포지토리 자체의 짧은 트랜잭션으로 끝내고, 잡는 쪽은 그 밖에 선다.
	 *
	 * @return 실제로 남은 행 — 내가 넣은 것이거나, 경합에서 이긴 쪽 것
	 */
	public AiDiagnosisRecord save(AiDiagnosisRecord fresh) {
		try {
			// flush 까지 해야 유니크 위반이 여기서 터진다 — 지연되면 잡을 곳이 없다.
			return repository.saveAndFlush(fresh);
		} catch (DataIntegrityViolationException e) {
			log.info("AI 진단 저장 경합 — 먼저 저장된 행을 재사용한다 (teamId={}, season={})",
					fresh.getTeamId(), fresh.getSeason());
			return find(fresh.getTeamId(), fresh.getSeason(),
					fresh.getWindowDays(), fresh.getMinMatches())
					// 유니크 위반이었는데 행이 없다면 원인이 다른 것이다 — 원래 예외를 살린다.
					.orElseThrow(() -> e);
		}
	}

	/**
	 * 분모가 달라진 기존 행을 새 서술로 교체한다.
	 *
	 * <p>행을 여기서 다시 읽는다 — 호출부가 트랜잭션 밖에서 읽어 둔 엔티티는 이미 준영속이라
	 * 그대로 고칠 수 없고, 그 사이 다른 요청이 먼저 교체했을 수도 있다. 같은 트랜잭션 안에서
	 * 읽고 고쳐야 더티 체킹이 온전히 탄다. 두 요청이 같은 행을 갈아 끼우면 나중 쪽이
	 * 남는데, 둘 다 검증을 통과한 최신 서술이므로 어느 쪽이든 화면은 옳다.
	 */
	@Transactional
	public void replace(AiDiagnosisRecord fresh) {
		repository.findByTeamIdAndSeasonAndWindowDaysAndMinMatches(
						fresh.getTeamId(), fresh.getSeason(),
						fresh.getWindowDays(), fresh.getMinMatches())
				.ifPresentOrElse(
						row -> row.replaceWith(fresh.getAnalyzedFixtures(),
								fresh.getHeadline(), fresh.getSub(),
								fresh.getEvidence(), fresh.getUnknowns(),
								fresh.getGeneratedAt()),
						// 그 사이 행이 사라졌으면(지우는 경로는 없지만) 신규로 넣으면 된다.
						() -> repository.save(fresh));
	}
}
