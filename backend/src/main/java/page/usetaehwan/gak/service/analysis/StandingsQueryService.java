package page.usetaehwan.gak.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Standing;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.StandingsTable;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.StandingRepository;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 화면이 볼 순위표를 꺼낸다.
 *
 * <h2>팀에서 리그를 찾는다 — 리그를 물어보지 않는다</h2>
 * <p>사용자는 "맨유"를 보고 있지 "대회 39번"을 보고 있지 않다. 그래서 팀 id 만 받고,
 * 그 팀이 실제로 뛴 경기에서 리그를 찾아낸다. 팀 화면에 "어느 리그 순위를 볼까요?"를
 * 묻는 드롭다운이 붙는 건 우리가 게을러서 사용자에게 일을 미루는 것이다.
 *
 * <h2>⚠️ 시즌은 <b>받는다</b> — 우리가 고르지 않는다</h2>
 * <p>예전에는 "그 팀의 가장 최근 리그 경기"로 시즌을 정했다. 그러면 2023-24 타임라인을
 * 보면서 순위표 탭을 열었을 때 <b>다른 시즌의 표</b>가 떴다. 에러는 안 나고 표는 멀쩡해
 * 보이는데 옆 화면과 다른 해를 말하는, 이 프로젝트가 가장 경계하는 종류의 실패다.
 * 이제 시즌은 입력 화면이 정해서 넘겨주는 값이고, 여기서는 그 시즌만 본다.
 *
 * <h2>없으면 없다고 말한다 — 최신 시즌 표로 대체하지 않는다</h2>
 * <p>컵만 뛰는 팀도 있고, 그 시즌엔 1부에 없던 팀도 있고, 순위표를 아직 동기화하지 않았을
 * 수도 있다. 그때 <b>가진 표를 대신 보여 주는 것</b>이 가장 나쁜 선택이다 — 화면은 채워지고
 * 사용자는 그게 그 시즌 표라고 읽는다. 빈 표도 안 된다("순위 없음"인지 "0위"인지 알 수 없다).
 * 사유를 함께 준다.
 */
@Service
public class StandingsQueryService {

	private final StandingRepository standingRepository;
	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;

	public StandingsQueryService(StandingRepository standingRepository,
	                             FixtureRepository fixtureRepository,
	                             TeamRepository teamRepository) {
		this.standingRepository = standingRepository;
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
	}

	/**
	 * @param teamId 보고 있는 팀
	 * @param season 보고 있는 시즌. 입력 화면이 정해 넘긴다 — 여기서 고르지 않는다
	 * @throws NoSuchElementException 팀이 없을 때
	 */
	@Transactional(readOnly = true)
	public StandingsTable forTeam(Long teamId, Integer season) {
		if (!teamRepository.existsById(teamId)) {
			throw new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId);
		}

		// 그 시즌에 이 팀이 뛴 리그. 한 팀이 한 시즌에 두 리그를 뛰지는 않지만, 승격 직후
		// 데이터가 겹쳐 들어온 경우를 대비해 id가 작은 쪽으로 결정론적으로 고른다.
		Competition league = fixtureRepository.findLeaguesInSeason(teamId, season).stream()
				.min(Comparator.comparing(Competition::getId))
				.orElse(null);

		if (league == null) {
			return StandingsTable.unavailable(
					"이 시즌에는 이 팀의 리그 경기가 없습니다. 다른 시즌 순위표로 대체하지 않습니다.");
		}

		List<Standing> table = standingRepository.findTable(league.getId(), season);

		if (table.isEmpty()) {
			return StandingsTable.unavailable(
					"%s %d 시즌 순위표를 아직 동기화하지 않았습니다."
							.formatted(league.displayName(), season));
		}

		List<StandingsTable.Row> rows = new ArrayList<>(table.size());
		for (Standing s : table) {
			Team team = s.getTeam();
			rows.add(new StandingsTable.Row(
					s.getRank(), team.getId(), team.displayName(), team.getCode(),
					s.getPlayed(), s.getPoints(), s.getGoalsFor(), s.getGoalsAgainst(),
					s.goalsDiff(), s.getDescription(),
					team.getId().equals(teamId)));
		}

		return new StandingsTable(
				true, null,
				league.getId(), league.displayName(), season,
				List.copyOf(rows),
				// 표의 모든 줄이 같은 동기화에서 왔으므로 아무 줄의 시각이나 같다.
				table.get(0).getUpdatedAt());
	}
}
