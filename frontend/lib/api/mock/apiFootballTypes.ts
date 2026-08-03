/**
 * API-Football `/fixtures` 응답 구조.
 *
 * ⚠️ 화면은 이 타입을 쓰지 않는다. 프론트는 우리 백엔드(`lib/api/types.ts`)만 본다.
 *    여기 남겨 둔 이유는 목 데이터(`manutd-2324.ts`)가 **외부 API가 주는 원본 모양**
 *    그대로 적혀 있기 때문이다 — 그래야 그 데이터를 백엔드 동기화 경로에 그대로 태워
 *    스냅샷을 만들 수 있다(자세한 건 `manutd-2324.ts` 머리말).
 *
 * (원래 `lib/api/types.ts`에 있던 내용이다. 목→실 API 전환 때 자리를 옮겼다.)
 */

/** 경기 상태 코드 — 우리가 쓰는 값만 좁혀 둔다. */
export type FixtureStatusShort =
  | "FT" // 정규시간 종료
  | "AET" // 연장 종료
  | "PEN" // 승부차기까지 감
  | "NS"; // 아직 안 열림(예정)

export interface Venue {
  id: number | null;
  name: string | null;
  city: string | null;
}

export interface FixtureStatus {
  long: string;
  short: FixtureStatusShort;
  /** 진행 분. 종료 경기는 90/120, 예정 경기는 null. */
  elapsed: number | null;
}

export interface FixtureInfo {
  id: number;
  /** ISO 8601 (타임존 오프셋 포함) — 정렬·간격 계산의 단일 근거. */
  date: string;
  timestamp: number;
  venue: Venue;
  status: FixtureStatus;
}

export interface LeagueInfo {
  id: number;
  name: string;
  country: string;
  season: number;
  /** 예: "Regular Season - 15", "Quarter-finals", "Group Stage - 3". */
  round: string;
}

export interface TeamSide {
  id: number;
  name: string;
  /**
   * 이 팀이 이 경기를 이겼는지.
   * true=승, false=패, null=무(또는 미결). 승부차기로 갈린 컵경기는
   * 진출 팀이 true 로 온다(API-Football 동작).
   *
   * ※ 우리는 이 플래그로 승패를 판정하지 않는다. 백엔드가 득점으로 판정하고
   *   승부차기는 무승부로 접는다(`MatchLoad` 참고).
   */
  winner: boolean | null;
}

export interface Teams {
  home: TeamSide;
  away: TeamSide;
}

export interface Goals {
  home: number | null;
  away: number | null;
}

export interface ScoreLine {
  home: number | null;
  away: number | null;
}

export interface Score {
  halftime: ScoreLine;
  fulltime: ScoreLine;
  extratime: ScoreLine;
  penalty: ScoreLine;
}

/** `/fixtures` 응답 배열의 한 원소. */
export interface FixtureResponse {
  fixture: FixtureInfo;
  league: LeagueInfo;
  teams: Teams;
  goals: Goals;
  score: Score;
}
