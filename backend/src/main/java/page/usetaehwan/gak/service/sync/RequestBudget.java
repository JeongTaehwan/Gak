package page.usetaehwan.gak.service.sync;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.config.SyncProperties;
import page.usetaehwan.gak.repository.SyncLogRepository;

/**
 * 하루 요청 예산 관리. 무료 티어 100요청이 이 앱의 하드 리밋이라 <b>넘기 전에 멈추는</b>
 * 장치가 필요하다. 넘고 나서 429를 받아 처리하는 건 이미 늦다 — 그날 남은 동기화가 전부 막힌다.
 *
 * <p>카운터를 메모리에 두지 않고 매번 {@code sync_log}에서 합산한다. 개발 중에는 앱을
 * 하루에도 몇 번씩 재시작하는데, 그때마다 카운터가 0으로 돌아가면 예산 제어가 무의미해진다.
 * 이력이 이미 "요청을 몇 번 썼는지"를 들고 있으니 그걸 그대로 쓴다.
 *
 * <p>기준은 UTC 자정. API 제공자의 리셋 시점과 정확히 일치한다는 보장은 없으므로
 * 기본 예산을 100이 아니라 80으로 두어 경계에서의 오차를 흡수한다.
 */
@Component
public class RequestBudget {

	private final SyncLogRepository syncLogRepository;
	private final SyncProperties properties;
	private final Clock clock;

	public RequestBudget(SyncLogRepository syncLogRepository, SyncProperties properties, Clock clock) {
		this.syncLogRepository = syncLogRepository;
		this.properties = properties;
		this.clock = clock;
	}

	/** 오늘 이미 소모한 요청 수. */
	public int spentToday() {
		return syncLogRepository.sumRequestCountSince(todayStart());
	}

	/** 오늘 더 쓸 수 있는 요청 수(음수는 0으로). */
	public int remainingToday() {
		return Math.max(0, properties.dailyRequestBudget() - spentToday());
	}

	public boolean canSpend(int requests) {
		return remainingToday() >= requests;
	}

	private Instant todayStart() {
		return Instant.now(clock).truncatedTo(ChronoUnit.DAYS);
	}
}
