/**
 * `TeamDiagnostics`(백엔드 응답) → `Timeline`(뷰모델).
 *
 * 이 함수가 응답↔화면의 경계다. 하는 일은 **표기 변환뿐**이다:
 *   ① 날짜 문자열  ② 간격 → 여백 픽셀  ③ 밀집 구간 경계(경기 id) → 줄 위치
 *   ④ 강조 태그  ⑤ 요약 문구
 *
 * 하지 **않는** 일: 정렬, 승패 판정, 밀집 구간 탐지, 폼 집계. 전부 서버가 끝냈다.
 * (예전 버전은 여기서 슬라이딩 윈도우로 밀집 구간을 직접 찾았다. 같은 규칙이 Java와
 *  TS 양쪽에 있으면 한쪽만 고치는 날이 오고, 그날부터 타임라인과 AI 진단이 서로 다른
 *  답을 말한다. 그래서 계산을 한쪽으로 몰고 이 파일은 그리기만 하도록 비웠다.)
 *
 * ## 구간 경계를 인덱스가 아니라 경기 id로 받는 이유
 * 서버는 연기·취소된 경기를 빼고 세는데 화면은 그것까지 그릴 수 있어, 두 목록의
 * "몇 번째"가 어긋난다. id는 그런 전제 없이 늘 같은 경기를 가리키므로, 서버는 id로
 * 주고 화면이 자기 목록에서 위치를 다시 찾는다 — 그 매핑이 아래 `positionOfSpan`이다.
 */
import type { CongestionSpanView, TeamDiagnostics } from "@/lib/api/types";
import { buildDiagnosis } from "@/lib/diagnosis/summarize";
import {
  CONFIDENCE_LABEL,
  toCompetition,
  toDateLabel,
  toDow,
  toFormSummary,
  toMonthLabel,
  toRecordLabel,
  toScore,
  toShootoutNote,
  toSpanSummary,
  toStatusNote,
  toTeamCode,
} from "@/lib/timeline/format";
import { gapLabel, gapToPx } from "@/lib/timeline/gapScale";
import type {
  Competition,
  CongestionSpan,
  CongestionStatus,
  HighlightTag,
  RowCongestion,
  Absences,
  Timeline,
  TimelineRow,
  Travel,
} from "@/lib/timeline/types";

export function buildTimeline(d: TeamDiagnostics): Timeline {
  const spanViews = d.congestion.spans;

  // ③ 구간 경계(경기 id) → 이 목록에서의 줄 번호. 못 찾으면 그 구간은 그리지 않는다.
  const bounds = spanViews
    .map((s) => positionOfSpan(s, d.matches))
    .filter((b): b is SpanBounds => b !== null);

  const spanByRowIndex = new Map<number, RowCongestion>();
  for (const b of bounds) {
    for (let i = b.startIdx; i <= b.endIdx; i++) {
      spanByRowIndex.set(i, {
        spanId: b.spanId,
        pos: i === b.startIdx ? "start" : i === b.endIdx ? "end" : "mid",
      });
    }
  }

  const spans: CongestionSpan[] = spanViews
    .filter((s) => bounds.some((b) => b.spanId === s.id))
    .map((s) => ({
      id: s.id,
      startFixtureId: s.startFixtureId,
      endFixtureId: s.endFixtureId,
      fromLabel: toDateLabel(s.from),
      toLabel: toDateLabel(s.to),
      spanDays: s.spanDays,
      matchCount: s.matchCount,
      awayCount: s.awayCount,
      extraTimeCount: s.extraTimeMatchCount,
      summary: toSpanSummary(s),
    }));

  // 폼 강조 대상 = 마지막 N개의 **확정된** 경기. 예정 경기는 폼이 아니다.
  const formFixtureIds = recentResolvedIds(d.matches, d.form.sampleSize);

  const rows: TimelineRow[] = d.matches.map((m, i) => {
    const competition = toCompetition(m);
    const rowCongestion = spanByRowIndex.get(i) ?? null;

    const tags: HighlightTag[] = [];
    if (rowCongestion) tags.push("congestion");
    if (formFixtureIds.has(m.fixtureId)) tags.push("form");
    if (competition.key === "europe" || competition.key === "cup") tags.push("europe");
    if (!m.home) tags.push("travel");

    return {
      id: m.fixtureId,
      date: m.kickoff,
      dateLabel: toDateLabel(m.kickoff),
      dow: toDow(m.kickoff),
      competition,
      homeAway: m.home ? "H" : "A",
      opponent: m.opponentName,
      score: toScore(m),
      result: m.result,
      pending: m.result === null,
      statusNote: toStatusNote(m.status),
      shootoutNote: toShootoutNote(m),
      gap:
        m.gapDays == null
          ? null
          : {
              days: m.gapDays,
              px: gapToPx(m.gapDays),
              shortRest: m.gapDays > 0 && m.gapDays <= 4,
              label: gapLabel(m.gapDays),
            },
      congestion: rowCongestion,
      absentCount: m.absentCount,
      tags,
    };
  });

  // 범례: 실제 등장한 대회만, league → cup → europe 순.
  const order: Competition["key"][] = ["league", "cup", "europe"];
  const seen = new Map<string, Competition>();
  for (const r of rows) {
    if (!seen.has(r.competition.label)) seen.set(r.competition.label, r.competition);
  }
  const competitionsPresent = [...seen.values()].sort(
    (a, b) => order.indexOf(a.key) - order.indexOf(b.key),
  );

  const timeline: Timeline = {
    team: {
      id: d.teamId,
      name: d.teamName,
      code: toTeamCode(d.teamName, d.teamCode),
      subtitle: teamSubtitle(d),
    },
    rows,
    spans,
    competitionsPresent,
    congestion: congestionStatus(d),
    travel: travelSummary(d),
    absences: absenceSummary(d),
    form: {
      recent: d.form.recent,
      sampleSize: d.form.sampleSize,
      requested: d.form.requested,
      confidence: d.form.confidence,
      wins: d.form.wins,
      draws: d.form.draws,
      losses: d.form.losses,
      points: d.form.points,
      maxPoints: d.form.maxPoints,
      recordLabel: toRecordLabel(d.form),
      summary: toFormSummary(d.form),
      pointsRate: d.form.pointsRate,
    },
    // 진단은 위에서 만든 뷰모델을 그대로 읽는다 — 화면과 진단이 같은 값을 보게.
    diagnosis: EMPTY_DIAGNOSIS,
    omissions: d.omissions,
    upcomingCount: d.matches.filter((m) => m.status === "NS").length,
    excludedCount: d.window.excludedFixtures,
  };

  return { ...timeline, diagnosis: buildDiagnosis(timeline) };
}

/** `buildDiagnosis`가 완성된 뷰모델을 입력으로 받으므로 자리만 먼저 채운다. */
const EMPTY_DIAGNOSIS: Timeline["diagnosis"] = {
  headline: "",
  sub: "",
  authored: "rule",
  cards: [],
  unknowns: [],
};

// --- 여기부터는 위에서 쓰는 작은 조각들 -------------------------------------

interface SpanBounds {
  spanId: number;
  startIdx: number;
  endIdx: number;
}

/**
 * 구간의 시작·끝 경기 id를 이 목록에서의 줄 번호로 옮긴다.
 *
 * 둘 중 하나라도 목록에 없으면 `null`을 돌려주고, 호출부는 그 구간을 그리지 않는다.
 * 어중간하게 한쪽만 맞춰 그리면 브래킷이 엉뚱한 경기에서 시작하는데, 그건 없는 것보다
 * 나쁘다 — 사용자는 화면에 그려진 구간을 사실로 읽기 때문이다.
 */
function positionOfSpan(
  span: CongestionSpanView,
  matches: { fixtureId: number }[],
): SpanBounds | null {
  const startIdx = matches.findIndex((m) => m.fixtureId === span.startFixtureId);
  const endIdx = matches.findIndex((m) => m.fixtureId === span.endFixtureId);
  if (startIdx < 0 || endIdx < 0 || endIdx < startIdx) {
    return null;
  }
  return { spanId: span.id, startIdx, endIdx };
}

/** 폼에 들어간 경기(= 마지막 N개의 확정 경기)의 id 집합. */
function recentResolvedIds(
  matches: { fixtureId: number; result: unknown }[],
  sampleSize: number,
): Set<number> {
  const resolved = matches.filter((m) => m.result !== null);
  return new Set(resolved.slice(-sampleSize).map((m) => m.fixtureId));
}

/**
 * 밀집 판정의 상태 문구.
 *
 * 구간이 0개일 때 그냥 아무것도 안 그리면 "일정이 여유로웠다"와 "판정할 만큼 경기가
 * 없었다"가 화면에서 똑같아 보인다. 둘은 완전히 다른 말이라 갈라서 적는다.
 */
function congestionStatus(d: TeamDiagnostics): CongestionStatus {
  const c = d.congestion;
  const note = !c.detectable
    ? `경기 ${c.analyzedMatchCount}건 — ${c.minMatches}경기는 있어야 ${c.windowDays}일 밀집을 판정할 수 있습니다`
    : c.spans.length === 0
      ? `밀집 구간 없음 — 가장 빡빡했던 ${c.windowDays}일에 ${c.busiestWindowMatchCount}경기 (기준 ${c.minMatches})`
      : `기준 ${c.windowDays}일 ${c.minMatches}경기 · 밀집 구간 ${c.spans.length}개`;

  return {
    detectable: c.detectable,
    windowDays: c.windowDays,
    minMatches: c.minMatches,
    analyzedMatchCount: c.analyzedMatchCount,
    busiestWindowMatchCount: c.busiestWindowMatchCount,
    shortestGapDays: c.shortestGapDays,
    medianGapDays: c.medianGapDays,
    note,
  };
}

/**
 * 이동거리 요약. 합계를 그냥 "총 이동 1,200km"로 적지 않는다 — 좌표를 모르는 경기가
 * 섞여 있으면 그건 부분합이고, 그걸 총합으로 읽으면 "이 팀은 별로 안 움직였다"는
 * 반대 결론이 나온다. 몇 경기를 실제로 쟀는지를 문장 안에 넣어 둔다.
 */
function travelSummary(d: TeamDiagnostics): Travel {
  const t = d.travel;
  const summary =
    t.awayMatches === 0
      ? "원정 경기 없음"
      : t.measuredMatches === 0
        ? `원정 ${t.awayMatches}경기 — 경기장 좌표가 없어 재지 못함`
        : [
            `원정 ${t.awayMatches}경기 중 ${t.measuredMatches}경기 측정`,
            `${Math.round(t.totalKm ?? 0).toLocaleString("ko-KR")}km`,
            t.unknownCoordinateMatches > 0
              ? `좌표 없음 ${t.unknownCoordinateMatches}경기(부분합)`
              : null,
          ]
            .filter(Boolean)
            .join(" · ");

  return {
    awayMatches: t.awayMatches,
    measuredMatches: t.measuredMatches,
    unknownCoordinateMatches: t.unknownCoordinateMatches,
    totalKm: t.totalKm,
    summary,
  };
}

/**
 * 결장 요약.
 *
 * 평균의 분모를 전체 경기 수가 아니라 **데이터가 있는 경기 수**로 잡는다. 52경기 중
 * 44경기만 데이터가 있는데 52로 나누면 "경기당 5.6명"이 되고, 실제(6.6명)보다 적어
 * 보인다. 데이터가 없는 8경기를 "0명"으로 세는 셈이기 때문이다.
 */
function absenceSummary(d: TeamDiagnostics): Absences {
  const a = d.absences;
  const avg =
    a.covered && a.coveredMatches > 0 ? a.totalOut / a.coveredMatches : null;

  const summary = !a.covered
    ? "결장 데이터 없음"
    : [
        `${a.coveredMatches}/${a.analyzedMatches}경기 확인`,
        `확정 결장 ${a.totalOut}명`,
        avg != null ? `경기당 ${avg.toFixed(1)}명` : null,
      ]
        .filter(Boolean)
        .join(" · ");

  return {
    covered: a.covered,
    coveredMatches: a.coveredMatches,
    analyzedMatches: a.analyzedMatches,
    totalOut: a.totalOut,
    distinctPlayers: a.distinctPlayers,
    maxOutInOneMatch: a.maxOutInOneMatch,
    averagePerCoveredMatch: avg == null ? null : Math.round(avg * 10) / 10,
    byReason: a.byReason,
    topAbsentees: a.topAbsentees,
    summary,
  };
}

/** "전 대회 통합 · 2024/08–2024/09 · 3경기". 없는 값은 조용히 빠진다. */
function teamSubtitle(d: TeamDiagnostics): string {
  const { from, to, analyzedFixtures } = d.window;
  const period =
    from && to
      ? toMonthLabel(from) === toMonthLabel(to)
        ? toMonthLabel(from)
        : `${toMonthLabel(from)}–${toMonthLabel(to)}`
      : null;

  return [
    "전 대회 통합",
    period,
    analyzedFixtures > 0 ? `${analyzedFixtures}경기` : "경기 없음",
    d.form.sampleSize > 0 ? CONFIDENCE_LABEL[d.form.confidence] : null,
  ]
    .filter(Boolean)
    .join(" · ");
}
