package page.usetaehwan.gak.dto.analysis;

/**
 * 최근 폼 구간에서 <b>어떤 상대를 만났나</b>.
 *
 * <p>"6경기 4패"까지만 말하면 팀이 무너진 것처럼 읽힌다. "4패인데 그중 3경기가 당시
 * 상위 6위 안쪽 상대였다"가 되면 같은 숫자가 다른 말을 한다. 이 앱이 처음부터 하려던
 * 진단이 이것이다.
 *
 * <h2>순위는 "그 경기 시점"의 것이다</h2>
 * <p>시즌 최종 순위로 8월 경기를 평가하면 <b>그 경기 시점에 존재하지 않던 정보로 과거를
 * 판단하는</b> 것이 된다. 이 앱은 예측에서 이미 같은 문제를 막아 뒀다 — 킥오프 이전에만
 * 예측을 받는다. 상대 강도에서 최종 순위를 쓰면 같은 앱이 반대편에서 자기 규칙을 어긴다.
 * 그래서 {@link page.usetaehwan.gak.service.analysis.LeagueTable} 이 경기 결과로 그 시점
 * 표를 다시 만든다.
 *
 * @param measured        순위를 알아낸 경기 수
 * @param unmeasured      순위를 못 매긴 경기 수(컵대회이거나 시즌 초라 표본이 얇음)
 * @param averageRank     만난 상대들의 평균 순위. {@code measured} 가 0이면 null
 * @param tableSize       순위의 분모(그 리그 팀 수). "20팀 중 3위"와 "8팀 중 3위"는 다르다
 * @param topCut          "상위권"의 경계 순위(표 크기의 30%). 20팀이면 6
 * @param vsTop           상위권 상대 성적
 * @param vsRest          그 외 상대 성적
 * @param deductionsKnown 승점 삭감을 반영했는가. 못 했으면 순위가 실제와 다를 수 있다
 */
public record OpponentStrength(
		int measured,
		int unmeasured,
		Double averageRank,
		Integer tableSize,
		Integer topCut,
		Split vsTop,
		Split vsRest,
		boolean deductionsKnown
) {

	/**
	 * 어떤 상대 무리를 상대로 얼마나 땄나.
	 *
	 * @param pointsRate 승점률. <b>표본이 얇으면 null</b> — 2경기 1승을 "50%"라고 적으면
	 *                   소수점이 표본의 빈약함을 가린다({@link SampleConfidence})
	 */
	public record Split(int matches, int wins, int draws, int losses,
	                    int points, int maxPoints, Double pointsRate) {

		public static final Split EMPTY = new Split(0, 0, 0, 0, 0, 0, null);
	}

	/** 순위를 하나도 못 매겼을 때 — 0이 아니라 "모름"이다. */
	public static OpponentStrength unmeasured(int matches) {
		return new OpponentStrength(0, matches, null, null, null,
				Split.EMPTY, Split.EMPTY, false);
	}

	/** 이 지표를 화면에 띄워도 되는가. */
	public boolean available() {
		return measured > 0;
	}
}
