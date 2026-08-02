/**
 * API-Football `/fixtures` 응답 구조를 그대로 옮긴 타입.
 *
 * ⚠️ 이 파일은 "외부 API의 계약"이다. 목 데이터든 실제 API든 이 shape을 지킨다.
 *    화면·알고리즘 계층은 오직 이 타입에만 의존하므로, 목을 실제 API로 바꿔도
 *    (lib/api/client.ts 한 곳만 교체) 나머지 코드는 손대지 않는다.
 *
 * 참고: 대회 종류(리그/컵/유럽)·경기 간격·밀집도·폼 같은 "파생값"은 여기 없다.
 *       API가 준 사실만 담고, 파생값은 lib/timeline 에서 계산한다.
 *       (프로젝트 원칙: 사실만 저장/전달, 파생값은 계산)
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
