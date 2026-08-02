package page.usetaehwan.gak.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.config.SyncProperties;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;

/**
 * 갱신 주기 차등과 대상 선정 순서. 여기가 하루 100요청을 실제로 지키는 지점이다.
 */
class SyncPlannerTest {

	private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

	private CompetitionRepository competitionRepository;
	private SyncLogRepository syncLogRepository;

	private record View(Long competitionId, Instant lastSyncedAt)
			implements SyncLogRepository.LastSyncView {
		@Override
		public Long getCompetitionId() {
			return competitionId;
		}

		@Override
		public Instant getLastSyncedAt() {
			return lastSyncedAt;
		}
	}

	@BeforeEach
	void setUp() {
		competitionRepository = mock(CompetitionRepository.class);
		syncLogRepository = mock(SyncLogRepository.class);
	}

	@Test
	@DisplayName("리그는 매일, 컵은 주 1회 — 이틀 전 동기화면 리그만 대상이 된다")
	void cadenceDiffersByCompetitionType() {
		Competition league = competition(39L, CompetitionType.LEAGUE);
		Competition hybrid = competition(2L, CompetitionType.HYBRID);
		Competition cup = competition(45L, CompetitionType.CUP);
		when(competitionRepository.findByDisplayedTrue()).thenReturn(List.of(league, hybrid, cup));

		Instant twoDaysAgo = NOW.minus(Duration.ofHours(48));
		when(syncLogRepository.findLastSuccessPerCompetition()).thenReturn(List.of(
				new View(39L, twoDaysAgo), new View(2L, twoDaysAgo), new View(45L, twoDaysAgo)));

		List<SyncPlanner.Candidate> due = planner(6).selectDue(NOW);

		// 리그 24h는 지났고, 하이브리드 84h·컵 168h는 아직 안 지났다.
		assertThat(due).extracting(c -> c.competition().getId()).containsExactly(39L);
	}

	@Test
	@DisplayName("한 번도 동기화한 적 없는 대회가 가장 먼저 온다")
	void neverSyncedComesFirst() {
		Competition never = competition(140L, CompetitionType.LEAGUE);
		Competition recent = competition(39L, CompetitionType.LEAGUE);
		when(competitionRepository.findByDisplayedTrue()).thenReturn(List.of(recent, never));
		when(syncLogRepository.findLastSuccessPerCompetition())
				.thenReturn(List.of(new View(39L, NOW.minus(Duration.ofHours(30)))));

		List<SyncPlanner.Candidate> due = planner(6).selectDue(NOW);

		assertThat(due).extracting(c -> c.competition().getId()).containsExactly(140L, 39L);
	}

	@Test
	@DisplayName("정원을 넘으면 오래된 것부터 — 잘린 대회가 다음 회차에 맨 앞에 오게")
	void oldestFirstWhenCapped() {
		Competition a = competition(39L, CompetitionType.LEAGUE);
		Competition b = competition(140L, CompetitionType.LEAGUE);
		Competition c = competition(78L, CompetitionType.LEAGUE);
		when(competitionRepository.findByDisplayedTrue()).thenReturn(List.of(a, b, c));
		when(syncLogRepository.findLastSuccessPerCompetition()).thenReturn(List.of(
				new View(39L, NOW.minus(Duration.ofHours(30))),
				new View(140L, NOW.minus(Duration.ofHours(100))),
				new View(78L, NOW.minus(Duration.ofHours(50)))));

		List<SyncPlanner.Candidate> due = planner(2).selectDue(NOW);

		assertThat(due).extracting(x -> x.competition().getId()).containsExactly(140L, 78L);
	}

	@Test
	@DisplayName("주기가 안 지났으면 아무것도 부르지 않는다")
	void nothingDueMeansNoRequests() {
		when(competitionRepository.findByDisplayedTrue())
				.thenReturn(List.of(competition(39L, CompetitionType.LEAGUE)));
		when(syncLogRepository.findLastSuccessPerCompetition())
				.thenReturn(List.of(new View(39L, NOW.minus(Duration.ofHours(1)))));

		assertThat(planner(6).selectDue(NOW)).isEmpty();
	}

	@Test
	@DisplayName("기본 주기라면 하루 최대 소모가 무료 100요청 안에 넉넉히 들어온다")
	void dailyWorstCaseFitsInFreeTier() {
		SyncProperties properties = properties(6);
		// 리그 11 + 하이브리드 3 + 컵 6 = 20개 대회, 대회·시즌당 1요청.
		double perDay = 11 * (24.0 / properties.intervalHoursFor(CompetitionType.LEAGUE))
				+ 3 * (24.0 / properties.intervalHoursFor(CompetitionType.HYBRID))
				+ 6 * (24.0 / properties.intervalHoursFor(CompetitionType.CUP));

		assertThat(perDay).isLessThan(properties.dailyRequestBudget());
		assertThat(perDay).isLessThan(100);
	}

	private SyncPlanner planner(int maxPerRun) {
		return new SyncPlanner(competitionRepository, syncLogRepository, properties(maxPerRun));
	}

	private SyncProperties properties(int maxPerRun) {
		return new SyncProperties(true, 80, maxPerRun, Map.of(), null);
	}

	private Competition competition(Long id, CompetitionType type) {
		return Competition.builder()
				.id(id).name("c" + id).type(type).calendarSeason(false).displayed(true)
				.build();
	}
}
