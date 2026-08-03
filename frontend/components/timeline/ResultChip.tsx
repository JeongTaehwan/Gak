import { cn } from "@usetaehwan/ui";
import type { MatchResult } from "@/lib/timeline/types";

/**
 * 승/무/패 이중 부호화 — 색 + 형태 (색각 이상 대응, 색은 보조 신호).
 *   W 사각(채움) · D 원(외곽선) · L 마름모(채움)
 *
 * `result`가 null이면 **아직 결과가 없는 경기**다. 이때 D(무승부)로 그리지 않는다 —
 * 안 치른 경기가 무승부로 스트릭에 뜨면 폼 전체가 거짓이 된다. 대신 결과 자리를
 * 비어 있는 자리로 그려서 "아직 없다"는 것 자체를 보여 준다.
 */
const SHAPE: Record<MatchResult, { box: string; inner: string; label: string }> =
  {
    W: { box: "bg-win text-canvas rounded-badge", inner: "", label: "승" },
    D: {
      box: "border border-draw text-draw rounded-full bg-transparent",
      inner: "",
      label: "무",
    },
    L: {
      box: "bg-loss text-canvas rounded-badge rotate-45",
      inner: "-rotate-45",
      label: "패",
    },
  };

export function ResultChip({
  result,
  size = 26,
}: {
  result: MatchResult | null;
  size?: number;
}) {
  if (result === null) {
    return (
      <span
        className="flex shrink-0 items-center justify-center rounded-badge border border-dashed border-line-dashed text-text-low"
        style={{ width: size, height: size }}
        role="img"
        aria-label="결과 없음"
      >
        <span className="font-display text-xs font-black leading-none">·</span>
      </span>
    );
  }

  const s = SHAPE[result];
  return (
    <span
      className={cn("flex shrink-0 items-center justify-center", s.box)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={s.label}
    >
      <span className={cn("font-display text-xs font-black leading-none", s.inner)}>
        {result}
      </span>
    </span>
  );
}
