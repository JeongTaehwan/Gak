// requirements.md 1·5장 — 자유 질문은 teamId + season 과 함께 진단 경로로 간다
import { NextResponse } from "next/server";
import type { TeamAnswer } from "@/lib/api/types";

/**
 * 자유 질문 프록시 — 브라우저 → 여기 → 백엔드.
 *
 * ## 왜 브라우저가 백엔드를 직접 안 부르나
 * `app/api/diagnosis/route.ts` 와 같은 이유다 — CORS, 그리고 백엔드 주소·인증이 바뀔 때
 * 고칠 곳을 한 군데로 모으기 위해서.
 *
 * ## 왜 서버 컴포넌트가 아니라 여기인가
 * 질문은 사용자가 던지는 시점에 생긴다. 첫 화면 렌더에 묶을 수 없고, 묶으면 타임라인이
 * 모델을 기다린다.
 *
 * ## 실패해도 200을 돌려준다
 * "근거 부족"·"범위 밖"·"분석 실패"·"이해 실패"는 전부 화면이 **다른 말을 해야 하는
 * 상태**이지 HTTP 오류가 아니다. 오류 코드로 내려보내면 화면이 넷을 구분하지 못하고
 * "실패"로 뭉뚱그린다.
 */
export async function POST(request: Request): Promise<NextResponse<TeamAnswer>> {
  let body: { teamId?: unknown; season?: unknown; question?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json(failed("요청을 읽지 못했습니다."));
  }

  const teamId = Number(body.teamId);
  const season = Number(body.season);
  const question = typeof body.question === "string" ? body.question.trim() : "";

  // 팀과 시즌은 함께 와야 한다. 하나라도 없으면 어느 시즌 데이터로 답할지가 정해지지 않는다.
  if (!Number.isInteger(teamId) || !Number.isInteger(season) || question.length === 0) {
    return NextResponse.json(failed("질문할 팀과 시즌이 정해지지 않았습니다."));
  }

  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

  try {
    const res = await fetch(`${base}/api/teams/${teamId}/questions`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ season, question }),
      // 질문마다 답이 다르다 — 캐시할 것이 없다.
      cache: "no-store",
    });
    if (!res.ok) {
      return NextResponse.json(failed("분석 처리에 실패했습니다."));
    }
    return NextResponse.json((await res.json()) as TeamAnswer);
  } catch {
    return NextResponse.json(failed("백엔드에 연결하지 못했습니다."));
  }
}

/**
 * 백엔드에 닿지 못했을 때의 응답.
 *
 * 분모를 **0으로 채우지 않는다** — 값을 모르는 것이지 0경기를 본 게 아니다. 화면은
 * `analyzedFixtures`가 0이고 `seasonFixtures`도 0이면 분모 줄을 그리지 않는다.
 */
function failed(message: string): TeamAnswer {
  return {
    status: "ANALYSIS_FAILED",
    statusMessage: message,
    answer: null,
    evidence: [],
    unknowns: [],
    basis: {
      season: null,
      calendarSeason: false,
      analyzedFixtures: 0,
      seasonFixtures: 0,
      upcomingFixtures: 0,
      excludedFixtures: 0,
      from: null,
      to: null,
      leagueRecord: false,
    },
  };
}
