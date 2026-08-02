package page.usetaehwan.gak.service.sync;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import page.usetaehwan.gak.config.SyncProperties;
import page.usetaehwan.gak.domain.Competition;
import page.usetaehwan.gak.repository.CompetitionRepository;
import page.usetaehwan.gak.repository.SyncLogRepository;

/**
 * 이번에 어느 대회를 동기화할지 고른다. 예산 배분의 핵심이 여기 있다.
 *
 * <h2>왜 성격별로 주기를 다르게 두는가</h2>
 * 리그는 주중·주말로 라운드가 촘촘해 하루만 지나도 결과가 바뀐다. 반면 컵대회는
 * 라운드 사이가 한 달씩 벌어지기도 한다 — 매일 부르면 <b>똑같은 응답</b>에 요청만 태운다.
 * 그래서 리그 24시간 / 하이브리드(조별+녹아웃) 84시간 / 컵 168시간을 기본으로 둔다.
 * 이 조합이면 하루 최대 소모가 리그 11 + 하이브리드 1 + 컵 1 ≈ 13요청으로,
 * 무료 100요청 안에 개발·수동 호출 여유까지 남는다.
 *
 * <h2>왜 "오래된 것부터"인가</h2>
 * 예산이 모자라 잘릴 때 잘리는 쪽이 항상 같은 대회면 그 대회는 영영 갱신되지 않는다.
 * 마지막 성공 시각이 오래된 순으로 정렬해 두면 잘린 대회가 다음 회차에서 맨 앞에 온다.
 */
@Component
public class SyncPlanner {

	private final CompetitionRepository competitionRepository;
	private final SyncLogRepository syncLogRepository;
	private final SyncProperties properties;

	public SyncPlanner(CompetitionRepository competitionRepository,
	                   SyncLogRepository syncLogRepository,
	                   SyncProperties properties) {
		this.competitionRepository = competitionRepository;
		this.syncLogRepository = syncLogRepository;
		this.properties = properties;
	}

	/**
	 * @param competition  대상 대회
	 * @param lastSyncedAt 마지막 성공 시각(한 번도 없으면 null)
	 */
	public record Candidate(Competition competition, Instant lastSyncedAt) {
	}

	/** 지금 시점에 갱신 주기가 지난 대회들. 오래된 순, {@code maxCompetitionsPerRun}개까지. */
	public List<Candidate> selectDue(Instant now) {
		Map<Long, Instant> lastSync = lastSuccessByCompetition();

		List<Candidate> due = new ArrayList<>();
		for (Competition competition : competitionRepository.findByDisplayedTrue()) {
			Instant last = lastSync.get(competition.getId());
			if (isDue(competition, last, now)) {
				due.add(new Candidate(competition, last));
			}
		}

		// 한 번도 동기화 안 된 대회(null)가 가장 오래된 것으로 취급돼 맨 앞에 온다.
		due.sort(Comparator.comparing(Candidate::lastSyncedAt,
				Comparator.nullsFirst(Comparator.naturalOrder())));

		int limit = Math.min(due.size(), properties.maxCompetitionsPerRun());
		return List.copyOf(due.subList(0, limit));
	}

	private boolean isDue(Competition competition, Instant lastSyncedAt, Instant now) {
		if (lastSyncedAt == null) {
			return true;
		}
		Duration interval = Duration.ofHours(properties.intervalHoursFor(competition.getType()));
		return !lastSyncedAt.plus(interval).isAfter(now);
	}

	private Map<Long, Instant> lastSuccessByCompetition() {
		Map<Long, Instant> map = new HashMap<>();
		for (SyncLogRepository.LastSyncView view : syncLogRepository.findLastSuccessPerCompetition()) {
			map.put(view.getCompetitionId(), view.getLastSyncedAt());
		}
		return map;
	}
}
