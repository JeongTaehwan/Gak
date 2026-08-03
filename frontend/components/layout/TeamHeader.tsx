import { cn } from "@usetaehwan/ui";
import type { Form, TeamSummary } from "@/lib/timeline/types";
import { TeamMark } from "@/components/timeline/TeamMark";
import { FormStreak } from "@/components/timeline/FormStreak";

/** 우측 패널이 보여 줄 화면. */
export type Mode = "timeline" | "diagnosis" | "prediction" | "standings";

const TABS: { mode: Mode; label: string }[] = [
  { mode: "timeline", label: "통합 타임라인" },
  { mode: "diagnosis", label: "진단" },
  { mode: "prediction", label: "예측 · 적중 기록" },
  { mode: "standings", label: "순위표" },
];

/**
 * 우측 패널 상단 — 팀 정보 + 최근 폼 + 모드 탭.
 *
 * 팀명·코드·기간은 전부 백엔드 응답에서 온다. 예전엔 "프리미어리그 8위 · 2023-24"가
 * 그대로 박혀 있었는데, 순위는 우리가 가진 사실이 아니다(순위표를 동기화하지 않는다).
 * 화면이 모르는 값을 적어 두면 그게 가장 먼저 틀린다.
 */
export function TeamHeader({
  team,
  form,
  mode,
  onModeChange,
}: {
  team: TeamSummary;
  form: Form;
  mode: Mode;
  onModeChange: (mode: Mode) => void;
}) {
  return (
    <div className="border-b border-line px-7 pt-5">
      <div className="flex items-start justify-between gap-6">
        <div className="flex min-w-0 items-center gap-4">
          <TeamMark code={team.code} size={48} />
          <div className="flex min-w-0 flex-col gap-0.5">
            <div className="truncate font-display text-2xl font-black tracking-tight text-text-hi">
              {team.name}
            </div>
            <div className="text-xs font-semibold text-text-mid">
              {team.subtitle}
            </div>
          </div>
        </div>
        <FormStreak form={form} />
      </div>

      <div className="mt-4 flex gap-1" role="tablist">
        {TABS.map((t) => (
          <button
            key={t.mode}
            type="button"
            role="tab"
            aria-selected={mode === t.mode}
            onClick={() => onModeChange(t.mode)}
            className={cn(
              "border-b-2 px-4.5 py-2.5 text-sm font-extrabold transition-colors",
              mode === t.mode
                ? "border-volt text-text-hi"
                : "border-transparent text-text-low hover:text-text-mid",
            )}
          >
            {t.label}
          </button>
        ))}
      </div>
    </div>
  );
}
