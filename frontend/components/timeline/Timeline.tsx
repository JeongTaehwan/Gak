import { Fragment } from "react";
import { cn } from "@usetaehwan/ui";
import type { HighlightTag, Timeline as TimelineVM } from "@/lib/timeline/types";
import { CompetitionBadge } from "@/components/timeline/CompetitionBadge";
import { MatchCard, type Emphasis } from "@/components/timeline/MatchCard";
import { GapMarker } from "@/components/timeline/GapMarker";
import {
  CongestionBanner,
  CongestionEndCap,
} from "@/components/timeline/CongestionBracket";

/**
 * 전 대회 통합 타임라인 — 날짜순 하나의 흐름.
 *   · 경기 간격 = 세로 여백(GapMarker)  · 대회 = 색 뱃지  · 승/무/패 = 이중 부호화
 *   · 밀집 구간 = 앰버 브래킷(시작 배너 → 세로 레일 → 끝 캡)
 *   · activeHighlight 가 있으면 해당 성격의 경기만 강조하고 나머지는 흐린다.
 */
export function Timeline({
  timeline,
  activeHighlight,
}: {
  timeline: TimelineVM;
  activeHighlight: HighlightTag | null;
}) {
  const spanById = new Map(timeline.spans.map((s) => [s.id, s]));

  const emphasisOf = (tags: HighlightTag[]): Emphasis => {
    if (!activeHighlight) return "none";
    return tags.includes(activeHighlight) ? "on" : "dim";
  };

  return (
    <div className="flex flex-col">
      {/* 헤더 + 범례 */}
      <div className="mb-3.5 flex items-center justify-between">
        <div className="text-xs font-extrabold tracking-wider text-text-low">
          리그 · 컵 · 유럽대항전 통합 — 간격이 곧 일정
        </div>
        <div className="flex gap-1.5">
          {timeline.competitionsPresent.map((c) => (
            <CompetitionBadge key={c.label} competition={c} />
          ))}
        </div>
      </div>

      {/* 경기 흐름 */}
      {timeline.rows.map((row) => {
        const cong = row.congestion;
        const span = cong ? spanById.get(cong.spanId) : undefined;
        // 간격: 밀집 구간 내부 전이면 레일 안, 아니면(휴식/시작 진입) 레일 밖.
        const gapInRail = !!cong && cong.pos !== "start";

        return (
          <Fragment key={row.id}>
            {row.gap && !gapInRail && <GapMarker gap={row.gap} />}

            {cong && span ? (
              <div className="ml-0.5 border-l-[3px] border-warn pl-2.5">
                {gapInRail && row.gap && <GapMarker gap={row.gap} inRail />}
                {cong.pos === "start" && <CongestionBanner span={span} />}
                <MatchCard
                  row={row}
                  emphasis={emphasisOf(row.tags)}
                  accent={activeHighlight}
                />
                {cong.pos === "end" && <CongestionEndCap span={span} />}
              </div>
            ) : (
              <MatchCard
                row={row}
                emphasis={emphasisOf(row.tags)}
                accent={activeHighlight}
              />
            )}
          </Fragment>
        );
      })}

      {/* 시즌 종료 — 실데이터가 완결된 시즌이라 예정 경기는 없다(정직하게 표기) */}
      <div
        className={cn(
          "mt-4 flex items-center gap-3.5 rounded-card border border-dashed border-line-dashed px-4 py-3.5",
        )}
      >
        <span className="rounded-badge bg-volt/10 px-2 py-1 font-display text-[13px] font-black text-volt">
          FIN
        </span>
        <span className="flex-1 text-sm font-bold text-text-hi">
          2023-24 시즌 종료 · FA컵 우승으로 마무리
        </span>
        <span className="text-[13px] font-extrabold text-text-mid">
          다음 시즌부터 예측·적중 추적
        </span>
      </div>
    </div>
  );
}
