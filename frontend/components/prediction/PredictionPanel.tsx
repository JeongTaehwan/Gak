import type { Timeline as TimelineVM } from "@/lib/timeline/types";

/**
 * 예측 · 적중 기록 화면 — **아직 못 만든다.** 그 이유를 화면이 직접 말한다.
 *
 * 빈 화면이나 "준비 중" 한 줄로 두지 않는 이유: 여기서 막힌 건 UI를 안 만들어서가
 * 아니라 **예측을 만들 수 있는 경기가 없어서**다. 이 앱의 핵심 규칙이 "예측은 킥오프
 * 이전에만"인데, 지금 DB의 경기가 전부 과거라 어떤 경기에도 예측을 걸 수 없다.
 * "준비 중"이라고만 적으면 화면만 붙이면 되는 것처럼 읽히고, 그건 사실이 아니다.
 *
 * 규칙 자체는 살아 있다 — `POST /api/predictions`를 지금 호출하면 400이 돌아온다.
 * 우회로(과거 시각 주입 등)는 절대 만들지 않는다. 그 규칙이 이 앱의 존재 이유다.
 */
export function PredictionPanel({ timeline }: { timeline: TimelineVM }) {
  const { upcomingCount } = timeline;

  return (
    <div className="flex flex-col gap-5">
      <div className="rounded-panel border border-line-strong bg-card p-6">
        <div className="mb-2.5 text-[11px] font-black tracking-[2px] text-volt">
          예측 · 적중 기록
        </div>
        <div className="font-display text-[26px] font-black leading-[1.3] tracking-tight text-text-hi">
          {upcomingCount > 0
            ? `예정된 경기 ${upcomingCount}건 — 화면은 아직 준비 중`
            : "예측을 남길 수 있는 경기가 없다"}
        </div>
        <p className="mt-2.5 text-sm leading-relaxed text-text-mid">
          {upcomingCount > 0
            ? "예측을 남길 경기는 있는데 입력·집계 화면이 아직 없다. 아래 남은 작업을 참고."
            : "이 앱은 예측을 킥오프 이전에만 받는다. 그래야 적중률이 정직해진다. 지금 동기화된 경기가 전부 과거라 걸 수 있는 경기가 하나도 없다."}
        </p>
      </div>

      <section className="flex flex-col gap-2.5">
        <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
          무엇이 있고 무엇이 없나
        </h2>
        <Row
          done
          label="예측 생성 API"
          detail="POST /api/predictions — 킥오프 이전 검증, 그 경기에 뛰는 팀인지 검증 포함"
        />
        <Row done label="킥오프 이전 규칙" detail="서버 시계로만 판정. 위반 시 400" />
        <Row
          done
          label="채점"
          detail="매시 30분에 끝난 경기를 채점한다(동기화 20분 뒤). 몇 번을 돌려도 결과가 같다"
        />
        <Row label="적중률 집계 · 조회 API" detail="예측 목록과 적중률을 읽을 통로가 아직 없다" />
        <Row label="입력 · 기록 화면" detail="위가 있어야 그릴 것이 생긴다" />
        <Row
          label="미래 경기 데이터"
          detail="현재 시즌을 동기화해야 예측을 걸 수 있는 경기가 생긴다"
        />
      </section>
    </div>
  );
}

function Row({
  label,
  detail,
  done = false,
}: {
  label: string;
  detail: string;
  done?: boolean;
}) {
  return (
    <div className="flex items-center gap-4 rounded-card border border-line bg-panel px-5 py-3.5">
      <span
        className={
          done
            ? "shrink-0 rounded-badge bg-win/15 px-2 py-1 text-[11px] font-black text-win"
            : "shrink-0 rounded-badge border border-line-dashed px-2 py-1 text-[11px] font-black text-text-low"
        }
      >
        {done ? "있음" : "없음"}
      </span>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-sm font-extrabold text-text-hi">{label}</span>
        <span className="text-[13px] leading-relaxed text-text-mid">{detail}</span>
      </span>
    </div>
  );
}
