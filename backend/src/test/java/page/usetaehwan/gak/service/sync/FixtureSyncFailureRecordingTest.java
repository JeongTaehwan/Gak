package page.usetaehwan.gak.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import page.usetaehwan.gak.config.SyncProperties;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.domain.CompetitionType;
import page.usetaehwan.gak.domain.SyncLog;
import page.usetaehwan.gak.domain.SyncSource;
import page.usetaehwan.gak.domain.SyncStatus;
import page.usetaehwan.gak.external.apifootball.ApiFootballClient;
import page.usetaehwan.gak.external.apifootball.ApiFootballException;
import page.usetaehwan.gak.external.apifootball.ReplayDataMissingException;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;

/**
 * <b>무엇을 이력에 남기고 무엇을 남기지 않는가.</b>
 *
 * <p>구분의 기준은 "시도했는가"다. 통신 실패나 API의 errors 응답은 시도한 결과라 남겨야
 * 재시도 판단과 원인 추적이 되고, 재생 파일이 없는 건 시도조차 못 한 상태다.
 * 후자를 매 주기 쌓으면 하루 400줄이 되고, 그 안에 진짜 장애가 묻힌다.
 */
class FixtureSyncFailureRecordingTest {

	private static final Instant NOW = Instant.parse("2026-03-10T12:00:00Z");

	private final ApiFootballClient client = mock(ApiFootballClient.class);
	private final FixtureUpsertService upsertService = mock(FixtureUpsertService.class);
	private final CompetitionRepository competitionRepository = mock(CompetitionRepository.class);
	private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
	private final SyncPlanner planner = mock(SyncPlanner.class);
	private final RequestBudget budget = mock(RequestBudget.class);

	private final FixtureSyncService syncService = new FixtureSyncService(
			client, upsertService, competitionRepository, syncLogRepository, planner, budget,
			new SyncProperties(true, 80, 6, Map.of(), 2024),
			Clock.fixed(NOW, ZoneOffset.UTC));

	private final Competition competition = Competition.builder()
			.id(140L).name("La Liga").type(CompetitionType.LEAGUE)
			.calendarSeason(false).displayed(true)
			.build();

	@Test
	@DisplayName("재생 파일 없음 — 이력에 저장하지 않는다")
	void replayDataMissingIsNotPersisted() {
		when(client.source()).thenReturn(SyncSource.REPLAY);
		when(client.fetchFixtures(anyLong(), anyInt()))
				.thenThrow(new ReplayDataMissingException("재생할 응답 파일이 없습니다: ..."));

		SyncLog result = syncService.syncCompetition(competition);

		verify(syncLogRepository, never()).save(any());
		assertThat(result.getStatus()).isEqualTo(SyncStatus.SKIPPED);
		assertThat(result.isRecorded()).isFalse();
		assertThat(result.getRequestCount()).isZero();
	}

	@Test
	@DisplayName("통신 실패·타임아웃 — FAILED로 저장한다")
	void networkFailureIsPersisted() {
		when(client.source()).thenReturn(SyncSource.REAL);
		when(client.fetchFixtures(anyLong(), anyInt())).thenThrow(
				new ApiFootballException("API-Football 통신 실패/타임아웃 (fixtures league=140): timeout", 1));

		syncService.syncCompetition(competition);

		SyncLog saved = captureSaved();
		assertThat(saved.getStatus()).isEqualTo(SyncStatus.FAILED);
		assertThat(saved.getMessage()).contains("타임아웃");
		// 요청은 나갔을 수 있으므로 소모량을 보수적으로 계산해 남긴다.
		assertThat(saved.getRequestCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("HTTP 200 + body의 errors — FAILED로 저장한다")
	void apiErrorResponseIsPersisted() {
		when(client.source()).thenReturn(SyncSource.REAL);
		when(client.fetchFixtures(anyLong(), anyInt())).thenThrow(new ApiFootballException(
				"API-Football이 오류를 반환했습니다: plan: Free plans do not have access to this season", 1));

		syncService.syncCompetition(competition);

		SyncLog saved = captureSaved();
		assertThat(saved.getStatus()).isEqualTo(SyncStatus.FAILED);
		// 플랜 제한 메시지가 그대로 남아야 왜 안 됐는지 안다.
		assertThat(saved.getMessage()).contains("Free plans do not have access");
	}

	@Test
	@DisplayName("DB 반영 단계 실패 — FAILED로 저장하고 소모한 요청 수를 남긴다")
	void upsertFailureIsPersisted() {
		when(client.source()).thenReturn(SyncSource.REAL);
		when(client.fetchFixtures(anyLong(), anyInt()))
				.thenReturn(new ApiFootballClient.FixturesFetch(java.util.List.of(), 1));
		when(upsertService.upsert(anyLong(), anyInt(), anyList()))
				.thenThrow(new IllegalStateException("매핑 실패"));

		syncService.syncCompetition(competition);

		SyncLog saved = captureSaved();
		assertThat(saved.getStatus()).isEqualTo(SyncStatus.FAILED);
		assertThat(saved.getMessage()).contains("매핑 실패");
		// 응답은 이미 받았으니 요청 1회는 실제로 소모됐다.
		assertThat(saved.getRequestCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("일일 예산 소진 — SKIPPED로 저장한다(그날 무엇을 못 했는지가 정보다)")
	void budgetExhaustionIsPersisted() {
		when(client.source()).thenReturn(SyncSource.REAL);
		when(budget.canSpend(1)).thenReturn(false);
		when(budget.remainingToday()).thenReturn(0);
		when(planner.selectDue(any()))
				.thenReturn(java.util.List.of(new SyncPlanner.Candidate(competition, null)));

		syncService.syncDueCompetitions();

		SyncLog saved = captureSaved();
		assertThat(saved.getStatus()).isEqualTo(SyncStatus.SKIPPED);
		assertThat(saved.getMessage()).contains("예산 소진");
		verify(client, never()).fetchFixtures(anyLong(), anyInt());
	}

	private SyncLog captureSaved() {
		ArgumentCaptor<SyncLog> captor = ArgumentCaptor.forClass(SyncLog.class);
		verify(syncLogRepository).save(captor.capture());
		return captor.getValue();
	}
}
