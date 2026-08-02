/**
 * FixtureResponse(원형) → 표시값 파생 헬퍼.
 * 대회 종류·결과·간격 같은 파생값은 저장하지 않고 여기서 그때그때 계산한다.
 */
import type { FixtureResponse } from "@/lib/api/types";
import type { Competition, MatchResult } from "@/lib/timeline/types";

const DOW_KO = ["일", "월", "화", "수", "목", "금", "토"] as const;

/** ISO → "3/17" (UTC 기준 — 저장·정렬과 동일 기준). */
export function toDateLabel(iso: string): string {
  const d = new Date(iso);
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()}`;
}

/** ISO → 요일 "일". */
export function toDow(iso: string): string {
  return DOW_KO[new Date(iso).getUTCDay()];
}

/**
 * 대회 종류 판별.
 * 목/실데이터 모두 대응: 알려진 league.id 를 우선 쓰고, 모르는 리그는 round
 * 문자열로 컵/유럽/리그를 추정한다(프로젝트 원칙: standings·round 로 파생).
 */
export function classifyCompetition(league: {
  id: number;
  round: string;
}): Competition {
  switch (league.id) {
    case 39:
      return { key: "league", label: "리그" };
    case 45:
      return { key: "cup", label: "FA컵" };
    case 48:
      return { key: "cup", label: "리그컵" };
    case 2:
      return { key: "europe", label: "챔스" };
    default: {
      const r = league.round.toLowerCase();
      if (r.includes("group") || r.includes("knockout"))
        return { key: "europe", label: "유럽" };
      if (
        r.includes("final") ||
        r.includes("round") ||
        r.includes("quarter") ||
        r.includes("semi")
      )
        return { key: "cup", label: "컵" };
      return { key: "league", label: "리그" };
    }
  }
}

/** 우리 팀 관점의 승/무/패. winner 플래그를 신뢰(승부차기 진출팀=true). */
export function resolveResult(
  fx: FixtureResponse,
  ourTeamId: number,
): MatchResult {
  const isHome = fx.teams.home.id === ourTeamId;
  const winner = isHome ? fx.teams.home.winner : fx.teams.away.winner;
  if (winner === true) return "W";
  if (winner === false) return "L";
  return "D";
}

/** 우리 팀 관점 스코어 "4-3" (우리 득 - 우리 실). */
export function resolveScore(fx: FixtureResponse, ourTeamId: number): string {
  const isHome = fx.teams.home.id === ourTeamId;
  const our = isHome ? fx.goals.home : fx.goals.away;
  const opp = isHome ? fx.goals.away : fx.goals.home;
  return `${our ?? "-"}-${opp ?? "-"}`;
}

/** 홈/원정/중립. */
export function resolveHomeAway(
  fx: FixtureResponse,
  ourTeamId: number,
): "H" | "A" | "N" {
  // 중립 경기(결승·준결승 등)는 venue 로 판단: 우리 홈 구장이 아니고 상대 홈도 아님.
  // 목에서는 status/venue 로 구분되지만, 간단히 홈팀 == 우리면 H, 아니면 A/N.
  const isHome = fx.teams.home.id === ourTeamId;
  // Wembley 등 중립 표기: venue.name 에 "Wembley" 가 있으면 N.
  if (fx.fixture.venue.name?.includes("Wembley")) return "N";
  return isHome ? "H" : "A";
}

/** 연장/승부차기 노트. */
export function resolveNote(fx: FixtureResponse): string | null {
  switch (fx.fixture.status.short) {
    case "AET":
      return "연장";
    case "PEN":
      return "승부차기";
    default:
      return null;
  }
}

/**
 * 상대 팀명 — 한글 매핑 우선, 없으면 영문 원문 fallback (표기 규칙 2b).
 */
export function opponentName(
  fx: FixtureResponse,
  ourTeamId: number,
  koMap: Record<string, string>,
): string {
  const opp = fx.teams.home.id === ourTeamId ? fx.teams.away : fx.teams.home;
  return koMap[opp.name] ?? opp.name;
}
