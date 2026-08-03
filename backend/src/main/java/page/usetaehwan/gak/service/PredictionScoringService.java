package page.usetaehwan.gak.service;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.service.analysis.SchedulePolicy;

/**
 * 예측 채점 — 끝난 경기의 실제 결과로 적중 여부를 매긴다.
 *
 * <p>예측 생성이 "킥오프 이전에만"이라면, 채점은 그 반대쪽 짝이다. 이 둘이 다 있어야
 * 적중률이라는 숫자가 성립한다. 채점이 없으면 예측은 영원히 {@code isHit = null}로 남고,
 * 적중률의 분모가 0인 채로 화면에 "기록 없음"만 뜬다.
 *
 * <h2>한 번만 채점한다</h2>
 * <p>이미 채점된 예측은 다시 건드리지 않는다({@code isHit is null}로만 뽑는다). 덕분에
 * 배치를 몇 번을 돌려도 결과가 같다(멱등). 나중에 API가 스코어를 정정하면 그 예측의
 * 채점은 옛 사실에 머무는데, 이건 알고 택한 것이다 — 한 번 매긴 성적이 나중에 조용히
 * 뒤집히는 쪽이 더 나쁘다. 적중률은 "그때 그 결과로 매긴 기록"이어야 신뢰할 수 있다.
 * (정정이 잦아지면 재채점이 아니라 <b>정정 이력</b>을 따로 남기는 쪽으로 간다.)
 *
 * <h2>결과를 모르면 건너뛴다</h2>
 * <p>상태가 FT여도 득점이 안 들어온 순간이 실제로 있다(동기화가 상태를 먼저 받는다).
 * 그때 채점하면 {@code resultFor}가 null을 주고, 그걸 그대로 쓰면 "틀렸다"로 기록된다.
 * <b>모르는 것과 틀린 것은 다르다.</b> 그래서 판정 가능한 것만 채점하고 나머지는 다음
 * 회차로 넘긴다 — 어차피 매시간 다시 돈다.
 *
 * <h2>포함/제외 규칙은 한곳에서만</h2>
 * <p>"어떤 경기가 결과로 인정되는가"는 {@link SchedulePolicy#countsForForm}이 정한다.
 * 폼 집계와 채점이 같은 함수를 봐야 "폼에는 들어갔는데 채점은 안 된 경기" 같은 게 안 생긴다.
 */
@Service
public class PredictionScoringService {

	private static final Logger log = LoggerFactory.getLogger(PredictionScoringService.class);

	private final PredictionRepository predictionRepository;

	public PredictionScoringService(PredictionRepository predictionRepository) {
		this.predictionRepository = predictionRepository;
	}

	/**
	 * 채점 한 회차의 결과.
	 *
	 * @param scored    이번에 채점한 예측 수
	 * @param hits      그중 적중
	 * @param deferred  결과를 아직 못 믿어 다음 회차로 넘긴 수(득점 미도착 등)
	 */
	public record ScoringReport(int scored, int hits, int deferred) {

		public int candidates() {
			return scored + deferred;
		}

		public int misses() {
			return scored - hits;
		}
	}

	/** 끝났는데 아직 채점되지 않은 예측을 전부 채점한다. 몇 번을 돌려도 결과가 같다. */
	@Transactional
	public ScoringReport scorePending() {
		List<Prediction> pending = predictionRepository.findPendingScoring();
		if (pending.isEmpty()) {
			return new ScoringReport(0, 0, 0);
		}

		int scored = 0;
		int hits = 0;
		int deferred = 0;

		for (Prediction prediction : pending) {
			Fixture fixture = prediction.getFixture();

			// 상태는 끝났다고 하는데 득점이 아직 안 온 경우 — 다음 회차로 넘긴다.
			if (!SchedulePolicy.countsForForm(fixture)) {
				deferred++;
				continue;
			}

			Pick actual = fixture.resultFor(prediction.getTeam().getId());
			if (actual == null) {
				// 여기 오면 예측 대상 팀이 그 경기에 없다는 뜻이다(생성 시 막았으므로 정상적으론
				// 안 온다). 조용히 넘기면 영영 안 풀리므로 눈에 띄게 남긴다.
				log.warn("채점 불가 — 예측 대상 팀이 이 경기에 없습니다. predictionId={}, fixtureId={}, teamId={}",
						prediction.getId(), fixture.getId(), prediction.getTeam().getId());
				deferred++;
				continue;
			}

			prediction.resolve(actual);
			scored++;
			if (Boolean.TRUE.equals(prediction.getIsHit())) {
				hits++;
			}
		}

		// 변경은 더티 체킹으로 반영된다(트랜잭션 경계가 이 메서드다).
		if (scored > 0 || deferred > 0) {
			log.info("예측 채점: {}건 채점(적중 {} / 빗나감 {}), {}건 보류",
					scored, hits, scored - hits, deferred);
		}
		return new ScoringReport(scored, hits, deferred);
	}
}
