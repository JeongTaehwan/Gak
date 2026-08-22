import { Fragment, useState } from "react";
import { cn } from "@usetaehwan/ui";
import type {
  HighlightTag,
  Timeline as TimelineVM,
  TimelineRow,
} from "@/lib/timeline/types";
import { REASON_LABEL } from "@/lib/diagnosis/summarize";
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
  // 선택된 경기 — 결장 명단·사유 갈래를 펼쳐 보여 준다(TL 요구사항 5절).
  const [selectedId, setSelectedId] = useState<number | null>(null);

  const emphasisOf = (tags: HighlightTag[]): Emphasis => {
    if (!activeHighlight) return "none";
    return tags.includes(activeHighlight) ? "on" : "dim";
  };

  if (timeline.rows.length === 0) {
    // 리그만 보기에서 비었다 = 이 시즌 자국 리그 경기가 없다(컵만 있는 방어 경로).
    // "경기 없음"으로 뭉뚱그리면 전 대회 보기에 있는 경기가 사라진 것처럼 읽힌다.
    //
    // ⚠️ 수집 전 대회 안내를 **여기서도** 낸다. 빈 화면에서 이 줄을 빼면 "아직 받지
    // 않았다"가 "원래 없다"로 읽힌다 — 경기가 하나도 없을 때가 그 오해가 가장 크게
    // 벌어지는 자리다.
    return (
      <div className="flex flex-col gap-4">
        <PendingSyncNote competitions={timeline.pendingCompetitions} />
        {timeline.scope === "league" ? (
          <EmptyLeagueSchedule
            pending={timeline.pendingCompetitions.length > 0}
          />
        ) : (
          <EmptySchedule
            excluded={timeline.excludedCount}
            pending={timeline.pendingCompetitions.length > 0}
          />
        )}
      </div>
    );
  }

  const scopeLabel =
    timeline.scope === "league" ? "자국 리그만" : "리그 · 컵 · 유럽대항전 통합";

  return (
    <div className="flex flex-col">
      {/* 헤더 + 분모 + 범례 — 분모(전체 중 몇 경기 표시)는 보기 기준을 따라 전환된다 */}
      <div className="mb-3.5 flex items-start justify-between gap-4">
        <div className="min-w-0">
          <div className="text-xs font-extrabold tracking-wider text-text-low">
            {scopeLabel} — {timeline.period.label}
          </div>
          {/*
            분모를 "시즌 전체"라고 부르지 않는다. 아직 수집하지 않은 대회가 있으면
            우리가 가진 경기 수는 시즌 전체가 아니라 **받아 둔 것 전부**다. 그걸
            전체라고 적으면 아래 "수집 전" 안내가 따로 있어도 이 줄 자체가 없는
            확신을 주장하게 된다(부분합을 전체처럼).
          */}
          <div className="mt-1 text-[11px] font-bold text-text-mid">
            {timeline.pendingCompetitions.length > 0 ? "받아 둔" : "시즌 전체"}{" "}
            {timeline.period.seasonMatches}경기 중 {timeline.rows.length}경기 표시
            {timeline.congestion.medianGapDays != null && (
              <> · 간격 중앙값 {timeline.congestion.medianGapDays}일</>
            )}
          </div>
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

      {/*
        수집 전 대회 안내 — (대회, 시즌) 동기화 이력이 근거다. 백엔드는 존재하지 않는
        경기를 개별적으로 알 수 없으므로 특정 날짜에 마커를 찍지 않고 대회 단위로만
        말한다. 이 안내가 없으면 미수집이 만든 공백이 "여유 구간"으로 읽힌다.
      */}
      <div className="mb-4 empty:mb-0">
        <PendingSyncNote competitions={timeline.pendingCompetitions} />
      </div>

      {/* 경기 흐름 */}
      {timeline.rows.map((row, i) => {
        const cong = row.congestion;
        // 진단이 끊긴 자리. 여기 아래로는 화면에는 보이지만 어떤 지표에도 안 들어간다.
        const boundary =
          !row.inAnalysis && (i === 0 || timeline.rows[i - 1].inAnalysis);
        const span = cong ? spanById.get(cong.spanId) : undefined;
        // 간격: 밀집 구간 내부 전이면 레일 안, 아니면(휴식/시작 진입) 레일 밖.
        const gapInRail = !!cong && cong.pos !== "start";
        const rowNews = news?.byRowId.get(row.id) ?? [];
        const selected = selectedId === row.id;

        // 경기를 선택하면 결장 명단·사유 갈래를 펼친다. 그 외 상세는 이번 범위 밖이다.
        const card = (
          <>
            <div
              role="button"
              tabIndex={0}
              aria-expanded={selected}
              className="cursor-pointer"
              onClick={() => setSelectedId(selected ? null : row.id)}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.preventDefault();
                  setSelectedId(selected ? null : row.id);
                }
              }}
            >
              <MatchCard
                row={row}
                emphasis={emphasisOf(row.tags)}
                accent={activeHighlight}
              />
            </div>
            {selected && (
              <AbsenceDetail row={row} synced={timeline.absences.synced} />
            )}
          </>
        );

        return (
          <Fragment key={row.id}>
            {boundary && timeline.period.analyzedMatches > 0 && (
              <AnalysisBoundary period={timeline.period} />
            )}
            {row.gap && !gapInRail && <GapMarker gap={row.gap} />}

            {cong && span ? (
              <div className="ml-0.5 border-l-[3px] border-warn pl-2.5">
                {gapInRail && row.gap && <GapMarker gap={row.gap} inRail />}
                {cong.pos === "start" && <CongestionBanner span={span} />}
                <NewsRail items={rowNews} />
                {card}
                {cong.pos === "end" && <CongestionEndCap span={span} />}
              </div>
            ) : (
              <>
                <NewsRail items={rowNews} />
                {card}
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
 * "여기까지 치렀다" — 진단이 끊기는 자리.
 *
 * 이 선이 없으면 아래의 예정 경기들이 위 카드의 숫자에 들어갔다고 읽힌다. 목록에는
 * 남기고 계산에서만 뺀 것이라, **뺐다는 사실을 화면이 말해야** 목록과 지표가 어긋나
 * 보이지 않는다. 밀집 브래킷이 여기서 끊기는 것도 같은 이유다.
 */
function AnalysisBoundary({ period }: { period: TimelineVM["period"] }) {
  return (
    <div className="my-2 flex items-center gap-3">
      <span className="rounded-badge border border-dashed border-line-dashed px-2 py-0.5 text-[10px] font-black tracking-wider text-text-low">
        여기까지 치름 · {period.analyzedMatches}경기
      </span>
      <span className="h-px flex-1 bg-line-dashed" />
      <span className="text-[11px] font-bold text-text-low">
        아래 {period.upcomingMatches}경기는 진단에 들어가지 않습니다
      </span>
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

/**
 * 선택한 경기의 결장 상세 — 명단과 사유 갈래만(이번 범위의 경기 상세는 이것뿐이다).
 *
 * **행이 없다는 사실만으로는 "0명"과 "모름"을 가를 수 없다.** 둘 다 행이 없기 때문이다.
 * 그래서 (팀, 시즌) 수집 이력(`synced`)을 함께 보고 세 가지를 갈라 적는다.
 *
 *   · 명단 있음        → 그대로 보여 준다
 *   · 행 없음 + 미수집  → "아직 받지 않았습니다"
 *   · 행 없음 + 수집함  → "받아 왔지만 이 경기는 포함되지 않았습니다"
 *
 * 마지막 경우를 "결장 0명"으로 적지 않는 이유: API는 한 시즌 요청에도 일부 경기만 준다
 * (맨유 2023 시즌 52경기 중 44경기). 수집 성공은 "이 팀·시즌을 받아 왔다"까지만 보장하고,
 * 그 경기에 아무도 안 빠졌다는 뜻이 아니다.
 */
function AbsenceDetail({
  row,
  synced,
}: {
  row: TimelineRow;
  synced: boolean;
}) {
  return (
    <div className="mb-1.5 ml-[46px] rounded-card border border-dashed border-line-dashed bg-panel/60 px-4 py-2.5">
      <div className="mb-1 text-[10px] font-black tracking-wider text-text-low">
        결장 · {row.dateLabel} vs {row.opponent}
      </div>
      {row.absentees == null ? (
        <div className="text-[12px] font-bold text-text-mid">
          {synced
            ? "결장 데이터를 받아 왔지만 이 경기는 포함되지 않았습니다 — 0명이 아니라 모름입니다"
            : "이 팀·시즌의 결장 데이터를 아직 받지 않았습니다 — 0명이 아니라 모름입니다"}
        </div>
      ) : row.absentees.length === 0 ? (
        <div className="text-[12px] font-bold text-text-mid">
          결장 데이터 확인 — 확정 결장 없음
        </div>
      ) : (
        <ul className="flex flex-col gap-1">
          {row.absentees.map((p) => (
            <li
              key={p.playerId}
              className="flex items-baseline gap-2 text-[12px] font-bold text-text-hi"
            >
              <span>{p.playerName}</span>
              <span className="rounded-badge bg-loss/10 px-1.5 py-px text-[10px] font-extrabold text-loss">
                {REASON_LABEL[p.reason]}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/**
 * 수집 전 대회 안내 — (대회, 시즌) 동기화 이력이 근거다.
 *
 * 백엔드는 존재하지 않는 경기를 개별적으로 알 수 없으므로 특정 날짜에 마커를 찍지 않고
 * 대회 단위로만 말한다. 참가 여부도 단정하지 않는다 — 경기가 없으면 그 팀이 그 대회에
 * 나갔는지는 우리가 알 수 없는 사실이다.
 */
function PendingSyncNote({ competitions }: { competitions: string[] }) {
  if (competitions.length === 0) return null;
  return (
    <div className="rounded-card border border-dashed border-line-dashed px-3.5 py-2.5 text-[12px] font-bold text-text-mid">
      이 시즌 아직 수집하지 않은 대회 {competitions.length}개 —{" "}
      <span className="text-text-hi">{competitions.join(" · ")}</span>
      <span className="text-text-low">
        {" "}
        · 이 팀이 참가했다면 그 경기는 이 타임라인에 없습니다
      </span>
    </div>
  );
}

/**
 * 리그만 보기가 비었을 때 — 컵 경기만 있는 시즌의 방어 문구(requirements TL 4절).
 *
 * 수집 전 대회가 있으면 "없습니다"라고 단정하지 않는다. 미수집과 실제 부재는 다른
 * 사실이고, 둘을 같은 문장으로 덮으면 화면이 받지 못한 데이터를 없는 것으로 말한다.
 */
function EmptyLeagueSchedule({ pending }: { pending: boolean }) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-panel border border-dashed border-line-dashed px-6 py-16 text-center">
      <div className="text-sm font-bold text-text-hi">
        {pending
          ? "받아 둔 자국 리그 경기가 없습니다"
          : "이 시즌 자국 리그 경기가 없습니다"}
      </div>
      <div className="max-w-md text-[13px] leading-relaxed text-text-mid">
        {pending
          ? "위 대회들이 아직 수집 전이라, 리그 일정이 원래 없는 것인지 아직 받지 못한 것인지 판정할 수 없습니다."
          : "전 대회 보기로 전환하면 수집된 경기(컵·유럽대항전)를 볼 수 있습니다."}
      </div>
    </div>
  );
}

function EmptySchedule({
  excluded,
  pending,
}: {
  excluded: number;
  pending: boolean;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-panel border border-dashed border-line-dashed px-6 py-16 text-center">
      <div className="font-display text-[40px] font-black leading-none text-text-low">
        각
      </div>
      <div className="text-sm font-bold text-text-hi">
        {pending
          ? "이 팀의 경기를 아직 받지 못했습니다"
          : "이 팀의 경기가 아직 없습니다"}
      </div>
      <div className="max-w-md text-[13px] leading-relaxed text-text-mid">
        {pending
          ? "위 대회들이 아직 수집 전입니다 — 경기가 없는 것인지 아직 받지 못한 것인지 판정할 수 없습니다."
          : "백엔드가 동기화한 경기 중 이 팀이 뛴 경기가 없습니다."}
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
