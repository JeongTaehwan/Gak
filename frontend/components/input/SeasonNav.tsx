// requirements.md 3장 — 시즌과 회고
"use client";

import type { TeamSelection } from "@/lib/api/types";
import { seasonLabel } from "@/lib/timeline/format";

/**
 * 시즌 이동 — **버튼 두 개로 한 칸씩.**
 *
 * <h2>드롭다운도, 시즌 목록 화면도 만들지 않는다</h2>
 * 시즌은 사용자가 고르는 값이 아니라 데이터가 정하는 값이다(치른 경기가 있는 시즌 중
 * 가장 큰 것). 목록을 펼쳐 놓으면 아직 경기가 하나도 없는 시즌을 고를 수 있게 되고,
 * 그 화면은 아무 에러 없이 통째로 빈다. 회고에 필요한 건 "한 칸 뒤로"뿐이다.
 *
 * <h2>갈 곳이 없으면 비활성화하고 이유를 적는다</h2>
 * 버튼을 눌렀는데 아무 일도 일어나지 않는 것과, 버튼이 꺼져 있고 왜 꺼졌는지 적혀 있는
 * 것은 다르다. 전자는 고장으로 읽힌다.
 */
export function SeasonNav({
  selection,
  onChange,
}: {
  selection: TeamSelection;
  onChange: (season: number) => void;
}) {
  const label = seasonLabel(selection.season, selection.calendarSeason);

  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-center gap-2">
        <button
          type="button"
          onClick={() => selection.previousSeason != null && onChange(selection.previousSeason)}
          disabled={selection.previousSeason == null}
          className="rounded-sm border border-line-strong bg-card px-2.5 py-1.5 text-[11px] font-extrabold text-text-mid transition-colors hover:border-volt hover:text-volt disabled:cursor-default disabled:border-line disabled:text-text-low disabled:hover:text-text-low"
        >
          ← 지난 시즌
        </button>

        <span className="flex-1 text-center text-[12px] font-extrabold text-text-hi">
          {label ?? "시즌 없음"}
          {/* 최신에 도달했음을 말한다 — 앞으로 갈 곳이 없는 게 고장이 아님을 알린다 */}
          {selection.current && (
            <span className="ml-1.5 text-[10px] font-bold text-volt">현재 시즌</span>
          )}
        </span>

        <button
          type="button"
          onClick={() => selection.nextSeason != null && onChange(selection.nextSeason)}
          disabled={selection.nextSeason == null}
          className="rounded-sm border border-line-strong bg-card px-2.5 py-1.5 text-[11px] font-extrabold text-text-mid transition-colors hover:border-volt hover:text-volt disabled:cursor-default disabled:border-line disabled:text-text-low disabled:hover:text-text-low"
        >
          다음 시즌 →
        </button>
      </div>

      {selection.previousSeason == null && (
        <p className="text-[11px] font-semibold text-text-low">
          이전 시즌 데이터가 없습니다
        </p>
      )}
    </div>
  );
}
