// requirements.md 2·4장 — 팀 선택 UI, 선택 대상이 아닌 시즌
"use client";

import type { TeamSelection } from "@/lib/api/types";
import { TeamMark } from "@/components/timeline/TeamMark";

/**
 * 팀 선택 — **조회 시즌에서 파생 계산된 목록**만 보여 준다.
 *
 * <h2>805개를 지원 팀처럼 노출하지 않는다</h2>
 * 목록은 서버가 "그 시즌 선택 기준 대회에 경기가 있는 팀"으로 계산해 준 것이다.
 * 저장된 팀 표를 그대로 뿌리면 FA컵 예선으로 들어온 비리그 클럽까지 고를 수 있게 된다.
 *
 * <h2>고른 팀을 조용히 바꾸지 않는다</h2>
 * 그 시즌 선택 대상이 아니면 목록에는 없지만 **선택 상태는 그대로 유지하고** 왜 그런지
 * 적는다. 목록의 다른 팀으로 갈아 끼우면, 사용자는 자기가 고른 적 없는 팀의 숫자를
 * 자기 팀 숫자로 읽는다.
 */
export function TeamPicker({
  selection,
  onChange,
}: {
  selection: TeamSelection;
  onChange: (teamId: number) => void;
}) {
  const selected = selection.selected;
  const eligible = selected?.eligible ?? false;

  // 선택 대상이 아니어도 이름은 보여야 하므로 목록 밖의 팀을 한 줄 얹는다.
  const options = eligible || !selected
    ? selection.teams
    : [{ teamId: selected.teamId, name: selected.name, code: selected.code },
       ...selection.teams];

  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <div className="flex min-w-0 items-center gap-2 rounded-sm border border-line-strong bg-card px-3 py-2">
        <TeamMark code={selected?.code ?? "?"} size={22} />
        <select
          aria-label="팀 선택"
          value={selected?.teamId ?? ""}
          onChange={(e) => onChange(Number(e.target.value))}
          disabled={options.length <= 1}
          className="min-w-0 flex-1 truncate bg-transparent text-[13px] font-bold text-text-hi outline-none disabled:cursor-default"
        >
          {options.map((t) => (
            <option key={t.teamId} value={t.teamId}>
              {t.name}
            </option>
          ))}
        </select>
      </div>

      {/* 목록이 하나뿐인 이유를 갈라 말한다 — 데이터가 없어서인가, 단계 제한인가 */}
      {!eligible && selected && (
        <p className="text-[11px] font-semibold leading-snug text-warn">
          이 시즌에는 선택 대상 1부 리그 기록이 없습니다
        </p>
      )}
      {eligible && selection.teams.length === 0 && (
        <p className="text-[11px] font-semibold leading-snug text-text-low">
          이 시즌에는 선택 가능한 팀이 없습니다
        </p>
      )}
      {selection.restricted && (
        <p className="text-[11px] font-semibold leading-snug text-text-low">
          비공개 검증 단계 — 지금은 맨체스터 유나이티드만 다룹니다
        </p>
      )}
    </div>
  );
}
