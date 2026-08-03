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
import { NewsRail } from "@/components/news/NewsRail";
import type { AttachedNews } from "@/lib/news/attach";

/**
 * 전 대회 통합 타임라인 — 날짜순 하나의 흐름.
 *   · 경기 간격 = 세로 여백(GapMarker)  · 대회 = 색 뱃지  · 승/무/패 = 이중 부호화
 *   · 밀집 구간 = 앰버 브래킷(시작 배너 → 세로 레일 → 끝 캡)
 *   · activeHighlight 가 있으면 해당 성격의 경기만 강조하고 나머지는 흐린다.
 *
 * ## 소식 층
 * `news`가 있으면 각 경기 **직전 구간**에 그 시기 헤드라인을 얹는다. 밀집 구간 안이면
 * 앰버 레일 안쪽에 놓여 "이 빡빡한 구간에 무슨 말이 있었나"가 한눈에 붙는다.
 *
 * ⚠️ **배치이지 인과가 아니다.** 소식은 진단 문장·근거 카드·AI 프롬프트 어디에도
 * 들어가지 않는다. 색을 쓰지 않고 점선 안에 넣는 것도 그 구분을 눈으로 유지하기 위해서다.
 *
 * ## 데이터가 적을 때
 * 지금 붙어 있는 실데이터는 팀당 몇 경기뿐이다. 그때 화면이 "밀집 구간 없음"만 보여
 * 주면 사용자는 **일정이 여유로웠다**고 읽는다. 사실은 판정할 만큼의 경기가 없는
 * 것이다. 그래서 머리말에 판정 상태를 한 줄로 밝히고, 꼬리말에는 이 목록이 어디서
 * 끝나는지를 적는다. 비어 있는 화면보다 "왜 비었는지 아는 화면"이 낫다.
 */
export function Timeline({
  timeline,
  activeHighlight,
  news,
}: {
  timeline: TimelineVM;
  activeHighlight: HighlightTag | null;
  news?: AttachedNews | null;
}) {
  const spanById = new Map(timeline.spans.map((s) => [s.id, s]));

  const emphasisOf = (tags: HighlightTag[]): Emphasis => {
    if (!activeHighlight) return "none";
    return tags.includes(activeHighlight) ? "on" : "dim";
  };

  if (timeline.rows.length === 0) {
    return <EmptySchedule excluded={timeline.excludedCount} />;
  }

  return (
    <div className="flex flex-col">
      {/* 헤더 + 범례 */}
      <div className="mb-3.5 flex items-center justify-between gap-4">
        <div className="min-w-0 text-xs font-extrabold tracking-wider text-text-low">
          리그 · 컵 · 유럽대항전 통합 — 간격이 곧 일정
        </div>
        <div className="flex shrink-0 gap-1.5">
          {timeline.competitionsPresent.map((c) => (
            <CompetitionBadge key={c.label} competition={c} />
          ))}
        </div>
      </div>

      {/* 밀집 판정 상태 — 구간이 0개일 때 "여유로움"과 "판정 불가"를 가른다 */}
      <div
        className={cn(
          "mb-4 flex items-center gap-2.5 rounded-card border px-3.5 py-2.5",
          timeline.congestion.detectable
            ? "border-line bg-panel"
            : "border-dashed border-line-dashed bg-transparent",
        )}
      >
        <span className="shrink-0 text-[11px] font-extrabold tracking-wider text-text-low">
          밀집 판정
        </span>
        <span className="text-[13px] font-bold text-text-mid">
          {timeline.congestion.note}
        </span>
      </div>

      {/* 경기 흐름 */}
      {timeline.rows.map((row) => {
        const cong = row.congestion;
        const span = cong ? spanById.get(cong.spanId) : undefined;
        // 간격: 밀집 구간 내부 전이면 레일 안, 아니면(휴식/시작 진입) 레일 밖.
        const gapInRail = !!cong && cong.pos !== "start";
        const rowNews = news?.byRowId.get(row.id) ?? [];

        return (
          <Fragment key={row.id}>
            {row.gap && !gapInRail && <GapMarker gap={row.gap} />}

            {cong && span ? (
              <div className="ml-0.5 border-l-[3px] border-warn pl-2.5">
                {gapInRail && row.gap && <GapMarker gap={row.gap} inRail />}
                {cong.pos === "start" && <CongestionBanner span={span} />}
                <NewsRail items={rowNews} />
                <MatchCard
                  row={row}
                  emphasis={emphasisOf(row.tags)}
                  accent={activeHighlight}
                />
                {cong.pos === "end" && <CongestionEndCap span={span} />}
              </div>
            ) : (
              <>
                <NewsRail items={rowNews} />
                <MatchCard
                  row={row}
                  emphasis={emphasisOf(row.tags)}
                  accent={activeHighlight}
                />
              </>
            )}
          </Fragment>
        );
      })}

      {news && news.trailing.length > 0 && (
        <NewsRail items={news.trailing} compact />
      )}

      <TimelineTail
        upcoming={timeline.upcomingCount}
        excluded={timeline.excludedCount}
      />
    </div>
  );
}

/**
 * 목록의 끝 — 여기가 "시즌의 끝"인지 "우리가 가진 데이터의 끝"인지 구분해 적는다.
 * 둘을 뭉뚱그리면 동기화가 덜 된 상태를 사용자가 사실로 읽는다.
 */
function TimelineTail({
  upcoming,
  excluded,
}: {
  upcoming: number;
  excluded: number;
}) {
  const headline =
    upcoming > 0
      ? `예정 ${upcoming}경기 — 킥오프 전까지 예측을 남길 수 있습니다`
      : "여기까지가 동기화된 일정입니다";

  return (
    <div className="mt-4 flex flex-wrap items-center gap-3.5 rounded-card border border-dashed border-line-dashed px-4 py-3.5">
      <span className="rounded-badge bg-volt/10 px-2 py-1 font-display text-[13px] font-black text-volt">
        {upcoming > 0 ? "NEXT" : "END"}
      </span>
      <span className="flex-1 text-sm font-bold text-text-hi">{headline}</span>
      {excluded > 0 && (
        <span className="text-[13px] font-extrabold text-text-mid">
          연기·취소 {excluded}경기는 일정에서 제외
        </span>
      )}
    </div>
  );
}

function EmptySchedule({ excluded }: { excluded: number }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-panel border border-dashed border-line-dashed px-6 py-16 text-center">
      <div className="font-display text-[40px] font-black leading-none text-text-low">
        각
      </div>
      <div className="text-sm font-bold text-text-hi">
        이 팀의 경기가 아직 없습니다
      </div>
      <div className="max-w-md text-[13px] leading-relaxed text-text-mid">
        백엔드가 동기화한 경기 중 이 팀이 뛴 경기가 없습니다.
        {excluded > 0 && ` (연기·취소로 제외된 경기 ${excluded}건)`}
        <br />
        동기화를 한 번 돌려 보세요 —{" "}
        <code className="rounded-badge bg-panel px-1.5 py-0.5 font-display text-[12px] text-volt">
          POST /api/admin/sync
        </code>
      </div>
    </div>
  );
}
