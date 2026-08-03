package page.usetaehwan.gak.service.sync;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import page.usetaehwan.gak.domain.Absence;
import page.usetaehwan.gak.domain.AbsenceReason;
import page.usetaehwan.gak.domain.AbsenceStatus;
import page.usetaehwan.gak.domain.Fixture;
import page.usetaehwan.gak.domain.Player;
import page.usetaehwan.gak.domain.Team;
import page.usetaehwan.gak.external.apifootball.dto.InjuryItem;
import page.usetaehwan.gak.repository.AbsenceRepository;
import page.usetaehwan.gak.repository.FixtureRepository;
import page.usetaehwan.gak.repository.NewEntityPersister;
import page.usetaehwan.gak.repository.PlayerRepository;
import page.usetaehwan.gak.repository.TeamRepository;
import page.usetaehwan.gak.service.sync.AbsenceSyncService.AbsenceSyncResult;

/**
 * 결장 응답 → DB 반영(upsert). 결장 동기화에서 <b>DB를 건드리는 유일한 트랜잭션 경계</b>다.
 *
 * <p>{@link AbsenceSyncService}와 빈을 나눈 건 취향이 아니다. 트랜잭션은 스프링 프록시가
 * 거는데, 같은 빈 안에서 메서드를 부르면 프록시를 거치지 않아 {@code @Transactional}이
 * 조용히 무시된다. 네트워크 호출(트랜잭션 밖)과 DB 반영(트랜잭션 안)을 갈라 두려면
 * 빈이 달라야 한다. {@code FixtureSyncService} / {@code FixtureUpsertService}와 같은 구조다.
 *
 * <h2>우리 DB에 없는 경기는 버린다</h2>
 * <p>결장은 "그 경기에 못 나왔다"는 사실이라 경기가 없으면 붙일 데가 없다. API는 우리가
 * 동기화하지 않는 대회의 결장도 함께 주는데, 저장해 봐야 어떤 화면에도 닿지 못한 채
 * 쌓이기만 한다. <b>버린 수를 결과에 담아</b> 조용히 사라지지 않게 한다 — "부상 데이터를
 * 붙였는데 화면에 아무것도 안 나온다"의 원인이 대개 여기다.
 *
 * <h2>멱등</h2>
 * <p>{@code (fixture, player)}로 기존 행을 찾아 갱신한다. 같은 응답을 몇 번 적용해도 행이
 * 늘지 않는다. 사유와 확정 여부는 경기 직전까지 바뀌므로("Questionable" → "Missing
 * Fixture") 매번 덮어쓴다.
 *
 * <h2>N+1</h2>
 * <p>결장 346건을 한 건씩 처리하면 건마다 (경기 1 + 선수 1 + 기존 결장 1) 조회가 나간다.
 * 응답에 등장하는 id를 전부 모아 벌크로 끌어온 뒤 메모리 맵만 본다.
 */
@Service
public class AbsenceUpsertService {

	private static final Logger log = LoggerFactory.getLogger(AbsenceUpsertService.class);

	private final AbsenceRepository absenceRepository;
	private final FixtureRepository fixtureRepository;
	private final PlayerRepository playerRepository;
	private final TeamRepository teamRepository;
	private final NewEntityPersister persister;

	public AbsenceUpsertService(AbsenceRepository absenceRepository,
	                            FixtureRepository fixtureRepository,
	                            PlayerRepository playerRepository,
	                            TeamRepository teamRepository,
	                            NewEntityPersister persister) {
		this.absenceRepository = absenceRepository;
		this.fixtureRepository = fixtureRepository;
		this.playerRepository = playerRepository;
		this.teamRepository = teamRepository;
		this.persister = persister;
	}

	@Transactional
	public AbsenceSyncResult apply(Long teamId, int season, List<InjuryItem> items,
	                                  int requestCount) {
		if (items.isEmpty()) {
			return new AbsenceSyncResult(0, 0, 0, 0, requestCount);
		}
		Team team = teamRepository.findById(teamId).orElseThrow();

		// --- 벌크 조회: 경기 / 선수 / 기존 결장 --------------------------------
		Set<Long> fixtureIds = items.stream()
				.map(i -> i.fixture() == null ? null : i.fixture().id())
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Fixture> fixtures = fixtureRepository.findAllById(fixtureIds).stream()
				.collect(Collectors.toMap(Fixture::getId, Function.identity()));

		Set<Long> playerIds = items.stream()
				.map(i -> i.player() == null ? null : i.player().id())
				.filter(Objects::nonNull)
				.collect(Collectors.toSet());
		Map<Long, Player> players = new HashMap<>(playerRepository.findAllById(playerIds).stream()
				.collect(Collectors.toMap(Player::getId, Function.identity())));

		Map<AbsenceKey, Absence> existing = absenceRepository
				.findByTeamAndFixtureIds(teamId, fixtureIds).stream()
				.collect(Collectors.toMap(
						a -> new AbsenceKey(a.getFixture().getId(), a.getPlayer().getId()),
						Function.identity(),
						(a, b) -> a));

		int applied = 0;
		int newPlayers = 0;
		int unknownFixture = 0;
		// 같은 응답 안에 같은 (경기, 선수) 가 두 번 오는 경우를 대비 — 우리 자연키가 유일해야 한다.
		Set<AbsenceKey> seen = new HashSet<>();

		for (InjuryItem item : items) {
			if (item.fixture() == null || item.fixture().id() == null
					|| item.player() == null || item.player().id() == null) {
				continue;
			}
			Fixture fixture = fixtures.get(item.fixture().id());
			if (fixture == null) {
				// 우리가 동기화하지 않는 대회의 경기. 사실이지만 붙일 데가 없다.
				unknownFixture++;
				continue;
			}
			AbsenceKey key = new AbsenceKey(fixture.getId(), item.player().id());
			if (!seen.add(key)) {
				continue;
			}

			Player player = players.get(item.player().id());
			if (player == null) {
				player = persister.persistNew(Player.builder()
						.id(item.player().id())
						.name(blankToNull(item.player().name()) == null
								? "Player " + item.player().id()
								: item.player().name())
						.build());
				players.put(player.getId(), player);
				newPlayers++;
			} else {
				player.applyApiFacts(item.player().name());
			}

			AbsenceStatus status = AbsenceStatus.from(item.player().type());
			AbsenceReason reason = AbsenceReason.from(item.player().reason());
			String raw = blankToNull(item.player().reason()) == null ? "Unknown" : item.player().reason();

			Absence found = existing.get(key);
			if (found == null) {
				persister.persistNew(Absence.builder()
						.fixture(fixture).player(player).team(team)
						.status(status).reason(reason).reasonRaw(raw)
						.build());
			} else {
				found.applyApiFacts(status, reason, raw);
			}
			applied++;
		}

		if (unknownFixture > 0) {
			log.info("결장 동기화: 우리 DB에 없는 경기 {}건은 건너뜀 (teamId={}, season={})",
					unknownFixture, teamId, season);
		}
		log.info("결장 동기화 완료: 수신 {} / 반영 {} / 신규 선수 {} (teamId={}, season={})",
				items.size(), applied, newPlayers, teamId, season);
		return new AbsenceSyncResult(items.size(), applied, newPlayers, unknownFixture, requestCount);
	}

	/** {@code (경기, 선수)} 자연키. */
	private record AbsenceKey(Long fixtureId, Long playerId) {
	}

	private static String blankToNull(String s) {
		return (s == null || s.isBlank()) ? null : s;
	}
}
