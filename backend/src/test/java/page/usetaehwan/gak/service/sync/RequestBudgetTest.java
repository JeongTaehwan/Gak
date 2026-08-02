package page.usetaehwan.gak.service.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import page.usetaehwan.gak.repository.SyncLogRepository;

class RequestBudgetTest {

	private final Clock clock = Clock.fixed(Instant.parse("2026-03-10T15:30:00Z"), ZoneOffset.UTC);
	private final SyncLogRepository syncLogRepository = mock(SyncLogRepository.class);
	private final SyncProperties properties = new SyncProperties(true, 80, 6, Map.of(), null);
	private final RequestBudget budget = new RequestBudget(syncLogRepository, properties, clock);

	@Test
	@DisplayName("오늘 소모량을 빼서 남은 예산을 낸다")
	void remainingIsBudgetMinusSpent() {
		when(syncLogRepository.sumRequestCountSince(any())).thenReturn(18);

		assertThat(budget.spentToday()).isEqualTo(18);
		assertThat(budget.remainingToday()).isEqualTo(62);
		assertThat(budget.canSpend(1)).isTrue();
	}

	@Test
	@DisplayName("예산을 넘겨 썼어도 음수가 되지 않는다")
	void neverNegative() {
		when(syncLogRepository.sumRequestCountSince(any())).thenReturn(95);

		assertThat(budget.remainingToday()).isZero();
		assertThat(budget.canSpend(1)).isFalse();
	}

	@Test
	@DisplayName("합산 기준은 UTC 자정 — 시각이 아니라 '오늘'을 센다")
	void countsFromUtcMidnight() {
		when(syncLogRepository.sumRequestCountSince(any())).thenReturn(0);

		budget.remainingToday();

		ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
		verify(syncLogRepository).sumRequestCountSince(from.capture());
		assertThat(from.getValue()).isEqualTo(Instant.parse("2026-03-10T00:00:00Z"));
	}

	@Test
	@DisplayName("카운터를 메모리가 아니라 이력에서 읽는다 — 재시작해도 오늘 쓴 양이 유지된다")
	void spentIsDerivedFromPersistedHistory() {
		when(syncLogRepository.sumRequestCountSince(any())).thenReturn(40);

		// 같은 인스턴스가 아니어도(=재시작해도) 같은 값이 나온다.
		RequestBudget afterRestart = new RequestBudget(syncLogRepository, properties, clock);
		assertThat(afterRestart.spentToday()).isEqualTo(budget.spentToday());
	}
}
