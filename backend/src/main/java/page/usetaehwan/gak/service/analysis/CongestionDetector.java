package page.usetaehwan.gak.service.analysis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 일정 밀집 구간 탐지 + 병합. <b>순수 계산</b>이다 — 엔티티도 DB도 모르고, 날짜를
 * "에포크 일수(long)" 배열로만 받는다. 그래서 테스트가 숫자 몇 개로 끝난다.
 *
 * <h2>기준</h2>
 * "임의의 {@code windowDays}일 창 안에 {@code minMatches}경기 이상"이면 밀집이다.
 * 기본값 14일 5경기는 축구에서 과부하를 말할 때 쓰는 관용 기준을 그대로 옮긴 것이다.
 * 창의 폭은 <b>양 끝 포함</b>으로 본다 — 8/1과 8/15는 차이가 14일이므로 같은 창이다.
 *
 * <h2>알고리즘 — 슬라이딩 윈도우 O(n)</h2>
 * <p>나이브하게 풀면 경기마다 "이 경기 기준 14일 안에 몇 경기?"를 세느라 매번 배열을
 * 다시 훑는다 → n개 경기 × 최대 n번 비교 = O(n²).
 *
 * <p>대신 두 포인터 {@code left}/{@code right}로 "폭 ≤ windowDays"인 창을 유지한다.
 * {@code right}를 한 칸 전진시킨 뒤, 창의 왼쪽 끝이 너무 멀어졌으면 {@code left}를
 * 필요한 만큼 당긴다. 핵심은 <b>{@code left}가 절대 되돌아가지 않는다</b>는 것이다.
 * 경기가 날짜 오름차순이므로, {@code right}가 커지면 조건을 만족하는 최소 {@code left}도
 * 커지기만 한다. 두 포인터가 각각 최대 n번만 전진하므로 전체 이동 횟수는 2n → O(n).
 *
 * <p>창 안 경기 수({@code right - left + 1})가 {@code minMatches} 이상이면 그 인덱스
 * 구간을 후보로 담는다.
 *
 * <h2>후보 병합</h2>
 * <p>후보는 {@code right}마다 하나씩 나오므로 서로 잔뜩 겹친다. 이를 "구간 병합
 * (merge intervals)" 문제로 보고 최대 구간으로 합친다. {@code left}가 단조 증가라
 * 후보는 이미 시작 인덱스 오름차순 → 한 번의 스윕이면 충분하다.
 *
 * <p><b>병합 조건은 "겹침"이지 "맞닿음"이 아니다.</b> 앞 구간이 인덱스 i에서 끝나고
 * 뒤 구간이 i+1에서 시작하면(겹치지 않고 맞닿기만 하면) 그 사이 전이는 밀집이 아니라는
 * 뜻이다 — 실제로 그 사이엔 인터내셔널 브레이크 같은 긴 공백이 있다. 그래서
 * {@code start <= lastEnd}일 때만 합친다. {@code +1}로 하면 브레이크를 사이에 둔 두
 * 버스트가 가을 내내 하나로 붙어 버린다. (프론트 {@code lib/timeline/congestion.ts}와
 * 같은 규칙 — 두 곳이 다른 답을 내면 안 된다.)
 */
public final class CongestionDetector {

	/** 인덱스 구간(양 끝 포함). 입력 배열의 위치를 가리킨다. */
	public record IndexSpan(int startIdx, int endIdx) {

		public int size() {
			return endIdx - startIdx + 1;
		}
	}

	private CongestionDetector() {
	}

	/**
	 * @param epochDays  경기 킥오프의 에포크 일수. <b>오름차순 정렬 전제</b>
	 * @param windowDays 창 폭(일). 양 끝 포함
	 * @param minMatches 밀집으로 볼 최소 경기 수
	 * @return 병합된 밀집 인덱스 구간들(시작 인덱스 오름차순)
	 * @throws IllegalArgumentException 인자가 비정상이거나 입력이 정렬돼 있지 않을 때
	 */
	public static List<IndexSpan> detect(long[] epochDays, int windowDays, int minMatches) {
		validate(epochDays, windowDays, minMatches);

		List<IndexSpan> candidates = new ArrayList<>();
		int left = 0;
		for (int right = 0; right < epochDays.length; right++) {
			// 창 폭이 넘치면 왼쪽을 당긴다. left는 전진만 한다 — 그래서 전체가 O(n).
			while (epochDays[right] - epochDays[left] > windowDays) {
				left++;
			}
			if (right - left + 1 >= minMatches) {
				candidates.add(new IndexSpan(left, right));
			}
		}
		return mergeIntervals(candidates);
	}

	/**
	 * 겹치는 인덱스 구간들을 최대 구간으로 병합한다.
	 *
	 * <p><b>정렬이 먼저다.</b> 병합 스윕은 "지금 보는 구간의 시작이 직전 구간의 끝보다
	 * 뒤면 둘은 떨어져 있다"를 근거로 앞의 것들을 더 볼 필요가 없다고 판단한다. 시작이
	 * 뒤죽박죽이면 이 판단이 성립하지 않아 방금 닫은 구간에 나중 구간이 다시 겹치는 일이
	 * 생기고, 그걸 막으려면 매번 전체를 다시 훑어야 한다(O(n²)).
	 *
	 * <p>{@link #detect}가 주는 후보는 이미 정렬돼 있지만, 이 메서드는 단독으로도 쓰일 수
	 * 있으므로 방어적으로 한 번 더 정렬한다(O(n log n)).
	 */
	public static List<IndexSpan> mergeIntervals(List<IndexSpan> spans) {
		if (spans == null || spans.isEmpty()) {
			return List.of();
		}
		List<IndexSpan> sorted = new ArrayList<>(spans);
		sorted.sort(Comparator.comparingInt(IndexSpan::startIdx).thenComparingInt(IndexSpan::endIdx));

		List<IndexSpan> merged = new ArrayList<>();
		int start = sorted.get(0).startIdx();
		int end = sorted.get(0).endIdx();
		for (int i = 1; i < sorted.size(); i++) {
			IndexSpan cur = sorted.get(i);
			if (cur.startIdx() <= end) {          // 겹칠 때만 — 맞닿음(end + 1)은 병합하지 않는다
				end = Math.max(end, cur.endIdx());
			} else {
				merged.add(new IndexSpan(start, end));
				start = cur.startIdx();
				end = cur.endIdx();
			}
		}
		merged.add(new IndexSpan(start, end));
		return List.copyOf(merged);
	}

	/**
	 * 가장 빡빡했던 창에 몇 경기가 들어 있었는가(기준 미달이어도 알려 주는 값).
	 *
	 * <p>밀집 구간이 하나도 안 나왔을 때 "기준 근처였는지(14일 4경기) 여유로웠는지
	 * (14일 2경기)"를 구분해 준다. 밀집 구간 목록이 빈 배열이라는 사실만으로는 이
	 * 둘을 구분할 수 없다. 같은 슬라이딩 윈도우를 한 번 더 도는 것이므로 역시 O(n).
	 */
	public static int busiestWindowMatchCount(long[] epochDays, int windowDays) {
		validate(epochDays, windowDays, 2);
		int best = 0;
		int left = 0;
		for (int right = 0; right < epochDays.length; right++) {
			while (epochDays[right] - epochDays[left] > windowDays) {
				left++;
			}
			best = Math.max(best, right - left + 1);
		}
		return best;
	}

	private static void validate(long[] epochDays, int windowDays, int minMatches) {
		if (epochDays == null) {
			throw new IllegalArgumentException("경기 날짜 배열이 null입니다.");
		}
		if (windowDays < 1) {
			throw new IllegalArgumentException("창 폭은 1일 이상이어야 합니다. windowDays=" + windowDays);
		}
		if (minMatches < 2) {
			throw new IllegalArgumentException("밀집 기준은 2경기 이상이어야 합니다. minMatches=" + minMatches);
		}
		// 정렬 전제가 깨지면 창이 음수 폭이 되어 left가 멈추고 결과가 조용히 틀린다.
		// 조용히 틀리느니 시끄럽게 실패하는 쪽이 낫다.
		for (int i = 1; i < epochDays.length; i++) {
			if (epochDays[i] < epochDays[i - 1]) {
				throw new IllegalArgumentException(
						"경기 날짜가 오름차순이 아닙니다. index=" + i);
			}
		}
	}
}
