import { cn } from "@usetaehwan/ui";
import type { Gap } from "@/lib/timeline/types";

/**
 * 경기 사이 간격 = 세로 여백. 높이는 비선형 스케일로 환산된 gap.px.
 * 짧은 휴식(≤4일)은 경고 톤, 긴 공백은 차분한 톤 — "붙었나/떴나"를 눈으로 읽게.
 */
export function GapMarker({
  gap,
  inRail = false,
}: {
  gap: Gap;
  inRail?: boolean;
}) {
  return (
    <div
      style={{ height: gap.px }}
      className={cn("flex items-center gap-2.5", inRail ? "" : "pl-[22px]")}
    >
      <div
        className={cn(
          "w-0.5 self-stretch rounded",
          gap.shortRest ? "bg-rail-warm" : "bg-line-strong",
        )}
      />
      <span
        className={cn(
          "whitespace-nowrap font-display text-[11px] font-bold",
          gap.shortRest ? "text-warn" : "text-text-low",
        )}
      >
        {gap.label}
      </span>
    </div>
  );
}
