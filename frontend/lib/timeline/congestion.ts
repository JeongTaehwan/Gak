/**
 * 일정 밀집 구간(fixture congestion) 탐지 + 병합.
 *
 * 규칙: "임의의 W일(기본 14일) 창 안에 K경기(기본 5) 이상"이면 그 창의 경기들은
 * 밀집이다. 축구에서 흔히 쓰는 "14일 5경기" 과부하 기준을 그대로 코드화했다.
 *
 * 알고리즘 — 슬라이딩 윈도우 O(n):
 *   경기는 이미 날짜 오름차순. 두 포인터 left/right 로 "폭 ≤ W일" 창을 유지한다.
 *   right 를 한 칸 전진할 때마다, 창의 좌측이 W일을 넘으면 left 를 필요한 만큼 당긴다.
 *   left 는 되돌아가지 않으므로 left·right 각각 최대 n번 이동 → 전체 O(n).
 *   (나이브하게 매 경기마다 앞뒤를 다시 세면 매번 최대 n번 비교 → O(n²).)
 *   창 안 경기 수(right-left+1)가 K 이상이면 그 인덱스 구간 [left,right] 을 후보로 담는다.
 *
 * 후보 구간들은 서로 겹친다(연속한 right 마다 한 창씩 나오므로). 이를 "구간 병합
 * 문제"로 보고 최대 구간으로 합친다 → mergeIntervals. left 가 단조 증가라 후보는
 * 이미 시작 인덱스 오름차순이므로, 한 번 훑으며 끝을 늘리는 스윕이면 충분하다.
 *
 * ⚠️ 병합 조건은 "실제 인덱스 겹침"이어야 한다(맞닿음이 아니라). 두 밀집 창이
 *   인덱스로 딱 붙어만 있고(앞 창 끝=i, 뒤 창 시작=i+1) 겹치지 않는다면, 그 사이
 *   전이(경기 i → i+1)는 밀집이 아니라는 뜻 — 실제로 그 사이에 인터내셔널 브레이크
 *   같은 긴 공백이 있다. 이때는 별개의 밀집 구간으로 남겨야 한다. 그래서 병합은
 *   startIdx <= endIdx (겹침) 일 때만 한다. (+1 로 하면 브레이크를 사이에 둔 두 버스트가
 *   가을 내내 하나로 붙어버린다.)
 */

export interface IndexSpan {
  startIdx: number;
  endIdx: number;
}

export interface CongestionConfig {
  windowDays: number;
  minMatches: number;
}

export const DEFAULT_CONGESTION: CongestionConfig = {
  windowDays: 14,
  minMatches: 5,
};

const MS_PER_DAY = 86_400_000;

/**
 * @param timestampsMs 경기 킥오프(ms), 날짜 오름차순 정렬 전제.
 * @returns 병합된 밀집 인덱스 구간들(경기 배열의 인덱스 기준).
 */
export function detectCongestion(
  timestampsMs: number[],
  config: CongestionConfig = DEFAULT_CONGESTION,
): IndexSpan[] {
  const { windowDays, minMatches } = config;
  const n = timestampsMs.length;
  const raw: IndexSpan[] = [];

  let left = 0;
  for (let right = 0; right < n; right++) {
    // 창 폭이 windowDays 를 넘으면 좌측을 당겨 창을 유지 (left 는 전진만).
    while (
      (timestampsMs[right] - timestampsMs[left]) / MS_PER_DAY >
      windowDays
    ) {
      left++;
    }
    if (right - left + 1 >= minMatches) {
      raw.push({ startIdx: left, endIdx: right });
    }
  }

  return mergeIntervals(raw);
}

/**
 * 겹치거나 맞닿은 인덱스 구간들을 최대 구간으로 병합.
 * 입력이 시작 인덱스 오름차순이라는 전제(슬라이딩 윈도우 출력) 하에 한 번의 스윕.
 * 방어적으로 정렬도 한 번 해 둔다(다른 곳에서 재사용될 때 안전).
 */
export function mergeIntervals(spans: IndexSpan[]): IndexSpan[] {
  if (spans.length === 0) return [];
  const sorted = [...spans].sort((a, b) => a.startIdx - b.startIdx);
  const merged: IndexSpan[] = [{ ...sorted[0] }];

  for (let i = 1; i < sorted.length; i++) {
    const cur = sorted[i];
    const last = merged[merged.length - 1];
    // 겹칠 때만 병합(맞닿음 제외). 위 주석의 이유 참고.
    if (cur.startIdx <= last.endIdx) {
      last.endIdx = Math.max(last.endIdx, cur.endIdx);
    } else {
      merged.push({ ...cur });
    }
  }
  return merged;
}
