import { cn } from "@usetaehwan/ui";
import type { StandingRow, StandingsTable } from "@/lib/api/types";

/**
 * 순위표.
 *
 * ## 진단의 상대 순위와 다른 값이다 — 그걸 숨기지 않는다
 *
 * 타임라인의 "vs 아스날 2위"는 **그 경기 시점**에 우리가 경기 결과로 계산한 순위이고,
 * 이 표는 **API가 준 지금 순위**다. 둘이 어긋날 수 있고 그건 버그가 아니라 서로 다른
 * 질문에 답하는 값이기 때문이다.
 *
 * 그래서 표 위에 기준 시각을 적는다. 안 적으면 사용자는 이 표를 실시간으로 읽는다.
 *
 * ## 승격·강등권을 색으로 말하지 않는다
 *
 * API가 준 `description`("Promotion - Champions League")을 왼쪽 띠로 그리되, 색만으로
 * 구분하지 않고 **글자로도 적는다.** 색맹인 사용자에게 초록/빨강 띠는 같은 회색이다.
 */
export function StandingsPanel({ table }: { table: StandingsTable }) {
  if (!table.available) {
    return (
      <div className="rounded-panel border border-dashed border-line-dashed p-6">
        <div className="text-sm font-extrabold text-text-mid">순위표 없음</div>
        <p className="mt-1.5 text-[13px] leading-relaxed text-text-low">
          {table.unavailableReason}
        </p>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <header className="flex flex-wrap items-baseline gap-x-3 gap-y-1">
        <h2 className="font-display text-lg font-black text-text-hi">
          {table.competitionName}
        </h2>
        <span className="text-xs font-bold text-text-mid">
          {table.season}–{String((table.season ?? 0) + 1).slice(2)} 시즌
        </span>
        {/*
          이 표가 언제 기준인지. 진단의 상대 순위와 다를 수 있는 이유가 여기 있으므로
          작게라도 반드시 적는다.
        */}
        <span className="ml-auto text-[11px] text-text-low">
          {table.updatedAt && `${toStamp(table.updatedAt)} 기준 · API 제공 순위`}
        </span>
      </header>

      <div className="overflow-x-auto rounded-panel border border-line-strong bg-card">
        <table className="w-full min-w-[560px] border-collapse text-sm">
          <thead>
            <tr className="border-b border-line text-[11px] font-extrabold tracking-wider text-text-low">
              <Th className="w-12 text-center">순위</Th>
              <Th className="text-left">팀</Th>
              <Th className="w-14 text-right">경기</Th>
              <Th className="w-14 text-right">득실</Th>
              <Th className="w-14 text-right">승점</Th>
            </tr>
          </thead>
          <tbody>
            {table.rows.map((row) => (
              <Row key={row.teamId} row={row} />
            ))}
          </tbody>
        </table>
      </div>

      <p className="text-[11px] leading-relaxed text-text-low">
        타임라인에 붙는 상대 순위는 <b>그 경기 시점</b>의 순위라 이 표와 다르다. 8월
        경기를 5월 순위로 평가하지 않으려고 경기 결과로 따로 계산한다.
      </p>
    </div>
  );
}

function Th({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return <th className={cn("px-3 py-2.5 font-extrabold", className)}>{children}</th>;
}

/** 순위의 의미 → 화면 라벨과 색. API 문자열을 그대로 노출하지 않는다(영문이다). */
function zone(description: string | null): { label: string; tone: string } | null {
  if (!description) return null;
  const d = description.toLowerCase();
  if (d.includes("champions league")) {
    return { label: "챔스", tone: "bg-win" };
  }
  if (d.includes("europa league")) {
    return { label: "유로파", tone: "bg-volt" };
  }
  if (d.includes("conference")) {
    return { label: "컨퍼런스", tone: "bg-draw" };
  }
  if (d.includes("relegation")) {
    return { label: "강등", tone: "bg-loss" };
  }
  return null;
}

function Row({ row }: { row: StandingRow }) {
  const z = zone(row.description);

  return (
    <tr
      className={cn(
        "border-b border-line/60 last:border-b-0",
        // 지금 보고 있는 팀. 20줄에서 눈이 갈 곳을 만든다.
        row.highlighted && "bg-volt/[0.07]",
      )}
    >
      <td className="relative px-3 py-2.5 text-center">
        {/* 승격·강등권 띠. 색만으로는 구분되지 않으므로 아래 라벨과 함께 쓴다. */}
        {z && (
          <span
            className={cn("absolute left-0 top-0 h-full w-[3px]", z.tone)}
            aria-hidden
          />
        )}
        <span
          className={cn(
            "font-display text-[15px] font-black tabular-nums",
            row.highlighted ? "text-volt" : "text-text-mid",
          )}
        >
          {row.rank}
        </span>
      </td>

      <td className="px-3 py-2.5">
        <span className="flex flex-wrap items-center gap-2">
          <span
            className={cn(
              "font-bold",
              row.highlighted ? "text-volt" : "text-text-hi",
            )}
          >
            {row.teamName}
          </span>
          {z && (
            <span className="rounded-badge border border-line-strong px-1.5 py-0.5 text-[10px] font-black tracking-wider text-text-low">
              {z.label}
            </span>
          )}
        </span>
      </td>

      <td className="px-3 py-2.5 text-right tabular-nums text-text-mid">
        {row.played}
      </td>
      <td
        className={cn(
          "px-3 py-2.5 text-right tabular-nums",
          row.goalsDiff > 0
            ? "text-win"
            : row.goalsDiff < 0
              ? "text-loss"
              : "text-text-mid",
        )}
      >
        {row.goalsDiff > 0 ? `+${row.goalsDiff}` : row.goalsDiff}
      </td>
      <td
        className={cn(
          "px-3 py-2.5 text-right font-display font-black tabular-nums",
          row.highlighted ? "text-volt" : "text-text-hi",
        )}
      >
        {row.points}
      </td>
    </tr>
  );
}

/** "2026-08-03T12:22:39Z" → "8/3 12:22". 초는 이 화면에서 의미가 없다. */
function toStamp(iso: string): string {
  const d = new Date(iso);
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, "0")}:${String(
    d.getMinutes(),
  ).padStart(2, "0")}`;
}
