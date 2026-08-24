/**
 * 좌측 대화 — 아직 AI 연동 전이라 답변은 우리가 쓴다. 다만 **숫자는 쓰지 않는다.**
 *
 * 예전 버전은 "가을 내내 밀집 구간이 세 번 겹쳤다" 같은 문장이 통째로 박혀 있었다.
 * 실데이터를 붙이는 순간 그 문장은 옆에 그려진 타임라인과 어긋난다 — 화면은 3경기를
 * 보여 주는데 대화는 밀집 구간 세 개를 말하는 상태다. 사용자 입장에선 앱이 자기 말을
 * 뒤집는 것이고, 이 앱이 파는 게 "데이터로 답한다"인 만큼 가장 하면 안 되는 실패다.
 *
 * 그래서 문장 틀만 남기고 값은 전부 진단 결과에서 읽어 온다. 나중에 AI가 들어와도
 * 읽는 값은 똑같다 — 사람이 쓴 문장이 AI 답변으로 바뀔 뿐이다.
 */
import type { Form, HighlightTag, Timeline } from "@/lib/timeline/types";

export type ChatHighlight = HighlightTag | null;

/**
 * 표본 부족이면 질문 입력 자체를 막는다 (requirements.md DG 5절, DG-OQ-13).
 *
 * 기준은 **전 대회 기준** 폼의 `confidence`다 — 백엔드가
 * `SampleConfidence.allowsRates`(MIN_SAMPLE_FOR_RATE = 5)로 판정해 내려준 값을
 * 그대로 읽는다. 여기서 5를 다시 세면 두 곳에 기준이 생기고, 백엔드가 임계값을
 * 바꾸는 날 화면만 옛 기준으로 막는다.
 *
 * ⚠️ 문구는 잠정이다(IN-OQ-06 미정) — 백엔드 `AiDiagnosisService.insufficientSample`
 * 의 톤("확정된 경기가 %d건뿐이라 결론을 내지 않습니다")과 맞춰 두었다. 문장 속
 * "5건"은 백엔드 `SampleConfidence.MIN_SAMPLE_FOR_RATE` 와 같은 값 — 갈라지면 안 됨.
 *
 * @return 막아야 하면 사용자에게 보여줄 사유, 물을 수 있으면 null
 */
export function questionBlockReason(form: Form): string | null {
  if (form.confidence === "MODERATE" || form.confidence === "SUFFICIENT") {
    return null;
  }
  return `확정된 경기가 ${form.sampleSize}건뿐이라 질문을 받을 수 없습니다 (5건 이상 필요)`;
}

export interface Evidence {
  text: string;
  /** "보기 →" 클릭 시 강조할 태그(없으면 링크 없음). */
  highlight?: HighlightTag;
}

export interface QA {
  key: string;
  question: string;
  answer: string;
  highlight: ChatHighlight;
  evidence: Evidence[];
}

export interface ChatScript {
  intro: string;
  questions: QA[];
}

export function buildChatScript(t: Timeline): ChatScript {
  return {
    intro: `${t.team.name}, ${t.team.subtitle}. 무슨 각인지 물어봐 — 데이터로 답한다.`,
    questions: [
      whyQuestion(t),
      scheduleQuestion(t),
      formQuestion(t),
      nextQuestion(t),
    ],
  };
}

/** "대체 왜 이러냐?" — 판정할 표본이 없으면 없다고 먼저 말한다. */
function whyQuestion(t: Timeline): QA {
  const hasSpans = t.spans.length > 0;
  // 답도 근거도 **같은 기간**에서 나온다. 대화가 화면과 다른 경기를 세면 앱이 자기
  // 말을 뒤집는 것으로 읽힌다.
  const scope = t.period.seasonLabel
    ? `${t.period.seasonLabel} ${t.period.analyzedMatches}경기를 봤다. `
    : "";
  const answer = !t.congestion.detectable
    ? `${scope}아직 답할 만큼의 경기가 없다. 원인을 말하면 그건 추측이지 데이터가 아니다.`
    : hasSpans
      ? `${scope}일정이다. ${t.congestion.windowDays}일 ${t.congestion.minMatches}경기 기준을 넘긴 밀집 구간이 ${t.spans.length}번 있었다.`
      : `${scope}적어도 일정 탓은 아니다. 밀집 기준을 넘긴 구간이 한 번도 없었다.`;

  return {
    key: "why",
    question: "대체 왜 이러냐?",
    answer,
    highlight: hasSpans ? "congestion" : null,
    evidence: [
      { text: t.congestion.note, highlight: hasSpans ? "congestion" : undefined },
      { text: `폼 ${t.form.summary}`, highlight: "form" },
      ...t.spans.slice(0, 2).map((s) => ({
        text: `${s.fromLabel}–${s.toLabel} ${s.summary}`,
        highlight: "congestion" as HighlightTag,
      })),
    ],
  };
}

/** "일정이 문제인가?" — 간격 숫자를 그대로 보여 준다. */
function scheduleQuestion(t: Timeline): QA {
  const { shortestGapDays, medianGapDays } = t.congestion;
  const answer =
    shortestGapDays == null
      ? "간격을 말하려면 경기가 최소 두 건은 있어야 한다. 아직 그만큼도 없다."
      : `보통 ${medianGapDays}일 만에 다시 뛰었고, 가장 짧을 땐 ${shortestGapDays}일이었다.`;

  return {
    key: "schedule",
    question: "일정이 문제인가?",
    answer,
    highlight: t.spans.length > 0 ? "congestion" : null,
    evidence: [
      medianGapDays != null
        ? {
            text: `간격 중앙값 ${medianGapDays}일 · 최단 ${shortestGapDays}일 — 평균 대신 중앙값을 쓰는 건 브레이크 한 번이 평균을 통째로 끌어올리기 때문이다`,
          }
        : { text: "경기가 2건 미만이라 간격 자체가 없다" },
      { text: t.travel.summary },
      ...(t.spans.length === 0
        ? [{ text: t.congestion.note }]
        : t.spans.map((s) => ({
            text: `${s.fromLabel}–${s.toLabel} ${s.summary}`,
            highlight: "congestion" as HighlightTag,
          }))),
    ],
  };
}

/** "폼은 어땠나?" — 표본이 작으면 비율 대신 개수로 답한다. */
function formQuestion(t: Timeline): QA {
  const answer =
    t.form.sampleSize === 0
      ? "결과가 확정된 경기가 아직 없다. 예정 경기를 무승부로 세면 폼이 거짓이 되므로 세지 않는다."
      : t.form.pointsRate == null
        ? `${t.form.summary}. 표본이 작아 승점률은 내지 않는다 — ${t.form.sampleSize}경기로 비율을 말하면 한 경기가 전체를 흔든다.`
        : `${t.form.summary}.`;

  return {
    key: "form",
    question: "폼은 어땠나?",
    answer,
    highlight: "form",
    evidence: [
      {
        text: `폼 스트릭은 이 기간에서 확정된 ${t.form.sampleSize}경기만 센다${
          t.period.upcomingMatches > 0
            ? ` — 예정 ${t.period.upcomingMatches}경기는 빠져 있다`
            : ""
        }`,
        highlight: "form",
      },
      ...t.omissions.map((o) => ({ text: o.reason })),
    ],
  };
}

/** "다음 경기 무슨 각?" — 예측은 킥오프 이전에만 남길 수 있다(이 앱의 핵심 규칙). */
function nextQuestion(t: Timeline): QA {
  const answer =
    t.upcomingCount > 0
      ? `예정된 경기가 ${t.upcomingCount}건 있다. 예측은 킥오프 전에만 남길 수 있다 — 그래야 적중률이 정직해진다.`
      : "예정된 경기가 없다. 동기화된 일정이 여기서 끝난다.";

  return {
    key: "next",
    question: "다음 경기 무슨 각?",
    answer,
    highlight: null,
    evidence: [
      {
        text:
          t.upcomingCount > 0
            ? "타임라인 아래쪽 점선 카드가 예정 경기다"
            : "새 일정은 백엔드가 매시 동기화하면서 채운다",
      },
      { text: "예측·적중률 트래킹 화면은 준비 중" },
    ],
  };
}
