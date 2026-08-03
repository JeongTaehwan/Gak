import { cn } from "@usetaehwan/ui";
import type { NewsLayerStatus } from "@/lib/news/attach";

/**
 * 소식이 안 보일 때 **왜 안 보이는지**를 적는 블록.
 *
 * ## 빈 화면이 버그로 보이면 안 된다
 * "소식 없음" 한 줄은 네 가지 다른 상황을 같은 말로 덮는다 — 아직 수집 전 / 보고 있는
 * 시즌과 시점이 다름 / 보관 기간 밖 / 정말 조용했음. 사용자가 할 수 있는 일도, 다음에
 * 기대할 것도 넷이 다르다.
 *
 * 지금(2026-08) 실제 상태는 **`season-mismatch`** 다. 타임라인은 2023-24 시즌 replay
 * 데이터이고 소식은 2026년 것이라 겹치는 구간이 없다. 이걸 "소식 없음"이라고만 적으면
 * 수집이 고장 난 것으로 읽힌다.
 *
 * 타임라인의 "밀집 판정" 블록이 <b>"여유로움"과 "판정 불가"를 가르는</b> 것과 같은 장치다.
 */
export function NewsLayerNote({ status }: { status: NewsLayerStatus }) {
  if (status.reason === "ok") return null;

  const waiting = status.reason !== "quiet";

  return (
    <div
      className={cn(
        "rounded-card border border-dashed border-line-dashed px-4 py-3.5",
        "bg-transparent",
      )}
    >
      <div className="flex items-center gap-2">
        <span className="rounded-badge border border-line-strong px-1.5 py-px text-[10px] font-black tracking-[0.14em] text-text-low">
          소식 층
        </span>
        <span className="text-[13px] font-extrabold text-text-hi">
          {status.headline}
        </span>
      </div>

      <p className="mt-2 text-[12px] leading-relaxed text-text-mid">
        {status.detail}
      </p>

      {(status.timelineLabel || status.coverageLabel) && (
        <div className="mt-2.5 flex flex-wrap gap-x-5 gap-y-1 text-[11px] font-bold text-text-low">
          {status.timelineLabel && (
            <span>
              보고 있는 일정{" "}
              <span className="tabular-nums text-text-mid">
                {status.timelineLabel}
              </span>
            </span>
          )}
          {status.coverageLabel && (
            <span>
              수집된 소식{" "}
              <span className="tabular-nums text-text-mid">
                {status.coverageLabel}
              </span>
            </span>
          )}
        </div>
      )}

      {waiting && (
        <p className="mt-2 text-[11px] font-bold text-text-low">
          RSS는 최근 기사만 제공합니다 — 지난 소식은 받을 수 없고, 수집을 계속
          돌려 앞으로 것을 쌓습니다.
        </p>
      )}
    </div>
  );
}
