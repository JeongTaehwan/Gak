package page.usetaehwan.gak.service.analysis;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import page.usetaehwan.gak.domain.Fixture;

/**
 * <b>그 시점의</b> 리그 순위표를 경기 결과로부터 만든다.
 *
 * <h2>왜 API 대신 계산하나</h2>
 * <p>{@code /standings} 는 <b>호출 시점의 표 하나</b>만 준다. 지난 시즌이면 최종 표이고,
 * 날짜를 지정할 방법이 없다. 그런데 8월 경기를 5월의 최종 순위로 평가하면 <b>그 경기
 * 시점에 존재하지 않던 정보로 과거를 판단하는</b> 것이 된다.
 *
 * <p>이 앱은 이미 같은 문제를 반대편에서 해결해 뒀다 — <b>예측은 킥오프 이전에만 받는다.</b>
 * 사후 정보로 성적을 매기지 않겠다는 규칙이고, 그게 이 앱의 존재 이유다. 상대 강도에
 * 최종 순위를 쓰면 같은 앱이 자기 규칙을 어긴다.
 *
 * <p>그래서 계산한다. 리그 순위는 <b>결과의 함수</b>다(승점 3/1/0 · 득실차 · 다득점).
 * 우리는 리그 전 경기를 이미 저장하고 있으므로({@code FixtureUpsertService} 는 팀으로
 * 거르지 않는다) 특정 날짜까지의 경기만 접으면 그 시점 표가 나온다. 이건 이 프로젝트의
 * 기존 원칙 그대로다 — <b>사실만 저장하고 파생값은 계산한다.</b>
 *
 * <h2>이 계산이 틀릴 수 있는 지점</h2>
 * <ol>
 *   <li><b>승점 삭감을 모른다.</b> 2023-24 에버턴 −10, 노팅엄 −4 같은 건 경기 결과에
 *       나타나지 않는다. {@code /standings} 최종 표와 대조하면 차이가 곧 삭감분이라
 *       나중에 보정할 수 있지만, 지금은 모른다.</li>
 *   <li><b>동점 처리 규칙이 리그마다 다르다.</b> 여기서는 프리미어리그 방식(득실차 →
 *       다득점)을 쓴다. 세리에A는 상대전적이 우선이라 순위가 한두 칸 달라질 수 있다.
 *       다만 이 값은 "상위권이었나"를 보려고 쓰는 것이라 한두 칸 차이는 대개 무해하다.</li>
 *   <li><b>시즌 초 순위는 순위가 아니다.</b> 2라운드 시점의 1위는 2경기 결과일 뿐이다.
 *       그래서 {@link Snapshot#rankOf(long)} 는 경기 수가 적은 팀을 <b>순위 없음</b>으로
 *       돌려준다 — 모르는 것을 아는 척하지 않는다.</li>
 *   <li><b>경기 수가 고르지 않으면 순위 자체가 왜곡된다.</b> 순위는 표에 오른 전 팀을
 *       놓고 매기므로, 2경기 6점인 팀이 8경기 5점인 팀보다 위에 온다. 정규 리그는 모든
 *       팀이 같은 라운드를 치러 이 문제가 사실상 없지만, 연기 경기가 몰린 시기나 컵
 *       일정이 겹친 리그에서는 한두 칸 어긋날 수 있다. 상위권 판정에는 대개 무해하다.</li>
 * </ol>
 */
public final class LeagueTable {

	/**
	 * 순위를 믿을 만해지는 최소 경기 수.
	 *
	 * <p>이 값 미만으로 치른 팀은 순위를 주지 않는다. 2경기 치르고 1위인 팀을 "상위권
	 * 상대"라고 부르면, 우리가 만든 숫자가 우리를 속인다.
	 */
	public static final int MIN_MATCHES_FOR_RANK = 5;

	private static final int POINTS_FOR_WIN = 3;
	private static final int POINTS_FOR_DRAW = 1;

	private LeagueTable() {
	}

	/**
	 * 한 팀의 성적 한 줄.
	 *
	 * @param rank 순위. 경기 수가 {@link #MIN_MATCHES_FOR_RANK} 에 못 미치면 순위를 매기되
	 *             {@link Snapshot#rankOf(long)} 가 걸러 준다
	 */
	public record Row(long teamId, int rank, int played, int points, int goalsFor, int goalsAgainst) {
		public int goalsDiff() {
			return goalsFor - goalsAgainst;
		}
	}

	/**
	 * 특정 시점의 표.
	 *
	 * @param rows   순위순
	 * @param byTeam 팀 id → 그 팀의 줄
	 */
	public record Snapshot(List<Row> rows, Map<Long, Row> byTeam) {

		/** 표에 오른 팀 수 — 순위의 분모다. "20팀 중 3위"와 "8팀 중 3위"는 다른 말이다. */
		public int size() {
			return rows.size();
		}

		/**
		 * 이 시점에 <b>믿을 만한</b> 순위. 표에 없거나 경기 수가 모자라면 null.
		 *
		 * <p>null 은 "순위가 없다"가 아니라 <b>"이 시점에는 순위를 말할 수 없다"</b>는 뜻이다.
		 */
		public Integer rankOf(long teamId) {
			Row row = byTeam.get(teamId);
			if (row == null || row.played() < MIN_MATCHES_FOR_RANK) {
				return null;
			}
			return row.rank();
		}

		/** 상위권 경계 — 표 크기의 30%. 20팀이면 6위까지, 18팀이면 5위까지. */
		public int topCut() {
			return Math.max(1, (int) Math.round(size() * 0.3));
		}

		public boolean isTop(long teamId) {
			Integer rank = rankOf(teamId);
			return rank != null && rank <= topCut();
		}
	}

	/** 승점 삭감을 모르는 경우 — 순수하게 경기 결과만으로 만든 표. */
	public static Snapshot at(List<Fixture> fixtures, Instant asOf) {
		return at(fixtures, asOf, Map.of());
	}

	/**
	 * {@code asOf} <b>이전에 킥오프한</b> 경기까지만 반영한 표.
	 *
	 * <p>경계는 {@code kickoff < asOf} 다 — 그 경기 자체는 넣지 않는다. "이 경기를 치르기
	 * 직전에 상대가 몇 위였나"를 묻는 것이므로, 결과를 이미 아는 채로 순위를 매기면 안 된다.
	 *
	 * @param fixtures   그 대회·시즌의 <b>전 경기</b>. 한 팀 것만 넘기면 표가 성립하지 않는다
	 * @param deductions 팀 id → 깎을 승점(양수). {@code /standings} 와 대조해 얻는다.
	 *                   비어 있으면 삭감을 <b>모르는</b> 상태이고, 그건 삭감이 0이라는 뜻이 아니다
	 */
	public static Snapshot at(List<Fixture> fixtures, Instant asOf, Map<Long, Integer> deductions) {
		Map<Long, int[]> tally = new HashMap<>();   // teamId → [played, points, gf, ga]

		for (Fixture f : fixtures) {
			if (f.getKickoff() == null || !f.getKickoff().isBefore(asOf)) {
				continue;
			}
			if (!SchedulePolicy.countsForForm(f)) {
				continue;   // 안 끝났거나 스코어를 모르는 경기는 표에 넣지 않는다
			}
			long home = f.getHomeTeam().getId();
			long away = f.getAwayTeam().getId();
			int hg = f.getGoalsHome();
			int ag = f.getGoalsAway();

			int[] h = tally.computeIfAbsent(home, k -> new int[4]);
			int[] a = tally.computeIfAbsent(away, k -> new int[4]);
			h[0]++; a[0]++;
			h[2] += hg; h[3] += ag;
			a[2] += ag; a[3] += hg;
			if (hg > ag) {
				h[1] += POINTS_FOR_WIN;
			} else if (hg < ag) {
				a[1] += POINTS_FOR_WIN;
			} else {
				h[1] += POINTS_FOR_DRAW;
				a[1] += POINTS_FOR_DRAW;
			}
		}

		// 삭감을 반영한다. 시점은 모르므로 시즌 전체에 균일하게 적용한다 — 최근 폼 구간
		// (이 값이 실제로 쓰이는 곳)에서는 맞고, 삭감 이전 경기에서는 실제와 다를 수 있다.
		deductions.forEach((teamId, points) -> {
			int[] v = tally.get(teamId);
			if (v != null) {
				v[1] -= points;
			}
		});

		// 승점 → 득실차 → 다득점 (프리미어리그 방식). 마지막 teamId 는 순위를 결정론적으로
		// 만들기 위한 것이지 규칙이 아니다 — 같은 입력에 같은 표가 나와야 테스트가 성립한다.
		List<Map.Entry<Long, int[]>> sorted = new ArrayList<>(tally.entrySet());
		sorted.sort(Comparator
				.<Map.Entry<Long, int[]>>comparingInt(e -> -e.getValue()[1])
				.thenComparingInt(e -> -(e.getValue()[2] - e.getValue()[3]))
				.thenComparingInt(e -> -e.getValue()[2])
				.thenComparingLong(Map.Entry::getKey));

		List<Row> rows = new ArrayList<>(sorted.size());
		Map<Long, Row> byTeam = new HashMap<>();
		for (int i = 0; i < sorted.size(); i++) {
			var e = sorted.get(i);
			int[] v = e.getValue();
			Row row = new Row(e.getKey(), i + 1, v[0], v[1], v[2], v[3]);
			rows.add(row);
			byTeam.put(e.getKey(), row);
		}
		return new Snapshot(List.copyOf(rows), Map.copyOf(byTeam));
	}

	/**
	 * 한 팀이 {@code played} 경기를 치른 시점까지의 <b>순수 계산 승점</b>(삭감 미반영).
	 *
	 * <p>순위표가 준 승점과 견주려고 쓴다 — 차이가 곧 삭감분이다. 경기 수를 맞춰야
	 * 비교가 성립한다: 순위표가 38경기 기준이면 우리도 38경기까지만 세야 한다.
	 */
	public static int pointsAfter(List<Fixture> fixtures, long teamId, int played) {
		int counted = 0;
		int points = 0;
		for (Fixture f : fixtures) {
			if (counted >= played) {
				break;
			}
			if (!SchedulePolicy.countsForForm(f)) {
				continue;
			}
			boolean home = f.getHomeTeam().getId() != null && f.getHomeTeam().getId() == teamId;
			boolean away = f.getAwayTeam().getId() != null && f.getAwayTeam().getId() == teamId;
			if (!home && !away) {
				continue;
			}
			counted++;
			int our = home ? f.getGoalsHome() : f.getGoalsAway();
			int theirs = home ? f.getGoalsAway() : f.getGoalsHome();
			if (our > theirs) {
				points += POINTS_FOR_WIN;
			} else if (our == theirs) {
				points += POINTS_FOR_DRAW;
			}
		}
		return points;
	}
}
