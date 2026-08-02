package page.usetaehwan.gak.service.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.config.SyncProperties;

/**
 * 주기 동기화 트리거. 판단은 하지 않는다 — "언제 깨울지"만 담당하고,
 * 무엇을 얼마나 동기화할지는 {@link SyncPlanner}와 {@link RequestBudget}이 정한다.
 *
 * <h2>왜 하루 한 번이 아니라 매시간인가</h2>
 * 하루 한 번 몰아서 20요청을 쏘면, 그 시각에 API가 죽어 있을 때 그날 갱신이 통째로 날아간다.
 * 매시간 깨어나 "주기가 지난 대회 몇 개"만 처리하면
 * <ul>
 *   <li>실패한 대회가 한 시간 뒤 자동으로 재시도된다(별도 재시도 로직이 필요 없다)</li>
 *   <li>요청이 하루에 걸쳐 퍼져 순간 버스트가 없다</li>
 *   <li>경기 종료 직후 결과가 최대 1시간 안에 반영된다</li>
 * </ul>
 * 그래도 총량은 늘지 않는다. 갱신 주기(리그 24h / 컵 168h)가 실제 호출 수를 정하지,
 * 깨어나는 빈도가 정하는 게 아니다.
 */
@Component
public class SyncScheduler {

	private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

	private final FixtureSyncService syncService;
	private final SyncProperties properties;

	public SyncScheduler(FixtureSyncService syncService, SyncProperties properties) {
		this.syncService = syncService;
		this.properties = properties;
	}

	/** 매시 10분(UTC). 정각을 피한 건 다른 배치들과 겹치지 않게 하려는 것뿐이다. */
	@Scheduled(cron = "${gak.sync.cron:0 10 * * * *}", zone = "UTC")
	public void run() {
		if (!properties.enabled()) {
			return;
		}
		try {
			syncService.syncDueCompetitions();
		} catch (RuntimeException e) {
			// 스케줄러 스레드로 예외가 올라가면 이후 스케줄이 조용히 죽는 경우가 있다.
			// 개별 대회 실패는 이미 서비스가 삼키므로, 여기 오는 건 계획 수립 단계의 사고다.
			log.error("동기화 스케줄 실행 실패", e);
		}
	}
}
