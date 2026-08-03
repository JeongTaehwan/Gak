package page.usetaehwan.gak.service.sync;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Standing;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.external.apifootball.dto.StandingItem;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.StandingRepository;
import page.usetaehwan.gak.repository.TeamRepository;

/**
 * 순위표 응답 → DB 반영. <b>동기화에서 DB를 건드리는 유일한 트랜잭션 경계</b>다.
 *
 * <p>동기화 서비스와 나눈 이유는 결장 동기화와 같다 — 네트워크를 기다리는 시간을
 * 트랜잭션 안에 넣으면 커넥션을 그만큼 붙잡고 있게 된다. 그리고 {@code @Transactional} 은
 * 프록시로 동작하므로 <b>같은 클래스 안에서 부르면 걸리지 않는다.</b>
 *
 * <h2>덮어쓴다 — 쌓지 않는다</h2>
 * <p>순위표는 이력이 아니라 <b>현재 상태</b>다. 매 동기화마다 행을 쌓으면 "지금 몇 위"를
 * 묻는 데 정렬과 중복 제거가 필요해지고, 그렇게 쌓은 이력도 동기화 주기만큼 듬성해서
 * 경기 시점 순위로는 못 쓴다. 경기 시점은
 * {@link page.usetaehwan.gak.service.analysis.LeagueTable} 이 계산으로 답한다.
 *
 * <h2>모르는 팀은 건너뛴다</h2>
 * <p>순위표에는 우리가 아직 경기로 만난 적 없는 팀이 있을 수 있다. 그때 팀을 새로 만들면
 * 이름·코드가 비어 있는 유령 행이 생긴다. <b>경기가 팀을 만들고, 순위표는 그 위에 얹힌다</b> —
 * 순서를 뒤집지 않는다.
 */
@Service
public class StandingUpsertService {

	private static final Logger log = LoggerFactory.getLogger(StandingUpsertService.class);

	private final StandingRepository standingRepository;
	private final CompetitionRepository competitionRepository;
	private final TeamRepository teamRepository;
	private final Clock clock;

	public StandingUpsertService(StandingRepository standingRepository,
	                             CompetitionRepository competitionRepository,
	                             TeamRepository teamRepository,
	                             Clock clock) {
		this.standingRepository = standingRepository;
		this.competitionRepository = competitionRepository;
		this.teamRepository = teamRepository;
		this.clock = clock;
	}

	/**
	 * @param tables       응답에 담긴 표 개수(정규 리그는 1, 조별리그는 조 수)
	 * @param rows         반영한 줄 수
	 * @param unknownTeams 우리 DB에 없어 건너뛴 팀 수
	 */
	public record UpsertResult(int tables, int rows, int unknownTeams) {
	}

	@Transactional
	public UpsertResult upsert(Long competitionId, int season, List<StandingItem> items) {
		Competition competition = competitionRepository.findById(competitionId).orElseThrow();
		Instant now = Instant.now(clock);

		// 기존 줄을 팀 id로 색인해 두고 덮어쓴다 — 줄마다 select 하지 않는다.
		Map<Long, Standing> existing = new HashMap<>();
		for (Standing s : standingRepository.findByCompetitionIdAndSeason(competitionId, season)) {
			existing.put(s.getTeam().getId(), s);
		}

		int tables = 0;
		int rows = 0;
		int unknownTeams = 0;

		for (StandingItem item : items) {
			if (item.league() == null || item.league().standings() == null) {
				continue;
			}
			for (List<StandingItem.Row> table : item.league().standings()) {
				if (table == null) {
					continue;
				}
				tables++;
				for (StandingItem.Row row : table) {
					if (row == null || row.team() == null || row.team().id() == null) {
						continue;
					}
					Team team = teamRepository.findById(row.team().id()).orElse(null);
					if (team == null) {
						// 경기로 만난 적 없는 팀 — 유령 행을 만들지 않는다.
						unknownTeams++;
						continue;
					}
					int gf = goals(row, true);
					int ga = goals(row, false);
					int played = row.all() != null && row.all().played() != null ? row.all().played() : 0;

					Standing standing = existing.get(team.getId());
					if (standing == null) {
						standing = Standing.builder()
								.competition(competition).season(season).team(team)
								.rank(row.rank()).points(orZero(row.points())).played(played)
								.goalsFor(gf).goalsAgainst(ga)
								.groupName(row.group()).description(row.description())
								.updatedAt(now)
								.build();
						standingRepository.save(standing);
						existing.put(team.getId(), standing);
					} else {
						standing.refresh(row.rank(), orZero(row.points()), played, gf, ga,
								row.group(), row.description(), now);
					}
					rows++;
				}
			}
		}
		log.debug("순위표 upsert: 대회 {} 시즌 {} → 표 {}개 · {}줄 (모르는 팀 {})",
				competitionId, season, tables, rows, unknownTeams);
		return new UpsertResult(tables, rows, unknownTeams);
	}

	private static int goals(StandingItem.Row row, boolean scored) {
		if (row.all() == null || row.all().goals() == null) {
			return 0;
		}
		Integer v = scored ? row.all().goals().forGoals() : row.all().goals().against();
		return v == null ? 0 : v;
	}

	private static int orZero(Integer v) {
		return v == null ? 0 : v;
	}
}
