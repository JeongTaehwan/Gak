/**
 * 백엔드 진단 API 호출 — 프론트가 데이터를 얻는 **유일한** 통로.
 *
 * 화면은 API-Football을 직접 부르지 않는다. 백엔드가 주기 동기화해 둔 우리 DB가
 * source of truth이고(무료 티어가 하루 100요청이라 이건 선택이 아니라 제약이다),
 * 밀집도·폼 같은 파생값도 백엔드가 계산해 내려준다.
 *
 * ## 목 데이터 전환
 *
 * 백엔드를 띄우지 않고 화면만 볼 때는 `.env.local`에 아래 한 줄을 넣는다.
 *
 * ```
 * GAK_DATA_SOURCE=mock
 * ```
 *
 * **백엔드가 죽었을 때 자동으로 목으로 넘어가지는 않는다.** 그러면 화면은 멀쩡해
 * 보이는데 보고 있는 숫자가 가짜인 상태가 되고, 그게 가장 눈치채기 어려운 실패다.
 * 전환은 사람이 명시적으로 한다. 백엔드가 안 뜨면 화면은 "안 뜬다"고 말한다.
 *
 * ※ `GAK_DATA_SOURCE`에 `NEXT_PUBLIC_` 접두어를 붙이지 않은 건 의도다. 이 모듈은
 *   서버 컴포넌트에서만 불리므로 브라우저 번들에 값이 실릴 이유가 없다.
 */
import type {
  PredictionAccuracy,
  StandingsTable,
  TeamDiagnostics,
  TeamNewsResponse,
  TeamSelection,
} from "@/lib/api/types";

/**
 * 맨체스터 유나이티드. API-Football 팀 id를 그대로 쓴다.
 *
 * ⚠️ **선택 가능 팀 목록의 출처가 아니다.** 목록은 서버가 조회 시즌에서 파생 계산해
 * 내려준다(`getTeamSelection`). 이 상수는 URL에 팀이 없을 때의 <b>첫 진입 기본값</b>일
 * 뿐이고, 그 팀이 그 시즌의 선택 대상인지는 서버가 따로 답한다.
 */
export const MANCHESTER_UNITED_ID = 33;

export interface DiagnosticsQuery {
  teamId: number;
  /** 밀집 판정 창 폭(일). 서버 기본값 14. */
  windowDays?: number;
  /** 그 창 안에 몇 경기부터 밀집으로 볼지. 서버 기본값 5. */
  minMatches?: number;
  /**
   * 볼 시즌(API 시즌 번호 = 시작 연도). 생략하면 서버가 고른다 —
   * **치른 경기가 있는 최신 시즌.** 기간 선택 UI가 붙으면 이 값을 넘긴다.
   */
  season?: number;
}

/** 데이터 출처 — 화면이 "지금 보고 있는 게 무엇인지" 표시할 수 있도록 함께 돌려준다. */
export type DataSource = "backend" | "mock";

export interface DiagnosticsResult {
  diagnostics: TeamDiagnostics;
  source: DataSource;
}

/** 백엔드에 닿지 못했다. 화면은 이걸 잡아 "연결 실패" 상태를 그린다. */
export class BackendUnavailableError extends Error {
  constructor(
    readonly url: string,
    readonly cause_: unknown,
  ) {
    super(`백엔드에 연결하지 못했습니다: ${url}`);
    this.name = "BackendUnavailableError";
  }
}

/** 백엔드는 응답했지만 우리가 원하는 답이 아니었다(404 없는 팀, 400 잘못된 기준 등). */
export class BackendResponseError extends Error {
  constructor(
    readonly status: number,
    readonly detail: string,
  ) {
    super(`백엔드 응답 ${status}: ${detail}`);
    this.name = "BackendResponseError";
  }
}

function usingMock(): boolean {
  return process.env.GAK_DATA_SOURCE === "mock";
}

function baseUrl(): string {
  return process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
}

/**
 * 입력 화면의 선행 요청 — 시즌과 그 시즌 선택 가능 팀.
 *
 * **가장 먼저, 혼자 부른다.** 나머지 네 요청은 여기서 확정된 `season`을 받아야 하므로
 * 나란히 부칠 수 없다. 왕복이 하나 늘지만, 병렬로 부르면 시즌이 정해지기 전에 다른
 * 화면들이 각자 시즌을 고르게 되고 그게 정확히 우리가 없애려는 상태다.
 *
 * **목 모드에서도 실제로 부르지 않는다.** 목 스냅샷은 맨유 한 팀·한 시즌짜리 진단 응답을
 * 찍어 둔 것이라, 여기서 그럴듯한 시즌 목록을 지어내면 화면에 "지난 시즌 보기" 버튼이
 * 살아 있는데 눌러도 같은 데이터가 나오는 상태가 된다.
 */
export async function getTeamSelection(query: {
  season?: number;
  teamId?: number;
}): Promise<TeamSelection> {
  if (usingMock()) {
    return mockSelection(query.teamId ?? MANCHESTER_UNITED_ID);
  }

  const params = new URLSearchParams();
  if (query.season != null) params.set("season", String(query.season));
  if (query.teamId != null) params.set("teamId", String(query.teamId));

  const qs = params.toString();
  const url = `${baseUrl()}/api/teams/selection${qs ? `?${qs}` : ""}`;

  let res: Response;
  try {
    res = await fetch(url, { next: { revalidate: 60 } });
  } catch (e) {
    // 실패를 "선택 가능 팀 0개"로 옮기지 않는다 — 그건 다른 사실이다.
    throw new BackendUnavailableError(url, e);
  }
  if (!res.ok) {
    throw new BackendResponseError(res.status, await readErrorMessage(res));
  }
  return (await res.json()) as TeamSelection;
}

/**
 * 목 모드의 선택 상태. 스냅샷이 담고 있는 시즌 하나·팀 하나를 그대로 말한다 —
 * **앞뒤 시즌이 없다는 사실을 숨기지 않는다.**
 */
function mockSelection(teamId: number): TeamSelection {
  const season = MOCK_SEASON;
  return {
    season,
    calendarSeason: false,
    currentSeason: season,
    current: true,
    previousSeason: null,
    nextSeason: null,
    teams: [{ teamId, name: "맨유", code: "MUN" }],
    restricted: true,
    selected: { teamId, name: "맨유", code: "MUN", eligible: true },
  };
}

/** 목 스냅샷이 찍힌 시즌(맨유 2023-24). 파일과 함께 움직이는 값이다. */
const MOCK_SEASON = 2023;

/**
 * 한 팀의 전 대회 통합 일정 + 진단을 가져온다.
 *
 * 경기 목록과 밀집도를 따로 부르지 않는 이유는 서버 쪽 컨트롤러 주석에 있다 —
 * 두 번 부르면 그 사이에 동기화가 끼어들어 "5경기가 그려지는데 밀집 구간은 6경기라고
 * 말하는" 상태가 생긴다.
 */
export async function getTeamDiagnostics(
  query: DiagnosticsQuery,
): Promise<DiagnosticsResult> {
  if (usingMock()) {
    return { diagnostics: await loadMock(), source: "mock" };
  }

  const params = new URLSearchParams();
  if (query.windowDays != null) params.set("windowDays", String(query.windowDays));
  if (query.minMatches != null) params.set("minMatches", String(query.minMatches));
  if (query.season != null) params.set("season", String(query.season));

  const qs = params.toString();
  const url = `${baseUrl()}/api/teams/${query.teamId}/diagnostics${qs ? `?${qs}` : ""}`;

  let res: Response;
  try {
    // 동기화는 매시 한 번 돈다. 매 새로고침마다 다시 물을 만큼 자주 변하는 값이 아니다.
    res = await fetch(url, { next: { revalidate: 60 } });
  } catch (e) {
    // fetch 자체가 실패 = 백엔드가 안 떠 있거나 주소가 틀렸다.
    throw new BackendUnavailableError(url, e);
  }

  if (!res.ok) {
    throw new BackendResponseError(res.status, await readErrorMessage(res));
  }
  return { diagnostics: (await res.json()) as TeamDiagnostics, source: "backend" };
}

/**
 * 이 팀의 예측 적중률 + 최근 기록.
 *
 * 진단과 엔드포인트를 나눈 건 둘이 서로 다른 속도로 변하기 때문이다(진단은 동기화마다,
 * 적중률은 채점마다).
 *
 * 목 모드에는 예측 기록이 없다 — 목의 경기가 전부 과거라 예측을 만들 수 없기 때문이다
 * (킥오프 이전 규칙). 빈 값을 돌려주되 그건 **적중률 0%가 아니라 "기록 없음"**이고,
 * 화면도 그렇게 구분해 그린다.
 */
export async function getTeamPredictions(
  teamId: number,
  season: number,
): Promise<PredictionAccuracy> {
  if (usingMock()) {
    return {
      teamId,
      teamName: "",
      season,
      scored: 0,
      pending: 0,
      hits: 0,
      misses: 0,
      hitRate: null,
      confidence: "NONE",
      byPick: {},
      recent: [],
    };
  }

  const url = `${baseUrl()}/api/teams/${teamId}/predictions?season=${season}`;
  let res: Response;
  try {
    res = await fetch(url, { next: { revalidate: 60 } });
  } catch (e) {
    throw new BackendUnavailableError(url, e);
  }
  if (!res.ok) {
    throw new BackendResponseError(res.status, await readErrorMessage(res));
  }
  return (await res.json()) as PredictionAccuracy;
}

/**
 * 이 팀이 뛰는 리그의 순위표.
 *
 * 진단과 따로 부른다 — 순위표는 동기화 때만 바뀌고, 진단은 경기가 끝날 때마다 바뀐다.
 *
 * 목 모드에는 순위표가 없다. 목 스냅샷은 진단 응답을 찍어 둔 것이라 순위표가 들어 있지
 * 않고, 없는 걸 만들어 넣으면 화면이 가짜 순위를 그린다. **"없음"을 그대로 돌려준다.**
 */
export async function getTeamStandings(
  teamId: number,
  season: number,
): Promise<StandingsTable> {
  if (usingMock()) {
    return {
      available: false,
      unavailableReason:
        "목 데이터에는 순위표가 없습니다. 백엔드에 연결하면 표시됩니다.",
      competitionId: 0,
      competitionName: null,
      season: null,
      rows: [],
      updatedAt: null,
    };
  }

  const url = `${baseUrl()}/api/teams/${teamId}/standings?season=${season}`;
  let res: Response;
  try {
    res = await fetch(url, { next: { revalidate: 60 } });
  } catch (e) {
    throw new BackendUnavailableError(url, e);
  }
  if (!res.ok) {
    throw new BackendResponseError(res.status, await readErrorMessage(res));
  }
  return (await res.json()) as StandingsTable;
}

/**
 * 이 팀의 소식 — `GET /api/teams/{teamId}/news`.
 *
 * **진단과 따로 부른다.** 합치면 화면 코드에서 두 층이 같은 객체로 들어오고, 언젠가
 * 누군가 진단 문장에 뉴스를 인용한다. 층을 나눈다는 건 서로를 참조하지 않는다는 뜻이고,
 * 호출을 나누는 것으로 그걸 지킨다.
 *
 * **실패해도 화면을 무너뜨리지 않는다.** 소식은 덧칠이라, 못 받으면 그 층만 비운다.
 * 여기서만 예외를 삼키는 이유가 그것이다 — 진단은 못 받으면 화면이 의미가 없지만,
 * 뉴스는 없어도 타임라인이 멀쩡하다.
 *
 * 목 모드에는 소식이 없다. **없는 걸 만들어 넣지 않는다** — 순위표와 같은 판단이다.
 */
export async function getTeamNews(teamId: number): Promise<TeamNewsResponse> {
  const empty: TeamNewsResponse = {
    items: [],
    coverage: { from: null, to: null, totalItems: 0, retentionDays: 0 },
  };
  if (usingMock()) return empty;

  const url = `${baseUrl()}/api/teams/${teamId}/news`;
  try {
    const res = await fetch(url, { next: { revalidate: 60 } });
    if (!res.ok) return empty;
    return (await res.json()) as TeamNewsResponse;
  } catch {
    return empty;
  }
}

/** 백엔드 공통 오류 응답(`{timestamp, status, message}`)에서 사람이 읽을 문구만 뽑는다. */
async function readErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string };
    return body.message ?? res.statusText;
  } catch {
    return res.statusText;
  }
}

/**
 * 개발용 스냅샷. **백엔드가 실제로 계산해 내려준 응답을 그대로 저장한 파일**이라,
 * 목이라도 화면이 보는 값의 규칙은 실제와 같다. (만드는 법:
 * `scripts/generate-mock-snapshot.md`)
 */
async function loadMock(): Promise<TeamDiagnostics> {
  const snapshot = await import("@/lib/api/mock/diagnostics-manutd-2324.json");
  return (snapshot.default ?? snapshot) as unknown as TeamDiagnostics;
}
