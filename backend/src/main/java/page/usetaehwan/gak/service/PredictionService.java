package page.usetaehwan.gak.service;

import java.time.Clock;
import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 예측 유스케이스. 이 앱의 핵심 규칙 — <b>예측은 반드시 킥오프 이전에만 생성</b> — 을
 * 강제하는 곳이다. 컨트롤러는 이 서비스를 통해서만 예측을 만들 수 있고, 서비스는
 * 주입된 {@link Clock}이 준 "지금"을 기준으로 시점을 판정한다.
 *
 * <p>계층 규칙: controller → service → repository → domain (단방향). 서비스는 도메인
 * 규칙을 조율할 뿐, 도메인 자체의 불변식(킥오프 이전 검증)은 {@link Prediction#create}가 지킨다.
 */
@Service
public class PredictionService {

	private final PredictionRepository predictionRepository;
	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;
	private final Clock clock;

	public PredictionService(PredictionRepository predictionRepository,
	                         FixtureRepository fixtureRepository,
	                         TeamRepository teamRepository,
	                         Clock clock) {
		this.predictionRepository = predictionRepository;
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
		this.clock = clock;
	}

	/**
	 * 예측을 만든다. 지금 시각이 킥오프 이후이면 생성하지 않는다(사후 예측 금지).
	 *
	 * @param teamId 누구 관점의 예측인가. 승/무/패는 주어가 있어야 채점된다
	 * @throws NoSuchElementException   해당 fixture나 team이 없을 때
	 * @throws IllegalArgumentException 킥오프 이후에 예측을 시도하거나, 그 팀이 이 경기에
	 *                                  뛰지 않을 때(Prediction.create가 던짐)
	 */
	@Transactional
	public Prediction createPrediction(Long fixtureId, Long teamId, Pick pick) {
		Fixture fixture = fixtureRepository.findById(fixtureId)
				.orElseThrow(() -> new NoSuchElementException(
						"경기를 찾을 수 없습니다. fixtureId=" + fixtureId));
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new NoSuchElementException(
						"팀을 찾을 수 없습니다. teamId=" + teamId));

		Instant now = Instant.now(clock);

		// 킥오프 이전 검증은 도메인 팩터리가 최종적으로 책임진다.
		Prediction prediction = Prediction.create(fixture, team, pick, now);
		return predictionRepository.save(prediction);
	}
}
