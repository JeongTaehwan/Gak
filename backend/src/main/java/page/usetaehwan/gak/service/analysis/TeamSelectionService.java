// requirements.md 2~4장 — 팀 선택 · 시즌과 회고 · 선택 대상이 아닌 시즌
package page.usetaehwan.gak.service.analysis;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.config.TeamAccessProperties;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.dto.analysis.TeamSelection;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 입력 화면의 선행 계산 — <b>시즌을 정하고, 그 시즌의 선택 가능 팀을 만든다.</b>
 *
 * <h2>이 서비스가 팀 범위의 단일 주인이다</h2>
 * <p>선택 가능 팀은 어디에도 저장하지 않는다. {@code Team} 에 소속 리그·등급 필드를 두지
 * 않고, 조회 시즌에 <b>선택 기준 대회의 경기가 있는 팀</b>을 매번 계산한다. 저장하면
 * 승격·강등이 일어난 순간 과거 시즌 목록이 조용히 틀려지고, 화면마다 다른 시점의 목록을
 * 들고 있게 된다.
 *
 * <h2>시즌은 필터가 아니라 선행 입력이다</h2>
 * <p>"지금 1부인 팀"으로 목록을 고정하면 강등된 팀의 과거를 볼 수 없다. 회고를 지원하기로
 * 한 이상 성립하지 않으므로, <b>조회 중인 시즌</b>이 목록을 만든다.
 *
 * <h2>현재 시즌 판정은 {@code AnalysisPeriodResolver} 와 같은 규칙이다</h2>
 * <p>치른 경기가 있는 시즌 중 가장 큰 값. 여기와 진단이 다른 규칙을 쓰면 입력 화면이 고른
 * 시즌과 진단이 실제로 본 시즌이 갈리고, 그 어긋남은 아무 에러 없이 다른 해의 숫자를
 * 보여 주는 방식으로만 드러난다.
 *
 * <h2>⚠️ 판정 범위를 선택 기준 대회로 좁힌 근거</h2>
 * <p>요구사항은 판정 규칙("치른 경기가 있는 시즌 중 가장 큰 season")은 못박았지만 <b>어느
 * 경기를 세는지</b>는 적지 않았다. 여기서는 선택 기준 대회 6개만 센다 — 조회 가능 시즌이
 * "선택 기준 대회에 경기가 있는 시즌"으로 정의돼 있어서, 세는 범위를 넓히면 <b>조회할 수
 * 없는 시즌이 현재 시즌이 되는</b> 상태가 나온다(컵 경기만 치른 해). 넓히는 건 나중에도
 * 쉬우므로 좁은 쪽으로 닫아 둔다.
 */
@Service
public class TeamSelectionService {

	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;
	private final TeamAccessProperties access;
	private final Clock clock;

	public TeamSelectionService(FixtureRepository fixtureRepository,
	                            TeamRepository teamRepository,
	                            TeamAccessProperties access,
	                            Clock clock) {
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
		this.access = access;
		this.clock = clock;
	}

	/**
	 * @param requestedSeason URL이 고정한 시즌. null이면 자동 판정한 현재 시즌
	 * @param teamId          URL이 고정한 팀. null이면 고른 팀 없음
	 * @throws NoSuchElementException 팀 id가 우리 DB에 아예 없을 때. <b>선택 대상이 아닌
	 *                                것과 존재하지 않는 것은 다르다</b> — 전자는 200 + eligible=false
	 */
	@Transactional(readOnly = true)
	public TeamSelection resolve(Integer requestedSeason, Long teamId) {
		List<Integer> available = fixtureRepository.findSelectableSeasons(); // 내림차순
		if (available.isEmpty()) {
			// 선택 기준 대회 경기가 하나도 없다. "선택 가능 팀 0개"가 아니라 볼 시즌이 없는 것이다.
			return TeamSelection.noSeasons(access.restricted());
		}

		Integer current = currentSeason(available);
		Integer season = requestedSeason != null ? requestedSeason : current;

		List<TeamSelection.Option> teams = new ArrayList<>();
		for (Team team : teamRepository.findSelectableInSeason(season)) {
			if (access.allows(team.getId())) {
				teams.add(new TeamSelection.Option(
						team.getId(), team.displayName(), team.getCode()));
			}
		}

		TeamSelection.Selected selected = selected(teamId, season);

		return new TeamSelection(
				season,
				calendarSeason(teamId, season),
				current,
				season.equals(current),
				previousSeason(available, season),
				nextSeason(available, season, current),
				List.copyOf(teams),
				access.restricted(),
				selected);
	}

	/** 이 팀이 그 시즌의 선택 대상인가. 진단·질문 경로가 "판정 불가"를 가릴 때 함께 쓴다. */
	@Transactional(readOnly = true)
	public boolean eligible(Long teamId, Integer season) {
		return season != null && fixtureRepository.hasSelectableFixture(teamId, season);
	}

	/**
	 * 자동 판정한 현재 시즌.
	 *
	 * <p>치른 경기가 하나도 없으면(개막 전에 일정만 들어온 상태) 조회 가능 시즌 중 가장 큰
	 * 값으로 물러난다. 그 시즌은 아직 진단할 것이 없지만 <b>일정은 보여 줄 수 있어야</b>
	 * 하고, 여기서 null 을 내면 화면이 갈 곳을 잃는다.
	 */
	private Integer currentSeason(List<Integer> availableDesc) {
		Integer played = fixtureRepository.findLatestPlayedSelectableSeason(Instant.now(clock));
		return played != null ? played : availableDesc.get(0);
	}

	/** 현재보다 작은 조회 가능 시즌 중 가장 큰 값. 없으면 null — 화면은 버튼을 비활성화한다. */
	private Integer previousSeason(List<Integer> availableDesc, Integer season) {
		for (Integer candidate : availableDesc) { // 내림차순이라 처음 만나는 게 가장 큰 값이다
			if (candidate < season) {
				return candidate;
			}
		}
		return null;
	}

	/**
	 * 한 시즌 앞으로. <b>자동 판정 최신 시즌을 넘지 않는다</b> — 그 너머는 아직 치른 경기가
	 * 없는 시즌이라, 이동하면 화면이 통째로 비고 사용자는 "현재 시즌"에 도달하지 못한다.
	 */
	private Integer nextSeason(List<Integer> availableDesc, Integer season, Integer current) {
		Integer next = null;
		for (Integer candidate : availableDesc) {
			if (candidate > season && candidate <= current && (next == null || candidate < next)) {
				next = candidate;
			}
		}
		return next;
	}

	/**
	 * 고른 팀. <b>선택 대상이 아니어도 다른 팀으로 바꾸지 않는다</b> — 그대로 담고
	 * {@code eligible=false} 로 내려보낸다.
	 */
	private TeamSelection.Selected selected(Long teamId, Integer season) {
		if (teamId == null) {
			return null;
		}
		Team team = teamRepository.findById(teamId)
				.orElseThrow(() -> new NoSuchElementException("팀을 찾을 수 없습니다. teamId=" + teamId));
		return new TeamSelection.Selected(
				team.getId(), team.displayName(), team.getCode(),
				fixtureRepository.hasSelectableFixture(teamId, season));
	}

	/**
	 * 이 시즌 표기가 "2025"인지 "2023-24"인지.
	 *
	 * <p>고른 팀의 선택 기준 대회에서 읽는다 — 같은 시즌 번호라도 K리그는 한 해 안에서
	 * 끝나고 유럽 리그는 걸친다. 고른 팀이 없거나 그 시즌 기록이 없으면 걸침 시즌으로 둔다.
	 * 날짜 범위로 되짚지 않는 이유는 시즌 초(8월 경기밖에 없는 시점)에 유럽 리그가
	 * "2026 시즌"이 되기 때문이다.
	 */
	private boolean calendarSeason(Long teamId, Integer season) {
		if (teamId == null) {
			return false;
		}
		List<Competition> competitions = fixtureRepository.findSelectableCompetitions(teamId, season);
		return !competitions.isEmpty() && competitions.get(0).isCalendarSeason();
	}
}
