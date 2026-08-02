package page.usetaehwan.gak.service.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import page.usetaehwan.gak.service.analysis.CongestionDetector.IndexSpan;

/**
 * 밀집 탐지·병합 단위 테스트. 엔티티도 DB도 없이 날짜 숫자만 넣는다 —
 * 알고리즘이 순수 함수라서 가능한 일이고, 그래서 반례를 몇 줄로 추가할 수 있다.
 */
class CongestionDetectorTest {

	private static final int WINDOW = 14;
	private static final int MIN = 5;

	/** "8/1부터 며칠째"를 에포크 일수로. 값 자체는 중요하지 않고 차이만 중요하다. */
	private static long[] days(long... offsets) {
		return offsets;
	}

	@Nested
	@DisplayName("탐지")
	class Detect {

		@Test
		@DisplayName("14일 안에 5경기면 밀집 구간이 된다")
		void detectsFiveInFourteenDays() {
			// 0,3,6,9,12일 — 폭 12일에 5경기
			List<IndexSpan> spans = CongestionDetector.detect(days(0, 3, 6, 9, 12), WINDOW, MIN);

			assertThat(spans).containsExactly(new IndexSpan(0, 4));
		}

		@Test
		@DisplayName("경기 수는 같아도 창 폭을 넘기면 밀집이 아니다")
		void ignoresFiveMatchesSpreadWiderThanWindow() {
			// 0,4,8,12,16일 — 5경기지만 폭이 16일이라 어떤 14일 창에도 5경기가 들어가지 않는다
			List<IndexSpan> spans = CongestionDetector.detect(days(0, 4, 8, 12, 16), WINDOW, MIN);

			assertThat(spans).isEmpty();
		}

		@Test
		@DisplayName("창 폭은 양 끝을 포함한다 — 정확히 14일 차이는 같은 창")
		void windowIsInclusiveAtBothEnds() {
			assertThat(CongestionDetector.detect(days(0, 1, 2, 3, 14), WINDOW, MIN))
					.containsExactly(new IndexSpan(0, 4));
			assertThat(CongestionDetector.detect(days(0, 1, 2, 3, 15), WINDOW, MIN))
					.isEmpty();
		}

		@Test
		@DisplayName("같은 날 두 경기(간격 0)도 정상 처리한다")
		void handlesSameDayFixtures() {
			List<IndexSpan> spans = CongestionDetector.detect(days(0, 0, 1, 2, 3), WINDOW, MIN);

			assertThat(spans).containsExactly(new IndexSpan(0, 4));
		}

		@Test
		@DisplayName("표본이 기준보다 적으면 빈 결과 — 예외가 아니다")
		void returnsEmptyWhenSampleIsSmallerThanThreshold() {
			assertThat(CongestionDetector.detect(days(0, 3, 6), WINDOW, MIN)).isEmpty();
			assertThat(CongestionDetector.detect(days(), WINDOW, MIN)).isEmpty();
		}

		@Test
		@DisplayName("연속한 밀집 창들은 하나의 긴 구간으로 합쳐진다")
		void mergesConsecutiveWindowsIntoOneSpan() {
			// 0..7일에 8경기: right가 4,5,6,7일 때마다 후보가 나오고 전부 겹친다
			List<IndexSpan> spans = CongestionDetector.detect(days(0, 1, 2, 3, 4, 5, 6, 7), WINDOW, MIN);

			assertThat(spans).containsExactly(new IndexSpan(0, 7));
		}

		@Test
		@DisplayName("긴 공백을 사이에 둔 두 버스트는 따로 남는다 — 맞닿음은 병합하지 않는 이유")
		void keepsBurstsSeparatedByABreakApart() {
			// 앞 버스트 0~8일 5경기, 40일 공백, 뒤 버스트 48~56일 5경기
			List<IndexSpan> spans = CongestionDetector.detect(
					days(0, 2, 4, 6, 8, 48, 50, 52, 54, 56), WINDOW, MIN);

			assertThat(spans).containsExactly(new IndexSpan(0, 4), new IndexSpan(5, 9));
		}

		@Test
		@DisplayName("정렬돼 있지 않으면 조용히 틀린 답 대신 예외를 던진다")
		void rejectsUnsortedInput() {
			assertThatThrownBy(() -> CongestionDetector.detect(days(0, 5, 3), WINDOW, MIN))
					.isInstanceOf(IllegalArgumentException.class)
					.hasMessageContaining("오름차순");
		}

		@Test
		@DisplayName("기준값이 비정상이면 예외")
		void rejectsInvalidThresholds() {
			assertThatThrownBy(() -> CongestionDetector.detect(days(0, 1), 0, MIN))
					.isInstanceOf(IllegalArgumentException.class);
			assertThatThrownBy(() -> CongestionDetector.detect(days(0, 1), WINDOW, 1))
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Nested
	@DisplayName("구간 병합")
	class Merge {

		@Test
		@DisplayName("겹치는 구간은 최대 구간으로 합친다")
		void mergesOverlapping() {
			List<IndexSpan> merged = CongestionDetector.mergeIntervals(
					List.of(new IndexSpan(0, 4), new IndexSpan(2, 6), new IndexSpan(5, 9)));

			assertThat(merged).containsExactly(new IndexSpan(0, 9));
		}

		@Test
		@DisplayName("맞닿기만 한 구간(끝+1에서 시작)은 합치지 않는다")
		void doesNotMergeMerelyAdjacent() {
			List<IndexSpan> merged = CongestionDetector.mergeIntervals(
					List.of(new IndexSpan(0, 4), new IndexSpan(5, 9)));

			assertThat(merged).containsExactly(new IndexSpan(0, 4), new IndexSpan(5, 9));
		}

		@Test
		@DisplayName("앞 구간이 뒤 구간을 통째로 품어도 결과는 앞 구간 하나")
		void handlesFullyContainedSpan() {
			List<IndexSpan> merged = CongestionDetector.mergeIntervals(
					List.of(new IndexSpan(0, 9), new IndexSpan(3, 5)));

			assertThat(merged).containsExactly(new IndexSpan(0, 9));
		}

		@Test
		@DisplayName("입력이 시작 순이 아니어도 정렬 후 병합한다")
		void sortsBeforeSweeping() {
			List<IndexSpan> merged = CongestionDetector.mergeIntervals(
					List.of(new IndexSpan(5, 9), new IndexSpan(0, 6)));

			assertThat(merged).containsExactly(new IndexSpan(0, 9));
		}

		@Test
		@DisplayName("빈 입력은 빈 결과")
		void emptyInput() {
			assertThat(CongestionDetector.mergeIntervals(List.of())).isEmpty();
			assertThat(CongestionDetector.mergeIntervals(null)).isEmpty();
		}
	}

	@Nested
	@DisplayName("가장 빡빡했던 창")
	class Busiest {

		@Test
		@DisplayName("기준에 못 미쳐도 최대 경기 수는 알려 준다")
		void reportsPeakEvenBelowThreshold() {
			// 밀집 구간은 안 나오지만(4경기 < 5), "14일에 4경기였다"는 말할 수 있어야 한다
			long[] input = days(0, 2, 4, 6, 40);
			assertThat(CongestionDetector.detect(input, WINDOW, MIN)).isEmpty();
			assertThat(CongestionDetector.busiestWindowMatchCount(input, WINDOW)).isEqualTo(4);
		}

		@Test
		@DisplayName("경기가 없으면 0")
		void zeroForEmptySchedule() {
			assertThat(CongestionDetector.busiestWindowMatchCount(days(), WINDOW)).isZero();
		}
	}
}
