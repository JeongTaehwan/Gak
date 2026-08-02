import type { CongestionSpan } from "@/lib/timeline/types";

/** 밀집 구간 시작 배너 — 앰버 밴드에 기간·경기수 요약. */
export function CongestionBanner({ span }: { span: CongestionSpan }) {
  return (
    <div className="mt-1 mb-2.5 flex flex-wrap items-center gap-2.5 rounded-card border border-warn/40 bg-warn/8 px-3.5 py-2.5">
      <span className="font-display text-[13px] font-black text-warn">
        ⚠ 밀집 구간 · {span.fromLabel}–{span.toLabel}
      </span>
      <span className="text-[13px] font-bold text-text-hi">{span.summary}</span>
    </div>
  );
}

/** 밀집 구간 끝 캡. */
export function CongestionEndCap({ span }: { span: CongestionSpan }) {
  return (
    <div className="mt-1.5">
      <span className="font-display text-[11px] font-extrabold text-warn">
        밀집 구간 끝 — {span.summary}
      </span>
    </div>
  );
}
