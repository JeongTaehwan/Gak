import type { Form } from "@/lib/timeline/types";
import { ResultChip } from "@/components/timeline/ResultChip";

/**
 * 최근 폼 스트릭(날짜 오름차순).
 *
 * 라벨에 "최근 N경기"의 N을 **실제로 센 경기 수**로 적는다. 요청은 6경기였는데 확정된
 * 경기가 2건뿐이라면 "최근 6경기"라고 쓰면 안 된다 — 칩 두 개만 그려 놓고 6경기라고
 * 적으면, 사용자는 나머지 넷을 "무승부"나 "데이터 누락"으로 각자 상상하게 된다.
 * 예정 경기를 D로 채워 여섯 칸을 맞추는 건 더 나쁘다(폼 자체가 거짓이 된다).
 */
export function FormStreak({ form }: { form: Form }) {
  if (form.sampleSize === 0) {
    return (
      <div className="flex flex-col items-end gap-1">
        <span className="text-[10px] font-extrabold tracking-widest text-text-low">
          최근 폼
        </span>
        <span className="text-[13px] font-bold text-text-mid">
          확정된 경기 없음
        </span>
      </div>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1">
      <span className="text-[10px] font-extrabold tracking-widest text-text-low">
        최근 {form.sampleSize}경기
        {form.sampleSize < form.requested && ` (요청 ${form.requested})`}
      </span>
      <div className="flex gap-1">
        {form.recent.map((r, i) => (
          <ResultChip key={i} result={r} size={24} />
        ))}
      </div>
      <span className="text-[11px] font-bold text-text-mid">{form.summary}</span>
    </div>
  );
}
