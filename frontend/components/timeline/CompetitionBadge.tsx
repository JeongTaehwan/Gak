import { cn } from "@usetaehwan/ui";
import type { Competition, CompetitionKey } from "@/lib/timeline/types";

/** 대회 식별 색 (토큰). 리그=실버 / 컵=시안 / 유럽=바이올렛. */
const COMP_CLASS: Record<CompetitionKey, string> = {
  league: "text-comp-league border-comp-league/35",
  cup: "text-comp-cup border-comp-cup/35",
  europe: "text-comp-europe border-comp-europe/35",
};

export function CompetitionBadge({
  competition,
  className,
}: {
  competition: Competition;
  className?: string;
}) {
  return (
    <span
      className={cn(
        "inline-flex shrink-0 items-center rounded-badge border px-2 py-[3px] text-[10px] font-black tracking-wide",
        COMP_CLASS[competition.key],
        className,
      )}
    >
      {competition.label}
    </span>
  );
}
