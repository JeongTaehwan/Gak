import { cn } from "@usetaehwan/ui";
import type { MatchResult } from "@/lib/timeline/types";

/**
 * 승/무/패 이중 부호화 — 색 + 형태 (색각 이상 대응, 색은 보조 신호).
 *   W 사각(채움) · D 원(외곽선) · L 마름모(채움)
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
  result: MatchResult;
  size?: number;
}) {
  const s = SHAPE[result];
  return (
    <span
      className={cn("flex shrink-0 items-center justify-center", s.box)}
      style={{ width: size, height: size }}
      role="img"
      aria-label={s.label}
    >
      <span
        className={cn(
          "font-display text-xs font-black leading-none",
          s.inner,
        )}
      >
        {result}
      </span>
    </span>
  );
}
