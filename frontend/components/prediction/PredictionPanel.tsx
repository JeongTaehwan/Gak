import { cn } from "@usetaehwan/ui";
import type {
  Pick as PickValue,
  PredictionAccuracy,
  PredictionRecord,
} from "@/lib/api/types";
import type { Timeline as TimelineVM } from "@/lib/timeline/types";
import { ResultChip } from "@/components/timeline/ResultChip";
import { CompetitionBadge } from "@/components/timeline/CompetitionBadge";
import { toDateLabel } from "@/lib/timeline/format";

/**
 * 예측 · 적중 기록.
 *
 * <h2>비율을 감추는 규칙</h2>
 * 표본이 5건 미만이면 서버가 `hitRate`를 null로 준다. 화면은 그 자리를 "3건 중 2건"처럼
 * 개수로 메운다. 적중률은 이 앱이 파는 숫자라 특히 그렇다 — 3번 맞히고 "적중률 100%"를
 * 띄우는 순간 앱이 스스로를 과장하기 시작한다.
 *
 * <h2>남은 시간을 보여 주는 이유</h2>
 * 기록마다 "킥오프 3일 전"이 찍힌다. 이 앱은 "킥오프 이전에 남긴 예측만 센다"는 규칙
 * 위에 서 있는데, 그 규칙은 보이지 않으면 믿을 이유가 없다. 서버가 "우리는 정직합니다"
 * 라고 말하는 것보다 매 줄에 근거가 찍혀 있는 편이 낫다.
 */
export function PredictionPanel({
  timeline,
  accuracy,
}: {
  timeline: TimelineVM;
  accuracy: PredictionAccuracy;
}) {
  const hasAny = accuracy.scored > 0 || accuracy.pending > 0;

  return (
    <div className="flex flex-col gap-5">
      <AccuracyHeadline accuracy={accuracy} upcoming={timeline.upcomingCount} />

      {accuracy.scored > 0 && <ByPick accuracy={accuracy} />}

      {hasAny ? (
        <section className="flex flex-col gap-2.5">
          <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
            예측 기록 — 최근순 · 남긴 시각과 킥오프까지의 여유를 함께 적는다
          </h2>
          {accuracy.recent.map((r) => (
            <RecordRow key={r.predictionId} record={r} />
          ))}
        </section>
      ) : (
        <MissingPieces upcoming={timeline.upcomingCount} />
      )}
    </div>
  );
}

function AccuracyHeadline({
  accuracy,
  upcoming,
}: {
  accuracy: PredictionAccuracy;
  upcoming: number;
}) {
  const { scored, hits, hitRate, pending } = accuracy;

  const headline =
    scored === 0
      ? upcoming > 0
        ? `예정된 경기 ${upcoming}건 — 아직 남긴 예측이 없다`
        : "예측을 남길 수 있는 경기가 없다"
      : hitRate == null
        ? `${scored}번 중 ${hits}번 적중`
        : `적중률 ${Math.round(hitRate * 100)}%`;

  const sub =
    scored === 0
      ? upcoming > 0
        ? "예측은 킥오프 이전에만 남길 수 있다. 그래야 적중률이 정직해진다."
        : "이 앱은 예측을 킥오프 이전에만 받는다. 지금 동기화된 경기가 전부 과거라 걸 수 있는 경기가 하나도 없다."
        : hitRate == null
          ? `표본이 ${scored}건이라 비율은 내지 않는다 — ${scored}건으로 퍼센트를 적으면 한 번이 전체를 흔든다.`
          : `${scored}번 채점 · ${hits}번 적중 · ${accuracy.misses}번 빗나감.`;

  return (
    <div className="rounded-panel border border-line-strong bg-card p-6">
      <div className="mb-2.5 flex flex-wrap items-center gap-2">
        <span className="text-[11px] font-black tracking-[2px] text-volt">
          예측 · 적중 기록
        </span>
        {scored > 0 && (
          <span className="rounded-badge border border-line-strong px-2 py-0.5 text-[10px] font-black tracking-wider text-text-low">
            표본 {scored}건
          </span>
        )}
        {pending > 0 && (
          <span className="rounded-badge bg-line/60 px-2 py-0.5 text-[10px] font-black tracking-wider text-text-mid">
            채점 대기 {pending}
          </span>
        )}
      </div>
      <div className="font-display text-[30px] font-black leading-[1.25] tracking-tight text-text-hi">
        {headline}
      </div>
      <p className="mt-2.5 text-sm leading-relaxed text-text-mid">{sub}</p>
    </div>
  );
}

const PICK_LABEL: Record<PickValue, string> = { W: "승", D: "무", L: "패" };

/** 예측값별 성적 — 전체 적중률 하나로는 "무승부를 못 맞힌다" 같은 편향이 안 보인다. */
function ByPick({ accuracy }: { accuracy: PredictionAccuracy }) {
  const picks: PickValue[] = ["W", "D", "L"];
  return (
    <section className="flex flex-col gap-2.5">
      <h2 className="text-xs font-extrabold tracking-[1.5px] text-text-low">
        무엇을 골랐을 때 맞았나
      </h2>
      <div className="grid gap-2.5 sm:grid-cols-3">
        {picks.map((p) => {
          const a = accuracy.byPick[p];
          return (
            <div
              key={p}
              className="flex items-center gap-3 rounded-card border border-line bg-panel px-4 py-3"
            >
              <ResultChip result={p} size={26} />
              <div className="flex min-w-0 flex-col">
                <span className="text-sm font-extrabold text-text-hi">
                  {PICK_LABEL[p]} 예측
                </span>
                <span className="text-[12px] text-text-mid">
                  {!a || a.predicted === 0
                    ? "고른 적 없음"
                    : a.hitRate == null
                      ? `${a.predicted}번 중 ${a.hits}번 적중`
                      : `${a.predicted}번 · 적중률 ${Math.round(a.hitRate * 100)}%`}
                </span>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
}

/** "3일 2시간 전" — 킥오프까지 얼마나 남기고 예측했나. */
function toLeadTime(minutes: number): string {
  if (minutes < 60) return `${minutes}분 전`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}시간 전`;
  const days = Math.floor(hours / 24);
  const rest = hours % 24;
  return rest === 0 ? `${days}일 전` : `${days}일 ${rest}시간 전`;
}

function RecordRow({ record: r }: { record: PredictionRecord }) {
  const scored = r.isHit !== null;
  return (
    <div
      className={cn(
        "flex flex-wrap items-center gap-3.5 rounded-card border px-4 py-3",
        scored
          ? r.isHit
            ? "border-win/40 bg-win/[0.06]"
            : "border-loss/35 bg-card"
          : "border-dashed border-line-dashed bg-card",
      )}
    >
      <span className="w-[46px] shrink-0 font-display text-[15px] font-extrabold text-text-hi">
        {toDateLabel(r.kickoff)}
      </span>
      <CompetitionBadge
        competition={{
          key: "league",
          label: r.competitionShortName,
          fullLabel: r.competitionName,
        }}
      />
      <span className="w-[26px] shrink-0 font-display text-[11px] font-extrabold text-text-mid">
        {r.home ? "H" : "A"}
      </span>
      <span className="min-w-0 flex-1 truncate text-sm font-bold text-text-hi">
        {r.opponentName}
      </span>

      {/* 규칙이 지켜졌다는 증거 — 항상 킥오프 이전이다 */}
      <span className="shrink-0 text-[11px] font-bold text-text-low">
        킥오프 {toLeadTime(r.leadTimeMinutes)} 예측
      </span>

      <span className="flex shrink-0 items-center gap-1.5">
        <ResultChip result={r.pick} size={22} />
        <span className="text-[11px] font-bold text-text-low">→</span>
        <ResultChip result={r.resolvedResult} size={22} />
      </span>

      <span
        className={cn(
          "w-[52px] shrink-0 text-right text-xs font-extrabold",
          !scored ? "text-text-low" : r.isHit ? "text-win" : "text-loss",
        )}
      >
        {!scored ? "채점 전" : r.isHit ? "적중" : "빗나감"}
      </span>
    </div>
  );
}

/**
 * 예측 기록이 하나도 없을 때 — 여기서 막힌 게 UI가 아니라 데이터라는 걸 밝힌다.
 * "준비 중"이라고만 적으면 화면만 붙이면 되는 것처럼 읽히는데 사실이 아니다.
 */
function MissingPieces({ upcoming }: { upcoming: number }) {
  return (
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
      <Row
        done
        label="적중률 집계 · 조회 API"
        detail="GET /api/teams/{teamId}/predictions — 표본이 5건 미만이면 비율을 감춘다"
      />
      <Row
        label="예측 입력 UI"
        detail={
          upcoming > 0
            ? "예정 경기가 있으니 입력 폼만 붙이면 된다"
            : "걸 수 있는 경기가 생긴 뒤에 붙인다 — 지금 만들면 누를 수 없는 버튼이다"
        }
      />
      <Row
        label="미래 경기 데이터"
        detail="현재 시즌을 동기화해야 예측을 걸 수 있는 경기가 생긴다 (개막 체크리스트 1·2번)"
      />
    </section>
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
