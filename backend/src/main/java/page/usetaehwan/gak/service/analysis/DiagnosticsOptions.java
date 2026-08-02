package page.usetaehwan.gak.service.analysis;

import java.time.Instant;

/**
 * 진단 계산의 기준값. 저장하지 않고 호출마다 넘긴다 — 기준을 바꿔 보는 것("14일 5경기
 * 말고 10일 4경기면?")이 이 앱에선 흔한 조작이라, 기준을 코드 상수로 굳히면 그때마다
 * 배포해야 한다.
 *
 * @param windowDays  밀집 판정 창 폭(일). 양 끝 포함
 * @param minMatches  그 창 안에 몇 경기부터 밀집으로 볼지
 * @param formSize    최근 폼을 몇 경기로 볼지
 * @param travelFrom  이동거리 집계 시작(포함). null이면 전체 기간
 * @param travelTo    이동거리 집계 끝(포함). null이면 전체 기간
 */
public record DiagnosticsOptions(
		int windowDays,
		int minMatches,
		int formSize,
		Instant travelFrom,
		Instant travelTo
) {

	/** 기본값 — 프론트({@code lib/timeline/congestion.ts})와 같은 14일 5경기, 최근 6경기. */
	public static final DiagnosticsOptions DEFAULTS = new DiagnosticsOptions(14, 5, 6, null, null);

	public DiagnosticsOptions {
		if (windowDays < 1) {
			throw new IllegalArgumentException("창 폭은 1일 이상이어야 합니다. windowDays=" + windowDays);
		}
		if (minMatches < 2) {
			throw new IllegalArgumentException("밀집 기준은 2경기 이상이어야 합니다. minMatches=" + minMatches);
		}
		if (formSize < 1) {
			throw new IllegalArgumentException("폼 경기 수는 1 이상이어야 합니다. formSize=" + formSize);
		}
		if (travelFrom != null && travelTo != null && travelFrom.isAfter(travelTo)) {
			throw new IllegalArgumentException("이동거리 집계 기간의 시작이 끝보다 뒤입니다.");
		}
	}

	public DiagnosticsOptions withTravelPeriod(Instant from, Instant to) {
		return new DiagnosticsOptions(windowDays, minMatches, formSize, from, to);
	}

	/** 이 경기 시각이 이동거리 집계 기간 안인가(경계 포함, null이면 제한 없음). */
	public boolean travelPeriodContains(Instant kickoff) {
		if (travelFrom != null && kickoff.isBefore(travelFrom)) {
			return false;
		}
		return travelTo == null || !kickoff.isAfter(travelTo);
	}
}
