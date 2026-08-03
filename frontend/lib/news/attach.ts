/**
 * 소식을 타임라인 위 시점에 얹는다.
 *
 * ⚠️ **여기서 하는 일은 배치뿐이다. 인과를 말하지 않는다.**
 *
 * "이 경기 직전에 이 기사가 있었다"는 시각 배치이지 "그래서 졌다"가 아니다. 둘은
 * 화면에서 아주 가까워 보이므로, 이 층이 지켜야 할 선을 코드에 적어 둔다 —
 * 소식은 절대 진단 문장·근거 카드·AI 프롬프트에 들어가지 않는다. 타임라인 옆에
 * 나란히 놓일 뿐이다.
 *
 * 프로젝트 규칙("계산은 백엔드에만")과도 어긋나지 않는다. 여기엔 판정이 없다 —
 * 어느 시각 구간에 속하는지를 나누는 표기 로직이다.
 */
import type { NewsCategory, NewsCoverage, NewsItem } from "@/lib/api/types";
import type { Timeline } from "@/lib/timeline/types";

/**
 * 소식이 안 보이는 이유. **넷을 구분해야 빈 화면이 버그로 안 보인다.**
 *
 * - `ok`               — 겹치는 소식이 있다
 * - `not-collected`    — 아직 하나도 수집되지 않았다
 * - `season-mismatch`  — 수집은 됐는데 **보고 있는 시즌과 시점이 다르다**
 * - `outside-retention`— 타임라인이 보관 기간보다 오래됐다
 * - `quiet`            — 겹치는데 그 구간에 소식이 없었다
 */
export type NewsGapReason =
  | "ok"
  | "not-collected"
  | "season-mismatch"
  | "outside-retention"
  | "quiet";

export interface NewsLayerStatus {
  reason: NewsGapReason;
  /** 화면에 그대로 띄울 한국어 한 줄. */
  headline: string;
  /** 부연 — 왜 그런지, 언제 채워지는지. */
  detail: string;
  /** 우리가 가진 소식 범위(있으면). */
  coverageLabel: string | null;
  /** 지금 타임라인이 덮는 범위. */
  timelineLabel: string | null;
}

export interface AttachedNews {
  status: NewsLayerStatus;
  /**
   * 경기 id → 그 경기 **직전 구간**의 소식들.
   *
   * 앞 경기 킥오프 이후 ~ 이 경기 킥오프까지. 첫 경기는 그 앞 7일까지만 본다
   * (무한정 앞을 끌어오면 시즌 전 기사가 전부 첫 경기에 매달린다).
   */
  byRowId: Map<number, NewsItem[]>;
  /** 마지막 경기 이후의 소식 — 목록 꼬리에 붙는다. */
  trailing: NewsItem[];
  /** 타임라인 구간에 실제로 얹힌 총 건수. */
  placedCount: number;
}

/** 첫 경기 앞으로 얼마나 거슬러 올라가 소식을 끌어올지. */
const LEAD_IN_DAYS = 7;

/**
 * 마지막 경기 **뒤로** 얼마나 소식을 붙일지.
 *
 * ⚠️ 이 상한이 없으면 시간축이 안 겹칠 때 <b>소식 전부가 목록 꼬리에 쌓인다.</b>
 * 실제로 그렇게 만들었다가 잡았다 — 2023-24 타임라인 밑에 2026년 기사 36건이 붙어,
 * 마치 그 시즌 끝에 나온 말인 것처럼 보였다. 시점이 안 맞으면 <b>놓지 않고</b>
 * 왜 못 놓는지를 말하는 편이 맞다.
 */
const TRAIL_OUT_DAYS = 14;

const DAY_MS = 86_400_000;

export function attachNews(
  timeline: Timeline,
  items: NewsItem[],
  coverage: NewsCoverage,
): AttachedNews {
  const byRowId = new Map<number, NewsItem[]>();
  const rows = timeline.rows;

  const timelineFrom = rows.length ? Date.parse(rows[0].date) : null;
  const timelineTo = rows.length ? Date.parse(rows[rows.length - 1].date) : null;

  // 최신순으로 들어오지만 배치는 시간순이 편하다.
  const sorted = [...items].sort(
    (a, b) => Date.parse(a.publishedAt) - Date.parse(b.publishedAt),
  );

  const trailing: NewsItem[] = [];
  let placedCount = 0;

  if (rows.length > 0 && timelineFrom != null && timelineTo != null) {
    let cursor = 0;
    for (let i = 0; i < rows.length; i++) {
      const row = rows[i];
      const rowAt = Date.parse(row.date);
      const windowStart =
        i === 0 ? rowAt - LEAD_IN_DAYS * DAY_MS : Date.parse(rows[i - 1].date);

      const bucket: NewsItem[] = [];
      while (cursor < sorted.length) {
        const at = Date.parse(sorted[cursor].publishedAt);
        if (at < windowStart) {
          cursor++; // 첫 경기보다 한참 앞 — 버린다(끌어오지 않는다)
          continue;
        }
        if (at > rowAt) break;
        bucket.push(sorted[cursor]);
        cursor++;
      }
      if (bucket.length) {
        // 한 구간 안에서는 최신이 위로 — 읽는 방향이 목록과 같아야 한다.
        bucket.reverse();
        byRowId.set(row.id, bucket);
        placedCount += bucket.length;
      }
    }
    // 마지막 경기 이후 — 가까운 것만 붙인다. 멀리 떨어진 건 놓지 않는다(위 주석 참고).
    const trailLimit = timelineTo + TRAIL_OUT_DAYS * DAY_MS;
    for (; cursor < sorted.length; cursor++) {
      if (Date.parse(sorted[cursor].publishedAt) <= trailLimit) {
        trailing.push(sorted[cursor]);
      }
    }
    trailing.reverse();
  }

  return {
    status: describe(coverage, timelineFrom, timelineTo, placedCount + trailing.length),
    byRowId,
    trailing,
    placedCount,
  };
}

/**
 * 왜 안 보이는지를 정한다.
 *
 * **"뉴스 없음"으로 뭉뚱그리지 않는다.** 지금(2026-08) 상태가 정확히
 * `season-mismatch`다 — 타임라인은 2023-24 시즌이고 소식은 2026년이라 겹치는 구간이
 * 없다. 이걸 "소식 없음"이라고만 적으면 사용자는 수집이 고장 났다고 읽는다.
 */
function describe(
  coverage: NewsCoverage,
  timelineFrom: number | null,
  timelineTo: number | null,
  overlapping: number,
): NewsLayerStatus {
  const coverageLabel =
    coverage.from && coverage.to
      ? `${monthLabel(coverage.from)} – ${monthLabel(coverage.to)}`
      : null;
  const timelineLabel =
    timelineFrom != null && timelineTo != null
      ? `${monthLabel(timelineFrom)} – ${monthLabel(timelineTo)}`
      : null;

  const base = { coverageLabel, timelineLabel };

  if (coverage.totalItems === 0) {
    return {
      ...base,
      reason: "not-collected",
      headline: "아직 수집된 소식이 없습니다",
      detail:
        "뉴스 수집은 매시 5분에 돕니다. RSS는 최근 기사만 주므로 지난 소식은 받을 수 없고, 지금부터 쌓입니다.",
    };
  }

  if (overlapping > 0) {
    return { ...base, reason: "ok", headline: "", detail: "" };
  }

  if (timelineFrom == null || timelineTo == null) {
    return {
      ...base,
      reason: "quiet",
      headline: "이 일정에 얹을 소식이 없습니다",
      detail: "경기가 없어 소식을 놓을 자리가 없습니다.",
    };
  }

  const coverageFrom = coverage.from ? Date.parse(coverage.from) : null;
  const coverageTo = coverage.to ? Date.parse(coverage.to) : null;

  // 소식이 타임라인보다 전부 뒤에 있다 = 보고 있는 시즌이 과거다. 지금 상태다.
  if (coverageFrom != null && coverageFrom > timelineTo) {
    return {
      ...base,
      reason: "season-mismatch",
      headline: "지금 보고 있는 시즌과 소식의 시점이 다릅니다",
      detail: `일정은 ${timelineLabel}, 수집된 소식은 ${coverageLabel} 것이라 겹치는 구간이 없습니다. 현재 시즌 일정이 들어오면 그 시점의 소식이 함께 놓입니다.`,
    };
  }

  // 타임라인이 보관 기간보다 오래됐다.
  if (coverageTo != null && timelineTo < coverageTo - coverage.retentionDays * DAY_MS) {
    return {
      ...base,
      reason: "outside-retention",
      headline: `보관 기간(${coverage.retentionDays}일) 밖의 기간입니다`,
      detail: `그때 소식이 없었던 것이 아니라, 우리가 ${coverage.retentionDays}일까지만 보관합니다. 남의 기사를 오래 쌓아 두지 않기 위한 선택입니다.`,
    };
  }

  return {
    ...base,
    reason: "quiet",
    headline: "이 기간에는 수집된 소식이 없습니다",
    detail: `소식은 ${coverageLabel} 구간을 덮고 있지만, 이 일정과 겹치는 시점에는 받은 기사가 없습니다.`,
  };
}

function monthLabel(value: string | number): string {
  const d = new Date(value);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}`;
}

/** 화면 필터가 쓰는 갈래 목록과 한글 라벨. */
export const NEWS_CATEGORIES: { key: NewsCategory; label: string }[] = [
  { key: "TRANSFER", label: "이적" },
  { key: "SQUAD", label: "선수단" },
  { key: "MATCH", label: "경기" },
  { key: "CLUB", label: "구단" },
  { key: "OTHER", label: "기타" },
];

/**
 * 갈래로 거른다.
 *
 * **`null`(태그 없음)은 어떤 필터에도 걸리지 않는다.** 태거가 아직 못 붙인 것을
 * "기타"에 밀어 넣으면, 모델이 기타라고 판단한 것과 아직 안 본 것이 섞인다.
 * 대신 필터를 걸지 않았을 때는 전부 보인다.
 */
export function filterByCategory(
  items: NewsItem[],
  active: NewsCategory | null,
): NewsItem[] {
  if (!active) return items;
  return items.filter((n) => n.category === active);
}

/** "8/3 23:14" — 소식은 시각까지 보여 준다(하루에 여러 건이 쌓이므로). */
export function newsTimeLabel(iso: string): string {
  const d = new Date(iso);
  const date = `${d.getMonth() + 1}/${d.getDate()}`;
  const time = `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
  return `${date} ${time}`;
}
