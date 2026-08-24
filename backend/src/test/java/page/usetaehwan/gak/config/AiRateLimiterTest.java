// requirements.md DG 8절 — 전역 상한 + IP 단위 상한의 조합 (test-cases.md DG-35·DG-36)
package page.usetaehwan.gak.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.config.AiRateLimiter.Decision;

class AiRateLimiterTest {

	private static final Instant NOON = Instant.parse("2026-08-25T12:00:00Z");

	@Test
	@DisplayName("전역 상한에 도달하면 어느 IP 든 거부된다")
	void globalLimitBlocksEveryone() {
		AiRateLimiter limiter = limiter(true, 3, 100, Clock.fixed(NOON, ZoneOffset.UTC));

		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("2.2.2.2")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("3.3.3.3")).isEqualTo(Decision.ALLOWED);

		// 처음 보는 IP 도, 이미 쓴 IP 도 똑같이 전역 초과다.
		assertThat(limiter.tryConsume("4.4.4.4")).isEqualTo(Decision.GLOBAL_EXCEEDED);
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.GLOBAL_EXCEEDED);
	}

	@Test
	@DisplayName("IP 상한 도달은 그 IP 만 거부한다 — 다른 IP 는 통과")
	void ipLimitBlocksOnlyThatIp() {
		AiRateLimiter limiter = limiter(true, 100, 2, Clock.fixed(NOON, ZoneOffset.UTC));

		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.IP_EXCEEDED);

		assertThat(limiter.tryConsume("2.2.2.2")).isEqualTo(Decision.ALLOWED);
	}

	@Test
	@DisplayName("IP 초과로 거부된 요청은 전역 예산을 소모하지 않는다 — 한 IP 의 남용이 전체를 잠그지 못한다")
	void ipRejectionDoesNotBurnGlobalBudget() {
		AiRateLimiter limiter = limiter(true, 3, 1, Clock.fixed(NOON, ZoneOffset.UTC));

		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);

		// 같은 IP 가 아무리 두드려도 IP 초과일 뿐, 전역 카운터는 그대로다.
		for (int i = 0; i < 5; i++) {
			assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.IP_EXCEEDED);
		}

		// 전역 잔여 2 가 남아 있어 다른 IP 둘이 통과하고, 그다음이 전역 초과다.
		assertThat(limiter.tryConsume("2.2.2.2")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("3.3.3.3")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("4.4.4.4")).isEqualTo(Decision.GLOBAL_EXCEEDED);
	}

	@Test
	@DisplayName("UTC 날짜가 바뀌면 전역·IP 카운터가 함께 리셋된다")
	void countersResetOnDateRollover() {
		MutableClock clock = new MutableClock(NOON);
		AiRateLimiter limiter = limiter(true, 2, 1, clock);

		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.IP_EXCEEDED);
		assertThat(limiter.tryConsume("2.2.2.2")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("3.3.3.3")).isEqualTo(Decision.GLOBAL_EXCEEDED);

		clock.advance(Duration.ofDays(1));

		// 어제 IP 초과였던 IP 도, 어제 전역 초과였던 상태도 새 하루에는 남지 않는다.
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("2.2.2.2")).isEqualTo(Decision.ALLOWED);
	}

	@Test
	@DisplayName("전역 초과 상태에서는 새 IP 가 와도 IP 맵이 자라지 않는다 — 맵 크기는 전역 상한으로 유계")
	void ipMapStaysBoundedAfterGlobalExhaustion() {
		AiRateLimiter limiter = limiter(true, 2, 10, Clock.fixed(NOON, ZoneOffset.UTC));

		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		assertThat(limiter.trackedIpCount()).isEqualTo(1);

		// IP 를 바꿔 가며 뿌려도 맵에는 항목이 생기지 않는다.
		for (int i = 0; i < 50; i++) {
			assertThat(limiter.tryConsume("10.0.0." + i)).isEqualTo(Decision.GLOBAL_EXCEEDED);
		}
		assertThat(limiter.trackedIpCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("enabled=false 면 상한과 무관하게 전부 통과한다")
	void disabledPassesEverything() {
		AiRateLimiter limiter = limiter(false, 1, 1, Clock.fixed(NOON, ZoneOffset.UTC));

		for (int i = 0; i < 10; i++) {
			assertThat(limiter.tryConsume("1.1.1.1")).isEqualTo(Decision.ALLOWED);
		}
		// 꺼진 상태에서는 카운터도 쌓지 않는다.
		assertThat(limiter.trackedIpCount()).isZero();
	}

	@Test
	@DisplayName("식별 불가(null IP)는 전역 상한만 적용 — 버킷을 만들지 않는다")
	void nullIpConsumesOnlyTheGlobalBudget() {
		AiRateLimiter limiter = limiter(true, 2, 1, Clock.fixed(NOON, ZoneOffset.UTC));

		assertThat(limiter.tryConsume(null)).isEqualTo(AiRateLimiter.Decision.ALLOWED);
		assertThat(limiter.tryConsume(null)).isEqualTo(AiRateLimiter.Decision.ALLOWED);
		// IP당 상한(1)을 넘었지만 식별 불가라 IP 판정 자체가 없다.
		assertThat(limiter.trackedIpCount()).isZero();
		// 전역 상한(2)은 그대로 적용된다.
		assertThat(limiter.tryConsume(null)).isEqualTo(AiRateLimiter.Decision.GLOBAL_EXCEEDED);
	}

	private static AiRateLimiter limiter(boolean enabled, int globalDaily, int perIpDaily, Clock clock) {
		return new AiRateLimiter(new AiRateLimitProperties(enabled, globalDaily, perIpDaily, null), clock);
	}

	/** 하루 경계를 넘길 수 있는 고정 시계. */
	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant start) {
			this.now = start;
		}

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return now;
		}
	}
}
