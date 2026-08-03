package page.usetaehwan.gak.service.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Standing;
import page.usetaehwan.gak.dto.analysis.OpponentStrength;
import page.usetaehwan.gak.dto.analysis.SampleConfidence;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.StandingRepository;

/**
 * "누구를 상대로 그 성적이었나"를 계산한다.
 *
 * <h2>순위는 경기 시점 것을 쓴다</h2>
 * <p>{@link LeagueTable} 이 경기 결과로 그 시점 표를 다시 만든다. 이유는
 * {@link OpponentStrength} 주석 참고 — 최종 순위를 쓰면 이 앱이 자기 규칙을 어긴다.
 *
 * <h2>승점 삭감은 순위표로 보정한다</h2>
 * <p>우리 계산은 경기 결과만 보므로 승점 삭감을 모른다. 그런데 {@code /standings} 가 준
 * 승점에는 삭감이 이미 반영돼 있다. <b>같은 경기 수에서 우리 승점과 API 승점의 차이가
 * 곧 삭감분</b>이다. 2023-24 프리미어리그 실제 데이터로 검산했더니 20팀 중 18팀이 완전히
 * 일치했고, 어긋난 둘이 정확히 에버턴 −8 · 노팅엄 포레스트 −4 였다. 이걸 반영하지 않으면
 * 에버턴이 12위로 나와 실제 15위와 세 계단 어긋난다.
 *
 * <p><b>다만 삭감 <i>시점</i>은 모른다.</b> API 는 "지금 몇 점 깎였나"만 줄 뿐 언제
 * 깎였는지 말하지 않는다. 그래서 시즌 전체에 균일하게 소급 적용한다 — 최근 폼 구간
 * (이 지표가 실제로 쓰이는 곳)에서는 맞고, 삭감 이전 경기에서는 실제와 다를 수 있다.
 * 이 한계는 {@code omissions} 로 밝힌다.
 */
@Service
public class OpponentStrengthService {

	private final FixtureRepository fixtureRepository;
	private final StandingRepository standingRepository;

	public OpponentStrengthService(FixtureRepository fixtureRepository,
	                               StandingRepository standingRepository) {
		this.fixtureRepository = fixtureRepository;
		this.standingRepository = standingRepository;
	}

	/**
	 * @param teamId  분석 대상 팀
	 * @param recent  최근 폼 구간의 경기들(결과가 확정된 것만). 과거→최근 순
	 */
	@Transactional(readOnly = true)
	public OpponentStrength of(Long teamId, List<Fixture> recent) {
		if (recent.isEmpty()) {
			return OpponentStrength.unmeasured(0);
		}

		// 리그 경기만 순위를 매길 수 있다. 컵은 표가 없고, 하이브리드는 조별 표라
		// 서로 다른 조의 1위를 같은 "1위"로 부르면 거짓이 된다.
		Map<TableKey, List<Fixture>> leagueFixtureCache = new HashMap<>();
		Map<TableKey, Map<Long, Integer>> deductionCache = new HashMap<>();

		int measured = 0;
		int unmeasured = 0;
		List<Integer> ranks = new ArrayList<>();
		Tally top = new Tally();
		Tally rest = new Tally();
		Integer tableSize = null;
		Integer topCut = null;
		boolean deductionsKnown = true;

		for (Fixture f : recent) {
			if (f.getCompetition().getType() != CompetitionType.LEAGUE) {
				unmeasured++;
				continue;
			}
			TableKey key = new TableKey(f.getCompetition().getId(), f.getSeason());
			List<Fixture> all = leagueFixtureCache.computeIfAbsent(key, k ->
					fixtureRepository.findByCompetitionIdAndSeasonOrderByKickoffAsc(k.competitionId(), k.season()));
			Map<Long, Integer> deductions = deductionCache.computeIfAbsent(key, this::deductionsFor);
			if (deductions.isEmpty()) {
				// 순위표를 아직 동기화하지 않았다 = 삭감을 확인하지 못했다.
				deductionsKnown = false;
			}

			// 그 경기 킥오프 직전까지의 표
			LeagueTable.Snapshot table = LeagueTable.at(all, f.getKickoff(), deductions);
			long opponentId = f.getHomeTeam().getId().equals(teamId)
					? f.getAwayTeam().getId() : f.getHomeTeam().getId();
			Integer rank = table.rankOf(opponentId);
			if (rank == null) {
				// 시즌 초라 상대의 경기 수가 얇다 → 순위를 말할 수 없다. 0으로 채우지 않는다.
				unmeasured++;
				continue;
			}

			measured++;
			ranks.add(rank);
			tableSize = table.size();
			topCut = table.topCut();
			(rank <= table.topCut() ? top : rest).add(f, teamId);
		}

		if (measured == 0) {
			return OpponentStrength.unmeasured(unmeasured);
		}
		double avg = ranks.stream().mapToInt(Integer::intValue).average().orElseThrow();
		return new OpponentStrength(
				measured, unmeasured,
				Math.round(avg * 10) / 10.0,
				tableSize, topCut,
				top.toSplit(), rest.toSplit(),
				deductionsKnown);
	}

	/**
	 * 팀별 승점 삭감분.
	 *
	 * <p>순위표의 승점(삭감 반영됨)과, 같은 경기 수까지의 우리 계산 승점을 견준다.
	 * 순위표가 없으면 빈 맵 — 삭감을 "0"이라고 단정하지 않는다.
	 */
	private Map<Long, Integer> deductionsFor(TableKey key) {
		List<Standing> table = standingRepository.findTable(key.competitionId(), key.season());
		if (table.isEmpty()) {
			return Map.of();
		}
		List<Fixture> all = fixtureRepository
				.findByCompetitionIdAndSeasonOrderByKickoffAsc(key.competitionId(), key.season());

		Map<Long, Integer> deductions = new HashMap<>();
		for (Standing s : table) {
			// 그 팀이 순위표 기준 몇 경기를 치렀는지에 맞춰 우리 계산을 끊는다.
			int computed = LeagueTable.pointsAfter(all, s.getTeam().getId(), s.getPlayed());
			int diff = computed - s.getPoints();
			if (diff != 0) {
				deductions.put(s.getTeam().getId(), diff);
			}
		}
		// 삭감이 하나도 없어도 "확인했다"는 사실은 남겨야 한다 — 빈 맵은 "순위표 없음"과
		// 구분되지 않으므로, 확인했음을 나타내는 표시를 하나 넣는다.
		if (deductions.isEmpty()) {
			deductions.put(CHECKED_MARKER, 0);
		}
		return deductions;
	}

	/** "삭감을 확인했고 없었다"를 "순위표가 없다"와 구분하는 표시. 실제 팀 id 와 겹치지 않는 값. */
	static final long CHECKED_MARKER = -1L;

	private record TableKey(Long competitionId, Integer season) {
	}

	/** 한 무리(상위권/그 외) 상대 성적 누적. */
	private static final class Tally {
		int matches, wins, draws, losses, points;

		void add(Fixture f, Long teamId) {
			matches++;
			boolean home = f.getHomeTeam().getId().equals(teamId);
			int our = home ? f.getGoalsHome() : f.getGoalsAway();
			int theirs = home ? f.getGoalsAway() : f.getGoalsHome();
			if (our > theirs) {
				wins++;
				points += 3;
			} else if (our == theirs) {
				draws++;
				points += 1;
			} else {
				losses++;
			}
		}

		OpponentStrength.Split toSplit() {
			int maxPoints = matches * 3;
			// 표본이 얇으면 비율을 내지 않는다 — 폼·적중률과 같은 기준이다.
			Double rate = SampleConfidence.of(matches).allowsRates() && maxPoints > 0
					? Math.round((double) points / maxPoints * 100) / 100.0
					: null;
			return new OpponentStrength.Split(matches, wins, draws, losses, points, maxPoints, rate);
		}
	}
}
