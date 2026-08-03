package page.usetaehwan.gak.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.Fixture;
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
 * <h2>없으면 없다고 말한다</h2>
 * <p>컵만 뛰는 팀도 있고, 순위표를 아직 동기화하지 않았을 수도 있다. 그때 빈 표를
 * 그리면 화면이 "순위 없음"인지 "0위"인지 알 수 없게 된다 — 사유를 함께 준다.
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

	@Transactional(readOnly = true)
	public StandingsTable forTeam(Long teamId) {
		if (!teamRepository.existsById(teamId)) {
			throw new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId);
		}

		// 그 팀이 뛴 리그 중 가장 최근 것. 시즌이 바뀌면 새 시즌 표를 본다.
		Fixture latestLeague = fixtureRepository.findTeamScheduleWithDetails(teamId).stream()
				.filter(f -> f.getCompetition().getType() == CompetitionType.LEAGUE)
				.max(Comparator.comparing(Fixture::getKickoff,
						Comparator.nullsFirst(Comparator.naturalOrder())))
				.orElse(null);

		if (latestLeague == null) {
			return StandingsTable.unavailable(
					"이 팀의 리그 경기가 없습니다. 컵 대회에는 순위표가 없습니다.");
		}

		Long competitionId = latestLeague.getCompetition().getId();
		Integer season = latestLeague.getSeason();
		List<Standing> table = standingRepository.findTable(competitionId, season);

		if (table.isEmpty()) {
			return StandingsTable.unavailable(
					"%s %d 시즌 순위표를 아직 동기화하지 않았습니다."
							.formatted(latestLeague.getCompetition().displayName(), season));
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
				competitionId, latestLeague.getCompetition().displayName(), season,
				List.copyOf(rows),
				// 표의 모든 줄이 같은 동기화에서 왔으므로 아무 줄의 시각이나 같다.
				table.get(0).getUpdatedAt());
	}
}
