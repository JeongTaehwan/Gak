import type { Form, Period } from "@/lib/timeline/types";
import { ResultChip } from "@/components/timeline/ResultChip";

/**
 * 스트릭에 칩으로 그리는 최대 경기 수. 넘치면 최근 것만 그리고 그 사실을 적는다.
 *
 * 기간이 시즌 전체가 되면서 폼이 52경기까지 늘었다. 전부 그리면 헤더 한 줄을 넘겨
 * 팀 이름을 밀어낸다 — 칩 하나가 24px + 여백이라 10개가 헤더가 감당하는 한계다.
 */
const MAX_CHIPS = 10;

/**
 * 폼 스트릭(날짜 오름차순) — **진단 기간 전체**의 확정 경기.
 *
 * 라벨의 N은 **실제로 센 경기 수**다. 기간이 52경기여도 확정된 경기가 2건뿐이면 "2경기"라고
 * 적는다 — 칩 두 개만 그려 놓고 52경기라고 적으면 사용자는 나머지 50을 "무승부"나
 * "데이터 누락"으로 각자 상상하게 된다. 예정 경기를 D로 채워 칸을 맞추는 건 더 나쁘다
 * (폼 자체가 거짓이 된다).
 *
 * <p>칩이 너무 많으면 헤더 한 줄에 들어가지 않아 최근 {@link MAX_CHIPS}개만 그린다.
 * 그때도 **숫자는 기간 전체 것**이고, 잘렸다는 사실을 라벨에 적는다 — 화면이 좁아서 덜
 * 그렸을 뿐 폼이 그만큼만 계산된 건 아니다.
 */
export function FormStreak({ form, period }: { form: Form; period?: Period }) {
  if (form.sampleSize === 0) {
    return (
      <div className="flex flex-col items-end gap-1">
        <span className="text-[10px] font-extrabold tracking-widest text-text-low">
          폼
        </span>
        <span className="text-[13px] font-bold text-text-mid">
          {period && period.upcomingMatches > 0 && period.analyzedMatches === 0
            ? "아직 치른 경기 없음"
            : "확정된 경기 없음"}
        </span>
      </div>
    );
  }

  const shown = form.recent.slice(-MAX_CHIPS);
  const trimmed = form.recent.length - shown.length;

  return (
    <div className="flex flex-col items-end gap-1">
      <span className="text-[10px] font-extrabold tracking-widest text-text-low">
        {period?.seasonLabel ? `${period.seasonLabel} ` : ""}
        {form.sampleSize}경기
        {trimmed > 0 && ` (최근 ${shown.length}만 표시)`}
      </span>
      <div className="flex gap-1">
        {shown.map((r, i) => (
          <ResultChip key={i} result={r} size={24} />
        ))}
      </div>
      <span className="text-[11px] font-bold text-text-mid">{form.summary}</span>
    </div>
  );
}
