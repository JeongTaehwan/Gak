// requirements.md 3·6장 — URL이 팀·고정된 시즌·탭을 기록한다
import type { Mode } from "@/components/layout/TeamHeader";

/**
 * URL에 담기는 화면 상태.
 *
 * <h2>⚠️ 형식은 아직 확정 전이다</h2>
 * `docs/open-questions.md` IN-OQ-08(등급 LATER)이 `[미정]`이다. 확정되면 **이 파일만**
 * 고치면 되도록 읽기·쓰기를 한곳에 모아 뒀다 — 링크를 만드는 곳과 읽는 곳이 각자
 * 규칙을 갖고 있으면 새로고침 복원과 공유 링크가 서로 다른 주소를 쓰게 된다.
 *
 * 지금은 가장 좁은 형태로 닫아 둔다: 기존 단일 경로 + 쿼리 파라미터 세 개.
 * `/?teamId=33&season=2023&tab=timeline`
 *
 * <h2>season 은 항상 적는다</h2>
 * 비워 두면 서버가 그때그때 자동 판정하므로, **같은 URL이 시간이 지나 다른 시즌을
 * 보여 준다.** 그래서 자동 판정이 끝나는 즉시 그 값을 URL에 고정한다 — 회고 링크를
 * 공유했는데 몇 달 뒤 다른 시즌이 열리는 일이 없어야 한다.
 */
/** 타임라인 보기 모드 — requirements.md TL 4절. 기본은 전 대회 보기다. */
export type TimelineView = "all" | "league";

export interface ScreenState {
  teamId: number;
  /** null이면 아직 자동 판정 전(첫 진입). 판정된 뒤에는 항상 적힌다. */
  season: number | null;
  tab: Mode;
  /**
   * 타임라인 보기 모드. URL에 기록해 공유·새로고침 시 복원한다 — "밀집 구간 3개를
   * 보라"고 공유한 링크가 받는 사람에게 리그만 보기(0개)로 열리면 안 된다.
   */
  view: TimelineView;
}

const TABS: Mode[] = ["timeline", "diagnosis", "prediction", "standings"];

/** 서버 컴포넌트가 받은 `searchParams` 를 상태로. 이해할 수 없는 값은 기본값으로 접는다. */
export function readScreenState(
  params: Record<string, string | string[] | undefined>,
  defaultTeamId: number,
): ScreenState {
  return {
    teamId: readInt(params.teamId) ?? defaultTeamId,
    season: readInt(params.season),
    tab: readTab(params.tab),
    view: readView(params.view),
  };
}

/** 상태 → 주소. 링크를 만드는 곳이 여기 하나뿐이어야 복원이 어긋나지 않는다. */
export function toHref(state: ScreenState): string {
  const params = new URLSearchParams();
  params.set("teamId", String(state.teamId));
  if (state.season != null) params.set("season", String(state.season));
  params.set("tab", state.tab);
  // 기본값(전 대회)은 적지 않는다 — 토글이 생기기 전의 링크와 같은 주소를 유지한다.
  if (state.view !== "all") params.set("view", state.view);
  return `/?${params.toString()}`;
}

function readInt(value: string | string[] | undefined): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw == null || !/^\d+$/.test(raw)) return null;
  return Number(raw);
}

/**
 * 모르는 탭 이름은 타임라인으로 접는다.
 *
 * 여기서만은 조용히 대체하는 게 맞다 — 탭은 "무엇을 보고 있나"일 뿐이라 잘못 접혀도
 * 사용자가 곧바로 알아차린다. 팀과 시즌은 반대다(숫자가 달라지는데 화면은 멀쩡하다).
 */
function readTab(value: string | string[] | undefined): Mode {
  const raw = Array.isArray(value) ? value[0] : value;
  return TABS.find((t) => t === raw) ?? "timeline";
}

/** 모르는 보기 이름은 기본(전 대회)으로 접는다 — 탭과 같은 이유로 여기서만 조용히 대체한다. */
function readView(value: string | string[] | undefined): TimelineView {
  const raw = Array.isArray(value) ? value[0] : value;
  return raw === "league" ? "league" : "all";
}
