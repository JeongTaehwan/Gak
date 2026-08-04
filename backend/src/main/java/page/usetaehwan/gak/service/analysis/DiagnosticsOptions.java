package page.usetaehwan.gak.service.analysis;

import java.time.Clock;
import java.time.Instant;

/**
 * 진단 계산의 기준값. 저장하지 않고 호출마다 넘긴다 — 기준을 바꿔 보는 것("14일 5경기
 * 말고 10일 4경기면?")이 이 앱에선 흔한 조작이라, 기준을 코드 상수로 굳히면 그때마다
 * 배포해야 한다.
 *
 * <h2>기간도 파라미터다 — 다만 지금은 UI가 없다</h2>
 * <p>{@link #season}·{@link #asOf}가 "언제를 진단할 것인가"를 정한다. 둘 다 null이면
 * 기본 규칙이 적용된다: <b>가장 최신 시즌의, 지금까지 치른 경기 전체</b>
 * ({@link AnalysisPeriodResolver} 참고). 기간 선택 화면은 나중에 붙이기로 했지만, 계산
 * 쪽이 기간을 상수로 갖고 있으면 그때 코드를 뜯어야 하므로 자리를 미리 열어 둔다.
 *
 * <h2>폼 경기 수가 사라진 이유</h2>
 * <p>예전에는 {@code formSize}(최근 6경기)가 따로 있었다. 그러면 폼만 다른 기간을 보게
 * 되고, 화면에서 "6경기 4패" 옆에 시즌 전체의 밀집 구간이 나란히 놓인다 — 그 4패가
 * 그 밀집 구간에서 나온 것처럼 읽히지만 근거가 없다. 이제 <b>모든 지표가 같은 기간</b>을
 * 본다.
 *
 * @param windowDays 밀집 판정 창 폭(일). 양 끝 포함
 * @param minMatches 그 창 안에 몇 경기부터 밀집으로 볼지
 * @param season     볼 시즌(API 시즌 번호). null이면 자동 — 치른 경기가 있는 최신 시즌
 * @param asOf       "여기까지 치른 경기"의 기준 시각. null이면 서버 시계의 지금
 */
public record DiagnosticsOptions(
		int windowDays,
		int minMatches,
		Integer season,
		Instant asOf
) {

	/** 기본값 — 14일 5경기, 기간은 자동(최신 시즌 · 치른 경기까지). */
	public static final DiagnosticsOptions DEFAULTS = new DiagnosticsOptions(14, 5, null, null);

	public DiagnosticsOptions {
		if (windowDays < 1) {
			throw new IllegalArgumentException("창 폭은 1일 이상이어야 합니다. windowDays=" + windowDays);
		}
		if (minMatches < 2) {
			throw new IllegalArgumentException("밀집 기준은 2경기 이상이어야 합니다. minMatches=" + minMatches);
		}
	}

	public DiagnosticsOptions withSeason(Integer season) {
		return new DiagnosticsOptions(windowDays, minMatches, season, asOf);
	}

	public DiagnosticsOptions withAsOf(Instant asOf) {
		return new DiagnosticsOptions(windowDays, minMatches, season, asOf);
	}

	/** 이번 계산의 "지금". 지정하지 않았으면 서버 시계를 쓴다 — 클라이언트 시각은 믿지 않는다. */
	public Instant asOfOr(Clock clock) {
		return asOf != null ? asOf : Instant.now(clock);
	}
}
