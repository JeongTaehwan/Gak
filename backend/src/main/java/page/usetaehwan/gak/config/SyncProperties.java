package page.usetaehwan.gak.config;

import java.util.EnumMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import page.usetaehwan.gak.domain.CompetitionType;

/**
 * 동기화 정책. 무료 티어 <b>하루 100요청</b>이 이 앱 설계의 상수라서,
 * "얼마나 자주 / 최대 몇 번" 을 코드가 아니라 설정으로 잡아 둔다.
 *
 * @param enabled              스케줄러 on/off
 * @param dailyRequestBudget   하루에 쓸 수 있는 요청 상한(무료 100 중 여유를 남긴 값)
 * @param maxCompetitionsPerRun 한 번 깨어날 때 처리할 대회 수 상한(버스트 방지)
 * @param intervalHours        대회 성격별 최소 재동기화 간격(시간)
 * @param seasonOverride       시즌 강제 지정. 무료 플랜이 최신 시즌을 막을 때 쓴다(null이면 자동 판정)
 */
@ConfigurationProperties(prefix = "gak.sync")
public record SyncProperties(
		boolean enabled,
		int dailyRequestBudget,
		int maxCompetitionsPerRun,
		Map<CompetitionType, Integer> intervalHours,
		Integer seasonOverride
) {

	/** 성격별 기본 간격 — 리그는 매일, 하이브리드는 주 2회, 컵은 주 1회. */
	private static final Map<CompetitionType, Integer> DEFAULT_INTERVALS = Map.of(
			CompetitionType.LEAGUE, 24,
			CompetitionType.HYBRID, 84,
			CompetitionType.CUP, 168);

	public SyncProperties {
		dailyRequestBudget = dailyRequestBudget > 0 ? dailyRequestBudget : 80;
		maxCompetitionsPerRun = maxCompetitionsPerRun > 0 ? maxCompetitionsPerRun : 6;

		Map<CompetitionType, Integer> merged = new EnumMap<>(DEFAULT_INTERVALS);
		if (intervalHours != null) {
			intervalHours.forEach((type, hours) -> {
				if (type != null && hours != null && hours > 0) {
					merged.put(type, hours);
				}
			});
		}
		intervalHours = Map.copyOf(merged);
	}

	/** 해당 성격의 대회를 몇 시간마다 다시 볼 것인가. */
	public int intervalHoursFor(CompetitionType type) {
		return intervalHours.getOrDefault(type, 24);
	}
}
