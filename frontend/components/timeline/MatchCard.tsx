import { cn } from "@usetaehwan/ui";
import type {
  HighlightTag,
  MatchResult,
  TimelineRow,
} from "@/lib/timeline/types";
import { ResultChip } from "@/components/timeline/ResultChip";
import { CompetitionBadge } from "@/components/timeline/CompetitionBadge";

/**
 * 결과별 좌측 컬러 바 — 토큰 CSS 변수로 지정.
 * 프레임 border-color(전 방향)와 왼쪽 색이 충돌하지 않도록 inline 으로 못 박는다
 * (클래스 간 border-color vs border-left-color 우선순위 애매함 회피). 값은 토큰 참조.
 *
 * 아직 결과가 없는 경기는 결과 색이 없다 — 중립 라인 색으로 둔다.
 */
const RESULT_VAR: Record<MatchResult, string> = {
  W: "var(--color-win)",
  D: "var(--color-draw)",
  L: "var(--color-loss)",
};
const PENDING_VAR = "var(--color-line-strong)";

/** 강조 시 카드 외곽선 색(강조 성격별). */
const ACCENT_BORDER: Record<HighlightTag, string> = {
  congestion: "border-warn/55",
  form: "border-volt/45",
  europe: "border-comp-europe/60",
  travel: "border-comp-cup/60",
};

export type Emphasis = "none" | "on" | "dim";

/**
 * 통합 타임라인의 한 경기 카드.
 * 레이아웃 안전 규칙: 팀명 셀만 flex-1 + min-w-0 + 1줄 ellipsis, 날짜·뱃지·스코어·
 * 결과칩은 shrink-0 이라 긴 팀명이 와도 깨지지 않는다. (시안 2b)
 */
export function MatchCard({
  row,
  emphasis = "none",
  accent = null,
}: {
  row: TimelineRow;
  emphasis?: Emphasis;
  accent?: HighlightTag | null;
}) {
  const haColor = row.homeAway === "A" ? "text-loss" : "text-draw";

  return (
    <div
      style={{
        borderLeftColor: row.result ? RESULT_VAR[row.result] : PENDING_VAR,
      }}
      className={cn(
        "flex items-center gap-4 rounded-card border border-l-[3px] px-4 py-3 transition-opacity",
        emphasis === "on"
          ? cn("bg-card-hi", accent ? ACCENT_BORDER[accent] : "border-line")
          : "bg-card border-line",
        emphasis === "dim" && "opacity-35",
        // 아직 안 치른 경기는 한 톤 낮춰서 "기록"과 "예정"이 섞여 보이지 않게.
        row.pending && emphasis !== "on" && "border-dashed",
      )}
    >
      {/* 날짜 */}
      <div className="flex w-[46px] shrink-0 flex-col items-center">
        <span className="font-display text-[15px] font-extrabold text-text-hi">
          {row.dateLabel}
        </span>
        <span className="text-[10px] font-bold text-text-low">{row.dow}</span>
      </div>

      <CompetitionBadge competition={row.competition} />

      {/* 홈/원정 */}
      <span
        className={cn(
          "w-[26px] shrink-0 font-display text-[11px] font-extrabold",
          haColor,
        )}
      >
        {row.homeAway}
      </span>

      {/* 상대 팀명 — 유일하게 늘어나고 줄어드는 셀 */}
      <span className="flex min-w-0 flex-1 items-baseline gap-1.5">
        <span className="truncate text-sm font-bold text-text-hi">{row.opponent}</span>
        {/*
          그 경기 시점의 상대 순위.

          **null 이면 아무것도 그리지 않는다.** 컵 경기이거나 시즌 초라 순위를 말할 수
          없다는 뜻이고, "-"나 "0위"로 채우면 화면이 거짓을 말한다. 자리를 비워 두는 게
          정직하다 — 순위가 붙은 줄과 안 붙은 줄이 섞여 보이는 건 의도다.

          상위권 상대는 볼트로 강조한다. "4패인데 상위권은 한 번뿐"이 한눈에 보이는 게
          이 표시의 목적이다.
        */}
        {row.opponentRank != null && (
          <span
            className={cn(
              "shrink-0 whitespace-nowrap font-display text-[11px] font-extrabold tabular-nums",
              row.opponentTop ? "text-volt" : "text-text-low",
            )}
            title={
              row.opponentTop
                ? "이 경기 직전 기준 상위권 상대"
                : "이 경기 직전 기준 리그 순위"
            }
          >
            {row.opponentRank}위
          </span>
        )}
      </span>

      {/*
        확정 결장 인원. null 은 "데이터 없음"이라 아무것도 그리지 않는다 — "0명"으로
        그리면 데이터가 없는 경기를 "전원 출전"으로 읽게 된다.
      */}
      {row.absentCount != null && row.absentCount > 0 && (
        <span
          className="shrink-0 rounded-badge bg-loss/10 px-2 py-[3px] text-[11px] font-extrabold text-loss"
          title={`확정 결장 ${row.absentCount}명 (부상·징계 등)`}
        >
          결장 {row.absentCount}
        </span>
      )}

      {/* 승부차기 — 결과 집계는 무승부지만 진출 여부는 따로 밝힌다 */}
      {row.shootoutNote && (
        <span className="shrink-0 rounded-badge bg-comp-cup/12 px-2 py-[3px] text-[11px] font-extrabold text-comp-cup">
          {row.shootoutNote}
        </span>
      )}

      {/* 연장·예정 등 상태 문구 */}
      {row.statusNote && (
        <span
          className={cn(
            "shrink-0 rounded-badge px-2 py-[3px] text-[11px] font-extrabold",
            row.pending
              ? "bg-line/60 text-text-mid"
              : "bg-warn/12 text-warn",
          )}
        >
          {row.statusNote}
        </span>
      )}

      {/* 스코어 — 없는 경기는 "–"로 비워 둔다(0-0으로 접지 않는다) */}
      <span
        className={cn(
          "w-[52px] shrink-0 text-right font-display text-lg font-black",
          row.score ? "text-text-hi" : "text-text-low",
        )}
      >
        {row.score ?? "–"}
      </span>

      <ResultChip result={row.result} />
    </div>
  );
}
