/**
 * FixtureResponse[] (API 원형) → Timeline 뷰모델.
 *
 * 이 함수가 원형↔화면의 경계다. 여기서 한 번:
 *   ① 날짜순 정렬  ② 대회·결과·스코어·홈원정 파생  ③ 앞 경기와의 간격(px 포함)
 *   ④ 밀집 구간 탐지·병합  ⑤ 각 경기에 밀집 소속/위치 부여  ⑥ 강조 태그  ⑦ 최근 폼
 * 을 계산한다. 컴포넌트는 결과만 그린다.
 */
import type { FixtureResponse } from "@/lib/api/types";
import {
  classifyCompetition,
  opponentName,
  resolveHomeAway,
  resolveNote,
  resolveResult,
  resolveScore,
  toDateLabel,
  toDow,
} from "@/lib/timeline/format";
import {
  DEFAULT_CONGESTION,
  detectCongestion,
  type CongestionConfig,
} from "@/lib/timeline/congestion";
import { gapLabel, gapToPx } from "@/lib/timeline/gapScale";
import type {
  Competition,
  CongestionSpan,
  HighlightTag,
  Timeline,
  TimelineRow,
} from "@/lib/timeline/types";

const MS_PER_DAY = 86_400_000;
const FORM_LEN = 6;

export interface BuildOptions {
  ourTeamId: number;
  koMap: Record<string, string>;
  congestion?: CongestionConfig;
}

export function buildTimeline(
  fixtures: FixtureResponse[],
  opts: BuildOptions,
): Timeline {
  const { ourTeamId, koMap, congestion = DEFAULT_CONGESTION } = opts;

  // ① 정렬 — 이후 모든 인덱스 기반 계산(간격·밀집)의 단일 전제.
  const sorted = [...fixtures].sort(
    (a, b) => a.fixture.timestamp - b.fixture.timestamp,
  );
  const timestampsMs = sorted.map((f) => f.fixture.timestamp * 1000);

  // ④ 밀집 구간 탐지·병합 → 인덱스별 소속/위치 룩업 구성.
  const spansRaw = detectCongestion(timestampsMs, congestion);
  const spans: CongestionSpan[] = spansRaw.map((s, id) => {
    const slice = sorted.slice(s.startIdx, s.endIdx + 1);
    const spanDays = Math.round(
      (timestampsMs[s.endIdx] - timestampsMs[s.startIdx]) / MS_PER_DAY,
    );
    const awayCount = slice.filter(
      (f) => resolveHomeAway(f, ourTeamId) !== "H",
    ).length;
    const extraTimeCount = slice.filter(
      (f) =>
        f.fixture.status.short === "AET" || f.fixture.status.short === "PEN",
    ).length;
    const parts = [
      `${spanDays}일 ${slice.length}경기`,
      awayCount > 0 ? `원정 ${awayCount}` : null,
      extraTimeCount > 0 ? `연장 ${extraTimeCount}` : null,
    ].filter(Boolean);
    return {
      id,
      startIdx: s.startIdx,
      endIdx: s.endIdx,
      fromLabel: toDateLabel(sorted[s.startIdx].fixture.date),
      toLabel: toDateLabel(sorted[s.endIdx].fixture.date),
      spanDays,
      matchCount: slice.length,
      awayCount,
      extraTimeCount,
      summary: parts.join(" · "),
    };
  });

  // 인덱스 → 밀집 소속 룩업.
  const spanOf = new Map<number, { spanId: number; startIdx: number; endIdx: number }>();
  for (const s of spans) {
    for (let i = s.startIdx; i <= s.endIdx; i++) {
      spanOf.set(i, { spanId: s.id, startIdx: s.startIdx, endIdx: s.endIdx });
    }
  }

  // 최근 폼 인덱스 집합(마지막 FORM_LEN 경기).
  const formStart = Math.max(0, sorted.length - FORM_LEN);

  // ②③⑤⑥ 각 경기를 뷰모델 행으로.
  const rows: TimelineRow[] = sorted.map((fx, i) => {
    const competition = classifyCompetition(fx.league);
    const result = resolveResult(fx, ourTeamId);
    const homeAway = resolveHomeAway(fx, ourTeamId);

    // ③ 앞 경기와의 간격.
    let gap: TimelineRow["gap"] = null;
    if (i > 0) {
      const days = Math.round(
        (timestampsMs[i] - timestampsMs[i - 1]) / MS_PER_DAY,
      );
      gap = {
        days,
        px: gapToPx(days),
        shortRest: days > 0 && days <= 4,
        label: gapLabel(days),
      };
    }

    // ⑤ 밀집 소속/위치.
    const membership = spanOf.get(i);
    const rowCongestion = membership
      ? {
          spanId: membership.spanId,
          pos:
            i === membership.startIdx
              ? ("start" as const)
              : i === membership.endIdx
                ? ("end" as const)
                : ("mid" as const),
        }
      : null;

    // ⑥ 강조 태그.
    const tags: HighlightTag[] = [];
    if (rowCongestion) tags.push("congestion");
    if (i >= formStart) tags.push("form");
    if (competition.key === "europe") tags.push("europe");

    return {
      id: fx.fixture.id,
      date: fx.fixture.date,
      dateLabel: toDateLabel(fx.fixture.date),
      dow: toDow(fx.fixture.date),
      competition,
      homeAway,
      opponent: opponentName(fx, ourTeamId, koMap),
      score: resolveScore(fx, ourTeamId),
      result,
      note: resolveNote(fx),
      gap,
      congestion: rowCongestion,
      tags,
    };
  });

  // 범례: 실제 등장 대회만, league→cup→europe 순.
  const order: Competition["key"][] = ["league", "cup", "europe"];
  const seen = new Map<string, Competition>();
  for (const r of rows) if (!seen.has(r.competition.label)) seen.set(r.competition.label, r.competition);
  const competitionsPresent = [...seen.values()].sort(
    (a, b) => order.indexOf(a.key) - order.indexOf(b.key),
  );

  // ⑦ 최근 폼(날짜 오름차순).
  const form = rows.slice(formStart).map((r) => r.result);

  return { rows, spans, competitionsPresent, form };
}
