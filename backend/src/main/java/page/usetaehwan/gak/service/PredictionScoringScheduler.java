package page.usetaehwan.gak.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 채점 트리거. 판단은 하지 않고 "언제 깨울지"만 담당한다
 * ({@link page.usetaehwan.gak.service.sync.SyncScheduler}와 같은 구조).
 *
 * <h2>왜 동기화 20분 뒤인가</h2>
 * 채점의 입력은 <b>동기화가 방금 갱신한 경기 결과</b>다. 동기화보다 먼저 돌면 아직
 * 안 들어온 결과를 보고 전부 보류로 넘기고, 실제 채점은 다음 시간까지 밀린다.
 * 동기화가 매시 10분에 시작하므로 30분에 깨우면 그 회차 결과를 그대로 받는다.
 *
 * <p>동기화가 끝나는 즉시 호출하지 않고 시간으로 떼어 둔 이유는, 그렇게 묶으면 동기화가
 * 실패하거나 오래 걸릴 때 채점까지 같이 멈추기 때문이다. 채점은 외부 API에 의존하지
 * 않는 순수 DB 작업이라 따로 돌 수 있고, 따로 도는 편이 사고를 격리한다.
 *
 * <p>놓쳐도 손해가 없다. 채점은 멱등이고 밀린 건 다음 회차가 그대로 집어 간다.
 */
@Component
public class PredictionScoringScheduler {

	private static final Logger log = LoggerFactory.getLogger(PredictionScoringScheduler.class);

	private final PredictionScoringService scoringService;
	private final boolean enabled;

	public PredictionScoringScheduler(PredictionScoringService scoringService,
	                                  @Value("${gak.scoring.enabled:true}") boolean enabled) {
		this.scoringService = scoringService;
		this.enabled = enabled;
	}

	/** 매시 30분(UTC) — 동기화(매시 10분)가 결과를 채워 넣은 뒤. */
	@Scheduled(cron = "${gak.scoring.cron:0 30 * * * *}", zone = "UTC")
	public void run() {
		if (!enabled) {
			return;
		}
		try {
			scoringService.scorePending();
		} catch (RuntimeException e) {
			// 스케줄러 스레드로 예외가 올라가면 이후 스케줄이 조용히 죽는 경우가 있다.
			log.error("예측 채점 스케줄 실행 실패", e);
		}
	}
}
