import { cn } from "@usetaehwan/ui";
import type {
  DiagnosisCard,
  HighlightTag,
  Timeline as TimelineVM,
} from "@/lib/timeline/types";

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

  return (
    <div className="flex flex-col gap-5">
      {/* ① 결론 */}
      <div className="rounded-panel border border-line-strong bg-card p-6">
        <div className="mb-2.5 flex items-center gap-2">
          <span className="text-[11px] font-black tracking-[2px] text-volt">
            진단 결론
          </span>
          <AuthorBadge authored={d.authored} />
        </div>
        <div className="font-display text-[30px] font-black leading-[1.25] tracking-tight text-text-hi">
          {d.headline}
        </div>
        <p className="mt-2.5 text-sm leading-relaxed text-text-mid">{d.sub}</p>
      </div>

      {/* ② 근거 */}
      {d.cards.length > 0 && (
        <section className="flex flex-col gap-2.5">
          <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
            근거 — 실제 수치 · 카드를 누르면 타임라인에서 해당 경기를 강조
          </h2>
          {d.cards.map((c) => (
            <EvidenceCard key={c.key} card={c} onInspect={onInspect} />
          ))}
        </section>
      )}

      {/* ③ 모르는 것 */}
      <section className="flex flex-col gap-2.5">
        <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
          데이터로 답 못하는 것 — 아는 척 안 함
        </h2>
        <div className="grid gap-2.5 sm:grid-cols-2 lg:grid-cols-3">
          {d.unknowns.map((u) => (
            <div
              key={u.label}
              className="flex flex-col gap-1 rounded-card border border-dashed border-line-dashed px-4 py-3.5"
            >
              <span className="text-sm font-extrabold text-text-mid">
                {u.label}
              </span>
              <span className="text-[11px] font-extrabold tracking-wider text-text-low">
                {u.structural ? "수집하지 않음" : "계산 생략"}
              </span>
              <span className="mt-0.5 text-[12px] leading-relaxed text-text-low">
                {u.reason}
              </span>
            </div>
          ))}
        </div>
      </section>
    </div>
  );
}

/**
 * 결론을 누가 썼는지 밝힌다. 지금은 규칙 기반 문장이라 그렇게 적는다 —
 * 사용자가 이걸 AI 분석으로 읽으면 실제보다 신뢰하게 된다.
 */
function AuthorBadge({ authored }: { authored: "rule" | "ai" }) {
  if (authored === "ai") {
    return (
      <span className="rounded-badge bg-volt/12 px-2 py-0.5 text-[10px] font-black tracking-wider text-volt">
        AI 분석
      </span>
    );
  }
  return (
    <span
      className="rounded-badge border border-line-strong px-2 py-0.5 text-[10px] font-black tracking-wider text-text-low"
      title="Anthropic 연동 전 — 같은 수치를 규칙으로 문장화한 결과입니다"
    >
      규칙 기반 · AI 연동 전
    </span>
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
