package page.usetaehwan.gak.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Pick;
import page.usetaehwan.gak.domain.Prediction;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.dto.prediction.PredictionAccuracy;
import page.usetaehwan.gak.dto.prediction.PredictionRecord;
import page.usetaehwan.gak.repository.PredictionRepository;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 적중률 집계. 저장된 채점 결과를 세기만 한다 — <b>여기서 채점하지 않는다.</b>
 *
 * <p>조회할 때 채점까지 하면, 조회 시점에 따라 적중률이 달라지고 누가 언제 화면을 열었는지가
 * 기록에 영향을 준다. 채점은 {@link PredictionScoringService}가 배치로만 한다.
 *
 * <p>파생값이라 저장하지 않는다(프로젝트 원칙). 예측 기록이 늘면 자동으로 따라온다.
 *
 * <h2>⚠️ 한 번에 한 시즌만 센다</h2>
 * <p>예전에는 팀의 <b>모든 시즌</b> 예측을 한꺼번에 집계했다. 그러면 2023-24 회고를 보면서
 * 읽는 적중률이 실제로는 여러 해의 합계가 된다 — 분모에 다른 시간축이 섞이는 것이고,
 * 이 앱이 파는 게 적중률이라 특히 나쁘다. 시즌은 입력 화면이 정해서 넘겨준다.
 */
@Service
public class PredictionAccuracyService {

	private final PredictionRepository predictionRepository;
	private final TeamRepository teamRepository;

	public PredictionAccuracyService(PredictionRepository predictionRepository,
	                                 TeamRepository teamRepository) {
		this.predictionRepository = predictionRepository;
		this.teamRepository = teamRepository;
	}

	/**
	 * @param season      집계할 시즌. 이 시즌 경기에 걸린 예측만 센다
	 * @param recentLimit 기록 목록에 몇 건까지 실을지
	 * @throws NoSuchElementException 팀이 없을 때
	 */
	@Transactional(readOnly = true)
	public PredictionAccuracy of(Long teamId, Integer season, int recentLimit) {
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId));

		List<Prediction> all = predictionRepository.findTeamPredictionsInSeason(teamId, season);
		if (all.isEmpty()) {
			// 이 시즌 기록이 없다는 뜻이지 이 팀의 기록이 없다는 뜻이 아니다.
			return PredictionAccuracy.empty(team.getId(), team.displayName(), season);
		}

		int hits = 0;
		int misses = 0;
		int pending = 0;
		Map<Pick, int[]> tally = new EnumMap<>(Pick.class); // [예측 횟수, 적중]

		for (Prediction p : all) {
			int[] counts = tally.computeIfAbsent(p.getPick(), k -> new int[2]);
			if (!p.isScored()) {
				pending++;
				continue;
			}
			counts[0]++;
			if (Boolean.TRUE.equals(p.getIsHit())) {
				counts[1]++;
				hits++;
			} else {
				misses++;
			}
		}

		int scored = hits + misses;
		SampleConfidence confidence = SampleConfidence.of(scored);

		Map<Pick, PredictionAccuracy.PickAccuracy> byPick = new EnumMap<>(Pick.class);
		tally.forEach((pick, counts) -> byPick.put(pick,
				new PredictionAccuracy.PickAccuracy(counts[0], counts[1], rate(counts[1], counts[0]))));

		// 최근 것부터 — 화면은 방금 무슨 일이 있었는지를 먼저 본다.
		List<PredictionRecord> recent = new ArrayList<>(all).stream()
				.sorted(Comparator.comparing((Prediction p) -> p.getFixture().getKickoff()).reversed())
				.limit(Math.max(1, recentLimit))
				.map(PredictionRecord::from)
				.toList();

		return new PredictionAccuracy(
				team.getId(), team.displayName(), season,
				scored, pending, hits, misses,
				confidence.allowsRates() ? rate(hits, scored) : null,
				confidence,
				Map.copyOf(byPick),
				recent);
	}

	/**
	 * 비율. 표본이 {@link SampleConfidence#MIN_SAMPLE_FOR_RATE} 미만이면 내지 않는다 —
	 * 3번 맞히고 "적중률 100%"라고 적는 순간 앱이 스스로를 과장하기 시작한다.
	 */
	private static Double rate(int hits, int total) {
		if (total < SampleConfidence.MIN_SAMPLE_FOR_RATE) {
			return null;
		}
		return Math.round((double) hits / total * 1000.0) / 1000.0;
	}
}
