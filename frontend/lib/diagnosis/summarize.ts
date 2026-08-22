/**
 * 진단 화면이 읽는 값 만들기 — 결론 문장 + 근거 카드 + "모르는 것" 목록.
 *
 * ⚠️ 여기서도 **계산하지 않는다.** 밀집 구간·폼·이동거리는 전부 백엔드가 낸 값이고,
 *    이 파일이 하는 일은 그 값을 사람이 읽는 문장으로 옮기는 것뿐이다.
 *    (`lib/chat/script.ts`와 같은 층위다.)
 *
 * ## 결론 문장이 지금은 규칙 기반인 이유
 * AI 진단(Anthropic) 연동이 아직 없다. 그렇다고 "진단 준비 중"만 띄우면, 이미 다 계산해
 * 둔 숫자를 화면이 안 보여 주는 셈이라 아깝다. 그래서 **같은 숫자를 읽는 규칙 기반
 * 문장**을 먼저 두고, 화면에 그 출처를 밝힌다(`authored: "rule"`).
 * AI가 들어오면 읽는 값은 그대로고 문장을 만드는 주체만 바뀐다 —
 * 그래서 이 파일의 입력이 곧 나중에 AI에게 넘길 프롬프트의 재료다.
 *
 * ## 표본이 작으면 결론을 내지 않는다
 * 3경기로 "일정이 문제다"라고 말하면 그건 진단이 아니라 추측이다. 이 앱은 근거로
 * 말하는 게 존재 이유라, 판정 불가일 때는 **판정 불가라고 결론 낸다.**
 */
import type { AbsenceReason } from "@/lib/api/types";
import type {
  Diagnosis,
  DiagnosisCard,
  Timeline,
  UnknownItem,
} from "@/lib/timeline/types";

/** 백엔드 omission의 metric 코드 → 화면 라벨. */
const OMISSION_LABEL: Record<string, string> = {
  period: "진단 기간",
  congestion: "밀집 판정",
  pointsRate: "승점률",
  opponentStrength: "상대 강도",
  travelDistance: "이동거리",
  absences: "결장 데이터 범위",
};

/**
 * 우리가 **아예 수집하지 않는** 것들. 계산에 실패한 게 아니라 데이터 자체가 없다.
 * 시안이 이 블록을 따로 둔 이유가 있다 — 부진의 원인으로 가장 먼저 떠오르는 것들이
 * 하필 우리가 못 보는 것들이라, 이걸 안 밝히면 진단이 실제보다 완전해 보인다.
 */
const STRUCTURAL_UNKNOWNS: UnknownItem[] = [
  { label: "전술 변화", reason: "포메이션·라인업을 다루지 않습니다", kind: "structural" },
  { label: "라커룸 분위기", reason: "애초에 데이터로 잴 수 없습니다", kind: "structural" },
  {
    label: "선수 개개인의 중요도",
    reason:
      "누가 빠졌는지는 알아도 그 선수가 팀에 얼마나 중요한지는 모릅니다",
    kind: "structural",
  },
  {
    label: "컵 상대의 강함",
    reason:
      "컵에는 순위표가 없어 순위를 매기지 못합니다. 컵에서 강팀을 이겨도 상대 강도에 잡히지 않습니다",
    kind: "structural",
  },
];

/** 결장 갈래 라벨. "부상"으로 뭉뚱그리지 않는다 — 징계·질병이 섞여 있다. */
export const REASON_LABEL: Record<AbsenceReason, string> = {
  INJURY: "부상",
  SUSPENSION: "징계",
  ILLNESS: "질병",
  NATIONAL_DUTY: "대표팀 차출",
  OTHER: "기타",
};

export function buildDiagnosis(t: Timeline): Diagnosis {
  return {
    ...conclusion(t),
    authored: "rule",
    cards: cards(t),
    unknowns: [
      ...t.omissions.map((o) => ({
        label: OMISSION_LABEL[o.metric] ?? o.metric,
        reason: o.reason,
        kind: "omitted" as const,
      })),
      // 결장 데이터를 아직 안 받았다면 그것도 "모르는 것"이다. 받았다면 이미 omissions
      // 쪽에서 커버 범위를 말하므로 여기 또 적지 않는다.
      ...(t.absences.covered
        ? []
        : [
            {
              label: "결장(부상·징계)",
              reason:
                "이 팀의 결장 데이터를 아직 동기화하지 않았습니다. POST /api/admin/sync/injuries/{teamId}?season=",
              kind: "omitted" as const,
            },
          ]),
      ...STRUCTURAL_UNKNOWNS,
    ],
  };
}

/**
 * 한 줄 결론. 순서가 곧 우선순위다 — 표본 부족 → 밀집 → 폼 → 무난.
 * 앞의 조건이 걸리면 뒤는 말하지 않는다. 판정할 수 없는 상태에서 폼을 논하면
 * "3경기 중 2패라 부진"처럼 표본이 빈약한 단정이 나온다.
 */
function conclusion(t: Timeline): { headline: string; sub: string } {
  const { congestion: c, form: f, spans, period: p } = t;

  // 결론 문장은 **기간을 먼저 말한다.** 같은 "3승 4패"라도 어느 시즌의 몇 경기인지에
  // 따라 다른 말이고, 시즌이 진행 중이면 아직 끝나지 않은 이야기다.
  const scope = p.seasonLabel ? `${p.seasonLabel} ${p.analyzedMatches}경기 기준. ` : "";

  if (!c.detectable) {
    return {
      headline:
        p.analyzedMatches === 0 && p.upcomingMatches > 0
          ? "이 시즌은 아직 시작되지 않았다"
          : "아직 진단할 만큼의 경기가 없다",
      sub: `${scope}${c.note}. 지금 원인을 말하면 그건 데이터가 아니라 추측이다 — 경기가 쌓이면 다시 본다.`,
    };
  }

  if (spans.length > 0) {
    const worst = [...spans].sort((a, b) => b.matchCount - a.matchCount)[0];
    return {
      headline: `일정이다 — ${c.windowDays}일 ${c.minMatches}경기 기준을 ${spans.length}번 넘겼다`,
      sub:
        `${scope}가장 빡빡했던 구간은 ${worst.fromLabel}–${worst.toLabel}의 ${worst.summary}. ` +
        `보통 ${c.medianGapDays}일 만에 다시 뛰었고 가장 짧을 땐 ${c.shortestGapDays}일이었다.`,
    };
  }

  if (f.pointsRate != null && f.pointsRate < 0.4) {
    return {
      headline: "일정 탓은 아니다 — 성적 쪽을 봐야 한다",
      sub: `${scope}밀집 기준을 넘긴 구간이 한 번도 없었다. 그런데 ${f.summary}. 원인이 일정 밖에 있다는 뜻이다.`,
    };
  }

  return {
    headline: "특별히 짚을 일정 부하가 없다",
    sub: `${scope}${c.note}. ${f.summary}.`,
  };
}

/** 근거 카드 — 값이 없는 지표는 카드를 만들지 않는다(0으로 채우지 않는다). */
function cards(t: Timeline): DiagnosisCard[] {
  const out: DiagnosisCard[] = [];
  const { congestion: c, form: f, travel: tr, spans, opponents: op } = t;

  // ① 폼 — 표본이 1경기라도 있으면 개수로는 말할 수 있다.
  if (f.sampleSize > 0) {
    out.push({
      key: "form",
      value: f.recordLabel,
      tone: f.pointsRate == null ? "draw" : f.pointsRate >= 0.5 ? "win" : "loss",
      label: `${t.period.seasonLabel ?? "기간"} ${f.sampleSize}경기 성적`,
      detail:
        f.pointsRate == null
          ? `승점률은 내지 않았다 — 확정 경기가 ${f.sampleSize}건이라 비율로 말하면 한 경기가 전체를 흔든다.`
          : `승점 ${f.points}/${f.maxPoints} · 승점률 ${Math.round(f.pointsRate * 100)}%.` +
            (t.period.upcomingMatches > 0
              ? ` 아직 치르지 않은 ${t.period.upcomingMatches}경기는 들어가지 않았다.`
              : " 이 시즌 전체다."),
      highlight: "form",
    });
  }

  // ①-b 상대 강도 — 폼 바로 뒤에 둔다. "4패"를 읽은 직후에 "누구한테"가 와야
  // 두 숫자가 한 문장으로 읽힌다. 순위를 하나도 못 매겼으면 카드를 만들지 않는다.
  if (op.available && op.averageRank != null && op.tableSize != null) {
    const top = op.vsTop;
    const rest = op.vsRest;
    // 비율은 백엔드가 null 로 준 것을 존중한다 — 표본이 얇으면 개수로 말한다.
    const say = (s: typeof top) =>
      s.matches === 0
        ? "만난 적 없음"
        : s.pointsRate == null
          ? `${s.matches}경기 ${s.recordLabel}`
          : `${s.matches}경기 ${s.recordLabel} · 승점률 ${Math.round(s.pointsRate * 100)}%`;

    out.push({
      key: "opponents",
      value: `평균 ${op.averageRank}위`,
      // 상위권을 많이 만났으면 그건 나쁜 성적의 변명이 된다 → 경고색이 아니라 볼트.
      tone: "volt",
      label: `상대 강도 — ${op.measured}경기 기준 (${op.tableSize}팀 중)`,
      detail: [
        `상위권(${op.topCut}위 이내) ${say(top)}`,
        `그 외 ${say(rest)}`,
        op.unmeasured > 0
          ? `컵·시즌 초 ${op.unmeasured}경기는 순위를 매길 수 없어 뺐다.`
          : null,
        op.deductionsKnown ? null : "승점 삭감은 확인하지 못했다.",
      ]
        .filter(Boolean)
        .join(" · "),
      highlight: "form",
    });
  }

  // ② 밀집 구간 — 판정이 가능했을 때만.
  if (c.detectable) {
    out.push({
      key: "congestion",
      value: `${spans.length}개`,
      tone: spans.length > 0 ? "warn" : "draw",
      label: `${c.windowDays}일 ${c.minMatches}경기 기준 밀집 구간`,
      detail:
        spans.length > 0
          ? spans.map((s) => `${s.fromLabel}–${s.toLabel} ${s.summary}`).join(" · ")
          : `가장 빡빡했던 ${c.windowDays}일에도 ${c.busiestWindowMatchCount}경기였다.`,
      highlight: spans.length > 0 ? "congestion" : null,
    });
  }

  // ③ 간격 — 경기가 2건 이상이어야 간격이란 게 존재한다.
  if (c.medianGapDays != null && c.shortestGapDays != null) {
    out.push({
      key: "gap",
      value: `${c.medianGapDays}일`,
      tone: c.medianGapDays <= 4 ? "warn" : "draw",
      label: "경기 간격 중앙값",
      detail: `가장 짧았던 간격은 ${c.shortestGapDays}일. 평균이 아니라 중앙값을 쓰는 건 브레이크 한 번이 평균을 통째로 끌어올리기 때문이다.`,
      highlight: null,
    });
  }

  // ④ 결장 — "부상"이 아니라 "결장"이다. 징계·질병이 섞여 있다.
  if (t.absences.covered && t.absences.totalOut > 0) {
    const a = t.absences;
    const breakdown = (Object.entries(a.byReason) as [AbsenceReason, number][])
      .filter(([, n]) => n > 0)
      .sort((x, y) => y[1] - x[1])
      .map(([k, n]) => `${REASON_LABEL[k]} ${n}`)
      .join(" · ");
    const worst = a.topAbsentees[0];

    out.push({
      key: "absence",
      value: `경기당 ${a.averagePerCoveredMatch}명`,
      tone: (a.averagePerCoveredMatch ?? 0) >= 5 ? "loss" : "draw",
      label: "확정 결장 인원",
      detail:
        `${breakdown}. 한 경기 최대 ${a.maxOutInOneMatch}명` +
        (worst ? `, 최다 결장은 ${worst.playerName} ${worst.matches}경기` : "") +
        `. ${a.coveredMatches}/${a.analyzedMatches}경기만 데이터가 있어 나머지는 '0명'이 아니라 '모름'이다.`,
      highlight: null,
    });
  }

  // ⑤ 이동거리 — 잰 경기가 있을 때만. 부분합이면 그 사실을 detail에 밝힌다.
  if (tr.measuredMatches > 0 && tr.totalKm != null) {
    out.push({
      key: "travel",
      value: `${Math.round(tr.totalKm).toLocaleString("ko-KR")}km`,
      tone: "volt",
      label: "원정 누적 이동거리",
      detail:
        tr.unknownCoordinateMatches > 0
          ? `원정 ${tr.awayMatches}경기 중 ${tr.measuredMatches}경기만 쟀다 — 나머지 ${tr.unknownCoordinateMatches}경기는 경기장 좌표를 몰라 이 값은 부분합이다.`
          : `원정 ${tr.awayMatches}경기 전부 측정. 홈 구장에서 경기장까지 편도 기준이다.`,
      highlight: "travel",
    });
  }

  return out;
}
