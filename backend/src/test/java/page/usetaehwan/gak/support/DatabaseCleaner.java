package page.usetaehwan.gak.support;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.repository.AbsenceRepository;
import page.usetaehwan.gak.repository.AiDiagnosisRecordRepository;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.NewsItemRepository;
import page.usetaehwan.gak.repository.PlayerRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.StandingRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;

/**
 * 테스트 사이 DB 비우기 — <b>참조 순서를 한곳에 모은다.</b>
 *
 * <p>테스트마다 {@code deleteAll()}을 손으로 나열하면, 새 엔티티가 생길 때마다 모든
 * 테스트를 찾아 고쳐야 한다. 실제로 그랬다: {@code Prediction}이 {@code Fixture}를
 * 참조하게 되자 진단 테스트가 깨졌고, {@code Absence}가 생기자 동기화 테스트가 또
 * 깨졌다. 둘 다 "테스트가 잘못된" 게 아니라 <b>정리 순서를 아는 곳이 없었던 것</b>이다.
 *
 * <p>여기서는 참조하는 쪽부터 지운다. 순서는 아래 한 곳만 보면 된다.
 *
 * <pre>
 *   absence ─▶ fixture, player, team
 *   prediction ─▶ fixture, team
 *   fixture ─▶ competition, team, venue
 *   team ─▶ venue
 * </pre>
 *
 * <p>{@code @Component}가 아니라 {@code @TestComponent}인 이유: 운영 컨텍스트에 "DB를 다
 * 지우는 빈"이 실려 있을 이유가 없다.
 */
@TestComponent
public class DatabaseCleaner {

	private final AbsenceRepository absenceRepository;
	private final StandingRepository standingRepository;
	private final PredictionRepository predictionRepository;
	private final FixtureRepository fixtureRepository;
	private final PlayerRepository playerRepository;
	private final SyncLogRepository syncLogRepository;
	private final TeamRepository teamRepository;
	private final VenueRepository venueRepository;
	private final CompetitionRepository competitionRepository;
	private final NewsItemRepository newsItemRepository;
	private final AiDiagnosisRecordRepository aiDiagnosisRecordRepository;

	public DatabaseCleaner(AbsenceRepository absenceRepository,
	                       StandingRepository standingRepository,
	                       PredictionRepository predictionRepository,
	                       FixtureRepository fixtureRepository,
	                       PlayerRepository playerRepository,
	                       SyncLogRepository syncLogRepository,
	                       TeamRepository teamRepository,
	                       VenueRepository venueRepository,
	                       CompetitionRepository competitionRepository,
	                       NewsItemRepository newsItemRepository,
	                       AiDiagnosisRecordRepository aiDiagnosisRecordRepository) {
		this.absenceRepository = absenceRepository;
		this.standingRepository = standingRepository;
		this.predictionRepository = predictionRepository;
		this.fixtureRepository = fixtureRepository;
		this.playerRepository = playerRepository;
		this.syncLogRepository = syncLogRepository;
		this.teamRepository = teamRepository;
		this.venueRepository = venueRepository;
		this.competitionRepository = competitionRepository;
		this.newsItemRepository = newsItemRepository;
		this.aiDiagnosisRecordRepository = aiDiagnosisRecordRepository;
	}

	/** 대회는 남긴다 — 시더가 매 테스트마다 다시 심으므로 지우면 그 일을 두 번 한다. */
	@Transactional
	public void clearAllButCompetitions() {
		absenceRepository.deleteAllInBatch();
		standingRepository.deleteAllInBatch();   // team·competition 을 참조한다
		predictionRepository.deleteAllInBatch();
		fixtureRepository.deleteAllInBatch();
		playerRepository.deleteAllInBatch();
		syncLogRepository.deleteAllInBatch();
		teamRepository.deleteAllInBatch();
		venueRepository.deleteAllInBatch();
		// 뉴스는 다른 층이라 FK 가 없다 — 순서 어디에 놓아도 되지만,
		// 여기 한 줄이 없으면 테스트끼리 소식이 새어 나간다.
		newsItemRepository.deleteAllInBatch();
		// AI 진단 저장분도 FK 없는 별도 층이다. 배치 삭제가 아닌 이유: 근거·unknowns 가
		// @ElementCollection 이라 부모만 배치로 지우면 자식 표의 FK 가 막는다.
		aiDiagnosisRecordRepository.deleteAll();
	}

	/** 대회까지 전부. */
	@Transactional
	public void clearAll() {
		clearAllButCompetitions();
		competitionRepository.deleteAllInBatch();
	}
}
