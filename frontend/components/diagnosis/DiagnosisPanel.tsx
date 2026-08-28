"use client";

import { cn } from "@usetaehwan/ui";
import type {
  DiagnosisCard,
  HighlightTag,
  Timeline as TimelineVM,
  UnknownItem,
} from "@/lib/timeline/types";
import type { AiEvidence } from "@/lib/api/types";
import { useAiDiagnosis } from "@/lib/diagnosis/useAiDiagnosis";

/**
 * 진단 화면 (시안 1a 모드 2) — 결론 · 근거 수치 · 모르는 것.
 *
 * 세 블록의 순서가 곧 이 앱의 태도다. 결론을 먼저 말하고, 그 근거가 된 **실제 수치**를
 * 보여 주고, 마지막으로 **답하지 못한 것**을 스스로 밝힌다. 마지막 블록이 없으면
 * 진단이 실제보다 완전해 보인다 — 부진의 원인으로 가장 먼저 떠오르는 것들(부상·전술)이
 * 하필 우리가 못 보는 것들이라 더 그렇다.
 *
 * 근거 카드를 누르면 타임라인 탭으로 넘어가면서 해당 경기들이 강조된다.
 */
const TONE_CLASS: Record<DiagnosisCard["tone"], string> = {
  win: "text-win",
  loss: "text-loss",
  draw: "text-draw",
  warn: "text-warn",
  volt: "text-volt",
};

export function DiagnosisPanel({
  timeline,
  onInspect,
}: {
  timeline: TimelineVM;
  /** 근거 카드 클릭 — 타임라인으로 이동하며 강조. */
  onInspect: (tag: HighlightTag) => void;
}) {
  const d = timeline.diagnosis;

  /*
    AI 문장은 **덧칠**이다. 아래 화면은 이 요청이 실패해도, 느려도, 아예 꺼져 있어도
    이미 완성돼 있다 — 규칙 기반 결론이 서버 렌더에 실려 왔기 때문이다. 그래서
    스켈레톤도 스피너도 없다. 있는 문장을 가리면서 기다리게 할 이유가 없다.
  */
  const ai = useAiDiagnosis(timeline.team.id, timeline.period.season);
  const aiReady = ai.state === "done" && ai.diagnosis?.available === true;

  // 문장·근거·배지는 한 덩어리로 전환된다(DG 3절) — AI 성공 시 규칙 기반 근거
  // 카드(아래 ②)도 함께 내려가고, 근거는 결론 카드 안의 AI 근거만 남는다.
  // 서로 다른 생성원의 근거가 한 진단처럼 섞이면 어디까지가 계산이고 어디부터가
  // 모델인지 사용자가 가를 수 없다. 축별 수치 상세는 타임라인 탭에 남아 있다.
  const headline = aiReady ? ai.diagnosis!.headline! : d.headline;
  const sub = aiReady ? ai.diagnosis!.sub! : d.sub;

  return (
    <div className="flex flex-col gap-5">
      {/* ① 근거 — 결론보다 먼저 놓는다 (DG 4절 "근거가 결론보다 먼저다"의 표시 순서
          해석, DG-OQ-22 확정 · 2026-08-25 오너 위임). 타임라인(사실)→진단(해석)과 같은
          방향 — 수치를 먼저 보이고 그 아래에서 해석한다. 규칙 기반 상태에서만 —
          AI 전환 시 한 덩어리로 함께 내려가고 근거는 결론 카드 안의 AI 근거가 맡는다 */}
      {!aiReady && d.cards.length > 0 && (
        <section className="flex flex-col gap-2.5">
          <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
            근거 — {timeline.period.label} · 카드를 누르면 타임라인에서 해당 경기를
            강조
          </h2>
          {d.cards.map((c) => (
            <EvidenceCard key={c.key} card={c} onInspect={onInspect} />
          ))}
        </section>
      )}

      {/* ② 결론 */}
      <div className="rounded-panel border border-line-strong bg-card p-6">
        <div className="mb-2.5 flex flex-wrap items-center gap-2">
          <span className="text-[11px] font-black tracking-[2px] text-volt">
            진단 결론
          </span>
          <PeriodBadge period={timeline.period} />
          <AuthorBadge authored={aiReady ? "ai" : "rule"} />
          {ai.state === "loading" && (
            <span className="text-[10px] font-extrabold tracking-wider text-text-low">
              AI 분석 중…
            </span>
          )}
          {ai.state === "done" &&
            !aiReady &&
            ai.diagnosis?.unavailableReason && (
              /*
                왜 규칙 기반에 머물렀는지 밝힌다. 조용히 실패하면 사용자는 이 앱에
                AI가 있다는 것도, 왜 안 돌았는지도 모른다 — "표본 3건이라 결론을
                내지 않았다"는 그 자체로 이 앱이 하는 말 중 하나다.
              */
              <span className="text-[10px] font-medium text-text-low">
                {ai.diagnosis.unavailableReason}
              </span>
            )}
        </div>
        {/* AI 근거도 결론보다 먼저 (DG-OQ-22) */}
        {aiReady && ai.diagnosis!.evidence.length > 0 && (
          <AiEvidenceList evidence={ai.diagnosis!.evidence} />
        )}

        <div className="font-display mt-2.5 text-[30px] font-black leading-[1.25] tracking-tight text-text-hi">
          {headline}
        </div>
        <p className="mt-2.5 text-sm leading-relaxed text-text-mid">{sub}</p>
      </div>

      {/* ③ 모르는 것 — 세 갈래를 한 블록으로 모으고 기본은 접어 둔다 */}
      <UnknownsBlock
        items={[
          ...d.unknowns,
          // AI가 이번 결론에서 스스로 밝힌 한계. 위의 두 갈래와 성격이 다르다 —
          // 앞의 것들은 고정된 사실이고 이건 결론마다 달라진다. 그래서 갈래를 나눠 둔다.
          ...(aiReady
            ? ai.diagnosis!.unknowns.map((u) => ({
                label: null,
                reason: u,
                kind: "ai" as const,
              }))
            : []),
        ]}
      />
    </div>
  );
}

/**
 * 이 진단이 **어느 기간을 본 것인지** 밝힌다.
 *
 * 아래의 모든 수치가 이 기간 하나에서 나온다. 기간을 적지 않으면 "12경기 4패"와
 * "밀집 구간 3개"가 같은 경기들을 가리키는지 아무도 알 수 없고, 시즌이 진행 중일 때는
 * 화면에 보이는 예정 경기가 계산에 들어갔다고 오해하게 된다.
 *
 * <p>자동으로 고른 시즌이라도 **사용자는 어느 시즌을 보고 있는지 알아야 한다.**
 */
function PeriodBadge({ period }: { period: TimelineVM["period"] }) {
  return (
    <span
      className="rounded-badge border border-line-strong px-2 py-0.5 text-[10px] font-black tracking-wider text-text-mid"
      title={
        period.inProgress
          ? "시즌이 진행 중입니다. 아직 치르지 않은 경기는 어떤 지표에도 들어가 있지 않습니다"
          : "이 시즌은 끝났습니다 — 시즌 전체가 진단 대상입니다"
      }
    >
      {period.label}
    </span>
  );
}

/**
 * 결론을 **누가 썼는지** 밝힌다.
 *
 * 이 배지가 틀리면 다른 게 다 맞아도 화면이 거짓말을 한다. 규칙 기반 문장에 "AI 분석"이
 * 붙으면 사용자는 우리가 하지 않은 분석을 했다고 믿고, 반대면 모델이 쓴 문장을
 * 결정론적 계산으로 오해한다. 후자가 더 위험하다 — AI 문장은 숫자를 옳게 인용하면서
 * 인과를 틀리게 말할 수 있고, 그걸 규칙의 산물로 읽으면 검증 없이 받아들이게 된다.
 *
 * 그래서 이 값은 **실제 응답에서만** 나온다. "AI 켜져 있음" 같은 설정값이 아니라
 * `available === true`인 응답을 받았을 때만 "AI 분석"이 된다.
 *
 * 답 영역(대화 말풍선)도 같은 원칙을 따르므로(requirements.md DG 5절, DG-OQ-07)
 * 이 배지를 그대로 내보내 재사용한다 — 두 자리의 배지가 다른 모양이면 같은 구분이
 * 아닌 것처럼 읽힌다.
 */
export function AuthorBadge({ authored }: { authored: "rule" | "ai" }) {
  if (authored === "ai") {
    return (
      <span
        className="rounded-badge bg-volt/12 px-2 py-0.5 text-[10px] font-black tracking-wider text-volt"
        title="같은 수치를 Claude 가 문장으로 옮긴 결과입니다. 근거는 아래에 함께 표시됩니다"
      >
        AI 분석
      </span>
    );
  }
  return (
    <span
      className="rounded-badge border border-line-strong px-2 py-0.5 text-[10px] font-black tracking-wider text-text-low"
      title="계산된 수치를 규칙으로 문장화한 결과입니다. AI를 쓰지 않았습니다"
    >
      규칙 기반
    </span>
  );
}

/**
 * AI 결론에 붙은 근거.
 *
 * **결론과 같은 카드 안에** 둔다. 아래 ② 블록(우리가 계산한 근거 카드)과 섞으면
 * "이 숫자를 누가 골랐는지"가 흐려진다 — ②는 항상 같은 규칙으로 뽑히지만 여기 것은
 * 모델이 무엇을 근거로 삼았다고 *말했는지*다. 값 자체는 우리가 준 것이므로 틀릴 수
 * 없지만, 어떤 것을 골라 인과로 엮었는지는 모델의 판단이다.
 */
function AiEvidenceList({ evidence }: { evidence: AiEvidence[] }) {
  return (
    <div className="mt-4 flex flex-col gap-1.5 border-t border-line pt-3.5">
      <span className="text-[10px] font-extrabold tracking-[1.5px] text-text-low">
        이 결론이 근거로 삼은 수치
      </span>
      {evidence.map((e, i) => (
        <div key={i} className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
          <span className="font-display text-[13px] font-black text-volt">
            {e.value}
          </span>
          <span className="text-[12px] font-bold text-text-mid">{e.metric}</span>
          <span className="text-[12px] text-text-low">— {e.claim}</span>
        </div>
      ))}
    </div>
  );
}

function EvidenceCard({
  card,
  onInspect,
}: {
  card: DiagnosisCard;
  onInspect: (tag: HighlightTag) => void;
}) {
  const clickable = card.highlight !== null;
  const Wrapper = clickable ? "button" : "div";

  return (
    <Wrapper
      {...(clickable
        ? {
            type: "button" as const,
            onClick: () => onInspect(card.highlight as HighlightTag),
          }
        : {})}
      className={cn(
        "flex w-full items-center gap-4 rounded-card border border-line bg-panel px-5 py-4 text-left transition-colors",
        clickable && "cursor-pointer hover:border-volt",
      )}
    >
      {/*
        값 셀은 고정 폭이 아니라 **최소 폭 + 줄바꿈 금지**다. 시안은 "1승 4패"처럼 짧은
        값을 전제했는데 실제로는 "3승 1무 2패"·"4,493km"가 온다. 고정 폭이면 두 줄로
        접히면서 카드 높이가 제각각이 된다. 긴 값은 한 단계 작은 글자로 흡수한다.
      */}
      <span
        className={cn(
          "min-w-[130px] shrink-0 whitespace-nowrap font-display font-black tracking-tight",
          card.value.length > 8 ? "text-[24px]" : "text-[30px]",
          TONE_CLASS[card.tone],
        )}
      >
        {card.value}
      </span>
      <span className="flex min-w-0 flex-col gap-1">
        <span className="text-[15px] font-extrabold text-text-hi">
          {card.label}
        </span>
        <span className="text-[13px] leading-relaxed text-text-mid">
          {card.detail}
        </span>
      </span>
      {clickable && (
        <span className="ml-auto shrink-0 whitespace-nowrap text-xs font-extrabold text-volt">
          타임라인 →
        </span>
      )}
    </Wrapper>
  );
}

const UNKNOWN_KIND: Record<
  UnknownItem["kind"],
  { label: string; hint: string }
> = {
  omitted: {
    label: "이번엔 계산 못 함",
    hint: "데이터를 채우면 이 자리는 메워진다",
  },
  structural: {
    label: "수집하지 않음",
    hint: "고쳐도 안 채워진다 — 애초에 우리가 다루는 영역이 아니다",
  },
  ai: {
    label: "AI가 이번 결론에서 짚은 것",
    hint: "결론마다 달라진다",
  },
};

const KIND_ORDER: UnknownItem["kind"][] = ["omitted", "structural", "ai"];

/**
 * "데이터로 답 못하는 것" — 한 블록, 기본 접힘.
 *
 * ## 왜 합쳤나
 * "계산 생략"과 "AI가 모른다고 밝힌 것"을 따로 두니 같은 말이 두 번 나오는 것처럼
 * 보였다. 사용자에게는 둘 다 "이 앱이 모르는 것"이고, 갈래는 그 안에서 나누면 된다.
 *
 * ## 왜 접었나
 * 이 블록은 **없애지 않는다.** 부진의 원인으로 가장 먼저 떠오르는 것들(전술·라커룸)이
 * 하필 우리가 못 보는 것들이라, 안 밝히면 진단이 실제보다 완전해 보인다.
 *
 * 다만 펼친 채로 두면 결론·근거보다 이 목록이 길어져 화면의 무게중심이 "모르는 것"으로
 * 옮겨간다. 접어 두되 **개수를 요약줄에 적어** 있다는 사실은 숨기지 않는다.
 * `<details>` 를 쓰므로 JS 없이도 열리고, 브라우저 찾기(Ctrl+F)에도 걸린다.
 */
function UnknownsBlock({ items }: { items: UnknownItem[] }) {
  if (items.length === 0) return null;

  return (
    <details className="group rounded-panel border border-dashed border-line-dashed">
      <summary className="flex cursor-pointer list-none items-center gap-2 px-5 py-3.5 text-xs font-extrabold tracking-[1.5px] text-text-low hover:text-text-mid">
        <span className="inline-block transition-transform group-open:rotate-90">
          ▸
        </span>
        데이터로 답 못하는 것 — 아는 척 안 함
        <span className="rounded-badge border border-line-strong px-1.5 py-0.5 text-[10px] font-black tabular-nums">
          {items.length}
        </span>
      </summary>

      <div className="flex flex-col gap-4 border-t border-line px-5 pb-5 pt-4">
        {KIND_ORDER.map((kind) => {
          const group = items.filter((u) => u.kind === kind);
          if (group.length === 0) return null;
          const meta = UNKNOWN_KIND[kind];
          return (
            <section key={kind} className="flex flex-col gap-2">
              <h3 className="flex flex-wrap items-baseline gap-2">
                <span className="text-[11px] font-extrabold tracking-wider text-text-mid">
                  {meta.label}
                </span>
                <span className="text-[11px] text-text-low">{meta.hint}</span>
              </h3>
              <ul className="flex flex-col gap-1.5">
                {group.map((u, i) => (
                  <li
                    key={`${kind}-${i}`}
                    className="text-[12px] leading-relaxed text-text-mid"
                  >
                    {u.label && (
                      <span className="font-extrabold text-text-hi">
                        {u.label}
                        {" — "}
                      </span>
                    )}
                    {u.reason}
                  </li>
                ))}
              </ul>
            </section>
          );
        })}
      </div>
    </details>
  );
}
