/**
 * 경기 데이터 진입점 — "교체 지점".
 *
 * 지금은 목 데이터를 돌려주지만, 반환 타입(FixtureResponse[])과 함수 시그니처는
 * 실제 API 계약과 동일하다. 실제 백엔드가 준비되면 아래 함수 본문 한 곳만
 * fetch 로 바꾸면 되고, 화면·알고리즘 계층은 전혀 손대지 않는다.
 *
 *   // 교체 예시 (백엔드가 API-Football shape 을 프록시해서 내려줄 때):
 *   const base = process.env.NEXT_PUBLIC_API_BASE_URL;
 *   const res = await fetch(`${base}/api/teams/${teamId}/fixtures?season=${season}`, {
 *     next: { revalidate: 60 },
 *   });
 *   if (!res.ok) throw new Error(`fixtures ${res.status}`);
 *   return (await res.json()) as FixtureResponse[];
 */
import type { FixtureResponse } from "@/lib/api/types";
import {
  MANCHESTER_UNITED_ID,
  MANUTD_2324_FIXTURES,
} from "@/lib/api/mock/manutd-2324";

export interface FixturesQuery {
  teamId: number;
  season: number;
}

/** 한 팀의 한 시즌 전 대회 경기를 날짜순으로 반환. */
export async function getTeamFixtures(
  query: FixturesQuery = { teamId: MANCHESTER_UNITED_ID, season: 2023 },
): Promise<FixtureResponse[]> {
  // 목 단계: 네트워크 없이 즉시 반환. (실제 API 교체 시 위 주석 블록 참고)
  void query;
  return MANUTD_2324_FIXTURES;
}
