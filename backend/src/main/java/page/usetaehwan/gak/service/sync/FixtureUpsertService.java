package page.usetaehwan.gak.service.sync;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.FixtureStatus;
import page.usetaehwan.gak.domain.Score;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.domain.Venue;
import page.usetaehwan.gak.external.apifootball.dto.FixtureItem;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.NewEntityPersister;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.repository.VenueRepository;
import page.usetaehwan.gak.service.seed.SeedCatalog;

/**
 * 응답 → DB 반영(upsert). 동기화에서 <b>DB를 건드리는 유일한 트랜잭션 경계</b>다.
 *
 * <p>네트워크 호출은 이 밖에서 끝낸다. HTTP를 기다리는 동안 트랜잭션을 열어 두면
 * 커넥션 하나가 응답 시간만큼 잠기고, 타임아웃이 길수록 커넥션 풀이 먼저 마른다.
 *
 * <p>대회 하나 = 트랜잭션 하나. 중간에 실패하면 그 대회는 통째로 롤백되고
 * 다른 대회는 영향을 받지 않는다. 다음 주기에 같은 응답을 다시 적용하면 되므로
 * "절반만 반영된 상태"가 남지 않는다.
 *
 * <h2>N+1을 미리 접는다</h2>
 * 경기 380건을 한 건씩 처리하면 건마다 (경기 1 + 팀 2 + 경기장 1) 조회가 나가 1500회쯤 된다.
 * 그래서 응답에 등장하는 id를 전부 모아 <b>세 번의 벌크 조회</b>로 끌어온 뒤,
 * 그 다음은 메모리 맵만 보고 처리한다.
 */
@Service
public class FixtureUpsertService {

	private static final Logger log = LoggerFactory.getLogger(FixtureUpsertService.class);

	private final CompetitionRepository competitionRepository;
	private final FixtureRepository fixtureRepository;
	private final TeamRepository teamRepository;
	private final VenueRepository venueRepository;
	private final NewEntityPersister persister;
	private final SeedCatalog seedCatalog;

	public FixtureUpsertService(CompetitionRepository competitionRepository,
	                            FixtureRepository fixtureRepository,
	                            TeamRepository teamRepository,
	                            VenueRepository venueRepository,
	                            NewEntityPersister persister,
	                            SeedCatalog seedCatalog) {
		this.competitionRepository = competitionRepository;
		this.fixtureRepository = fixtureRepository;
		this.teamRepository = teamRepository;
		this.venueRepository = venueRepository;
		this.persister = persister;
		this.seedCatalog = seedCatalog;
	}

	/**
	 * @param fixtureCount  반영한 경기 수(신규 + 갱신)
	 * @param newTeamCount  새로 만든 팀 수
	 * @param newVenueCount 새로 만든 경기장 수
	 * @param skippedCount  필수 정보가 없어 건너뛴 경기 수
	 */
	public record UpsertResult(int fixtureCount, int newTeamCount, int newVenueCount,
	                           int skippedCount) {
	}

	@Transactional
	public UpsertResult upsert(Long competitionId, int season, List<FixtureItem> items) {
		Competition competition = competitionRepository.findById(competitionId)
				.orElseThrow(() -> new NoSuchElementException(
						"대회를 찾을 수 없습니다. competitionId=" + competitionId));

		Map<Long, Venue> venues = loadVenues(items);
		Map<Long, Team> teams = loadTeams(items);
		Map<Long, Fixture> fixtures = loadFixtures(items);

		int newVenues = 0;
		int newTeams = 0;
		int applied = 0;
		int skipped = 0;

		for (FixtureItem item : items) {
			if (!hasRequiredFields(item)) {
				skipped++;
				continue;
			}

			Venue venue = resolveVenue(item.fixture().venue(), venues);
			if (venue != null && !venues.containsKey(venue.getId())) {
				venues.put(venue.getId(), venue);
				newVenues++;
			}

			// 홈 팀에만 경기장을 홈 구장 후보로 넘긴다. 원정 팀에 넘기면 상대 홈구장이
			// 자기 홈으로 저장된다. (컵 결승 같은 중립 경기 때문에 이미 값이 있으면 덮지 않는다)
			Team home = resolveTeam(item.teams().home(), venue, teams);
			Team away = resolveTeam(item.teams().away(), null, teams);
			if (home == null || away == null) {
				skipped++;
				continue;
			}
			newTeams += countNew(teams, home) + countNew(teams, away);

			Instant kickoff = resolveKickoff(item.fixture());
			if (kickoff == null) {
				skipped++;
				continue;
			}

			applyFixture(item, competition, season, home, away, venue, kickoff, fixtures);
			applied++;
		}

		if (skipped > 0) {
			log.warn("대회 {} 시즌 {} — 필수 정보 누락으로 {}건 건너뜀", competitionId, season, skipped);
		}
		return new UpsertResult(applied, newTeams, newVenues, skipped);
	}

	// --- 벌크 조회 -----------------------------------------------------------

	private Map<Long, Venue> loadVenues(List<FixtureItem> items) {
		Set<Long> ids = new HashSet<>();
		for (FixtureItem item : items) {
			FixtureItem.Venue v = item.fixture() == null ? null : item.fixture().venue();
			if (v != null && v.id() != null) {
				ids.add(v.id());
			}
		}
		return toMap(venueRepository.findAllById(ids), Venue::getId);
	}

	private Map<Long, Team> loadTeams(List<FixtureItem> items) {
		Set<Long> ids = new HashSet<>();
		for (FixtureItem item : items) {
			if (item.teams() == null) {
				continue;
			}
			addTeamId(ids, item.teams().home());
			addTeamId(ids, item.teams().away());
		}
		return toMap(teamRepository.findAllById(ids), Team::getId);
	}

	private Map<Long, Fixture> loadFixtures(List<FixtureItem> items) {
		Set<Long> ids = new HashSet<>();
		for (FixtureItem item : items) {
			if (item.fixture() != null && item.fixture().id() != null) {
				ids.add(item.fixture().id());
			}
		}
		return toMap(fixtureRepository.findAllById(ids), Fixture::getId);
	}

	// --- 개별 반영 -----------------------------------------------------------

	private Venue resolveVenue(FixtureItem.Venue node, Map<Long, Venue> cache) {
		if (node == null || node.id() == null || isBlank(node.name())) {
			return null; // 경기장 미정. 에러가 아니라 사실이다.
		}
		Venue existing = cache.get(node.id());
		if (existing != null) {
			existing.applyApiFacts(node.name(), node.city());
			return existing;
		}

		Venue created = Venue.builder()
				.id(node.id())
				.name(node.name())
				.city(node.city())
				.build();
		// 좌표는 API가 주지 않는다 → 신규 생성 시 시드에서만 채운다.
		SeedCatalog.Coordinates coords = seedCatalog.coordinates(node.city());
		if (coords != null) {
			created.assignCoordinates(coords.latitude(), coords.longitude());
		}
		return persister.persistNew(created);
	}

	private Team resolveTeam(FixtureItem.Team node, Venue homeVenue, Map<Long, Team> cache) {
		if (node == null || node.id() == null || isBlank(node.name())) {
			return null;
		}
		Team existing = cache.get(node.id());
		if (existing != null) {
			// 이미 홈 구장이 있으면 유지한다 — 중립 경기장 경기가 홈 구장을 갈아치우면 안 된다.
			existing.applyApiFacts(node.name(), existing.getHomeVenue() == null ? homeVenue : null);
			return existing;
		}

		Team created = Team.builder()
				.id(node.id())
				.name(node.name())
				.homeVenue(homeVenue)
				.build();

		SeedCatalog.TeamName seed = seedCatalog.teamName(node.name());
		if (seed != null) {
			created.applySeedNames(seed.nameKo(), seed.shortNameKo(), seed.code());
		}
		if (isBlank(created.getCode())) {
			// /fixtures 응답에는 team.code가 없다. 시드에도 없으면 팀명에서 만들어 둔다.
			created.applySeedNames(null, null, TeamCodes.derive(node.name()));
		}
		return persister.persistNew(created);
	}

	private void applyFixture(FixtureItem item, Competition competition, int season,
	                          Team home, Team away, Venue venue, Instant kickoff,
	                          Map<Long, Fixture> cache) {
		FixtureItem.Fixture node = item.fixture();
		String round = item.league() == null ? null : item.league().round();
		FixtureStatus status = FixtureStatus.fromApiCode(
				node.status() == null ? null : node.status().code());
		Integer elapsed = node.status() == null ? null : node.status().elapsed();
		Integer goalsHome = item.goals() == null ? null : item.goals().home();
		Integer goalsAway = item.goals() == null ? null : item.goals().away();
		FixtureItem.Score score = item.score();

		Fixture existing = cache.get(node.id());
		if (existing != null) {
			existing.applyApiFacts(competition, season, round, home, away, venue, kickoff,
					status, elapsed, goalsHome, goalsAway,
					toScore(score == null ? null : score.halftime()),
					toScore(score == null ? null : score.fulltime()),
					toScore(score == null ? null : score.extratime()),
					toScore(score == null ? null : score.penalty()));
			return;
		}

		Fixture created = Fixture.builder()
				.id(node.id())
				.competition(competition)
				.season(season)
				.round(round)
				.homeTeam(home)
				.awayTeam(away)
				.venue(venue)
				.kickoff(kickoff)
				.status(status)
				.elapsed(elapsed)
				.goalsHome(goalsHome)
				.goalsAway(goalsAway)
				.halftime(toScore(score == null ? null : score.halftime()))
				.fulltime(toScore(score == null ? null : score.fulltime()))
				.extratime(toScore(score == null ? null : score.extratime()))
				.penalty(toScore(score == null ? null : score.penalty()))
				.build();
		cache.put(created.getId(), persister.persistNew(created));
	}

	/**
	 * 킥오프 시각. {@code timestamp}(epoch seconds)를 우선 쓴다 — 타임존 해석의 여지가 없다.
	 * 없을 때만 ISO 문자열을 판다.
	 */
	private Instant resolveKickoff(FixtureItem.Fixture node) {
		if (node.timestamp() != null) {
			return Instant.ofEpochSecond(node.timestamp());
		}
		if (!isBlank(node.date())) {
			try {
				return OffsetDateTime.parse(node.date()).toInstant();
			} catch (RuntimeException e) {
				log.warn("킥오프 시각 파싱 실패 fixtureId={} date={}", node.id(), node.date());
			}
		}
		return null;
	}

	private static Score toScore(FixtureItem.Goals goals) {
		return goals == null ? null : Score.of(goals.home(), goals.away());
	}

	private static boolean hasRequiredFields(FixtureItem item) {
		return item != null
				&& item.fixture() != null
				&& item.fixture().id() != null
				&& item.teams() != null;
	}

	private static void addTeamId(Set<Long> ids, FixtureItem.Team team) {
		if (team != null && team.id() != null) {
			ids.add(team.id());
		}
	}

	/** 캐시에 없던 팀이면 신규로 세고, 캐시에 넣는다. */
	private static int countNew(Map<Long, Team> cache, Team team) {
		if (cache.containsKey(team.getId())) {
			return 0;
		}
		cache.put(team.getId(), team);
		return 1;
	}

	private static <T> Map<Long, T> toMap(List<T> entities, Function<T, Long> idOf) {
		Map<Long, T> map = new HashMap<>();
		for (T entity : entities) {
			map.put(idOf.apply(entity), entity);
		}
		return map;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
