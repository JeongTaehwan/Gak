import { cn } from "@usetaehwan/ui";
import type { MatchResult } from "@/lib/timeline/types";
import { TeamMark } from "@/components/timeline/TeamMark";
import { FormStreak } from "@/components/timeline/FormStreak";

/** 우측 패널 상단 — 팀 정보 + 최근 폼 + 모드 탭. */
export function TeamHeader({ form }: { form: MatchResult[] }) {
  return (
    <div className="border-b border-line px-7 pt-5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <TeamMark code="MUN" size={48} />
          <div className="flex flex-col gap-0.5">
            <div className="font-display text-2xl font-black tracking-tight text-text-hi">
              맨체스터 유나이티드
            </div>
            <div className="text-xs font-semibold text-text-mid">
              프리미어리그 8위 · 2023-24 시즌 · 전 대회 통합
            </div>
          </div>
        </div>
        <FormStreak form={form} />
      </div>

      {/* 모드 탭 — 타임라인 먼저. 진단·예측은 준비 중. */}
      <div className="mt-4 flex gap-1">
        <Tab active>통합 타임라인</Tab>
        <Tab disabled>진단</Tab>
        <Tab disabled>예측 · 적중 기록</Tab>
      </div>
    </div>
  );
}

function Tab({
  children,
  active = false,
  disabled = false,
}: {
  children: React.ReactNode;
  active?: boolean;
  disabled?: boolean;
}) {
  return (
    <button
      type="button"
      disabled={disabled}
      title={disabled ? "준비 중" : undefined}
      className={cn(
        "border-b-2 px-4.5 py-2.5 text-sm font-extrabold transition-colors",
        active
          ? "border-volt text-text-hi"
          : "border-transparent text-text-low",
        disabled && "cursor-not-allowed",
      )}
    >
      {children}
      {disabled && (
        <span className="ml-1.5 text-[10px] font-bold text-text-low">
          준비 중
        </span>
      )}
    </button>
  );
}
