import type { MatchResult } from "@/lib/timeline/types";
import { ResultChip } from "@/components/timeline/ResultChip";

/** 최근 N경기 결과 스트릭(날짜 오름차순). */
export function FormStreak({ form }: { form: MatchResult[] }) {
  return (
    <div className="flex flex-col items-end gap-1">
      <span className="text-[10px] font-extrabold tracking-widest text-text-low">
        최근 {form.length}경기
      </span>
      <div className="flex gap-1">
        {form.map((r, i) => (
          <ResultChip key={i} result={r} size={24} />
        ))}
      </div>
    </div>
  );
}
