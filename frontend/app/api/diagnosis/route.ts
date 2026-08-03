import { NextResponse } from "next/server";
import type { AiDiagnosis } from "@/lib/api/types";

/**
 * AI 진단 프록시 — 브라우저 → 여기 → 백엔드.
 *
 * ## 왜 브라우저가 백엔드를 직접 안 부르나
 *
 * 두 가지다. 하나는 **CORS** — 백엔드는 브라우저에서 직접 불릴 걸 전제하지 않는다.
 * 다른 하나는 **경계 유지** — 지금은 백엔드 주소가 `NEXT_PUBLIC_`이라 공개돼 있지만,
 * 나중에 인증 헤더나 사설 주소로 바뀌면 브라우저에서 부르던 코드는 전부 고쳐야 한다.
 * 여기서 한 번 받아 넘기면 그때 이 파일만 바뀐다.
 *
 * ## 왜 서버 컴포넌트에서 미리 받아 두지 않나
 *
 * 그러면 **첫 화면이 AI를 기다린다.** 이 앱의 첫 화면은 타임라인이고, 그건 우리 DB만
 * 읽어 수십 ms에 나온다. 거기에 수 초짜리 모델 호출을 묶으면 밀집도 브래킷을 보러 온
 * 사용자가 빈 화면을 본다. 그래서 화면은 규칙 기반 문장으로 먼저 완성되고, 이 요청은
 * 진단 탭이 열릴 때 브라우저가 따로 보낸다.
 *
 * ## 실패해도 200을 돌려준다
 *
 * 백엔드가 죽었든 모델이 느리든, 화면이 할 일은 똑같다 — 규칙 기반 문장을 그대로 두는
 * 것. 그걸 굳이 에러 상태로 만들어 클라이언트에 try/catch 를 강요할 이유가 없다.
 */
export async function GET(request: Request): Promise<NextResponse<AiDiagnosis>> {
  const { searchParams } = new URL(request.url);
  const teamId = searchParams.get("teamId");

  if (!teamId || !/^\d+$/.test(teamId)) {
    return NextResponse.json(unavailable("팀을 지정하지 않았습니다"));
  }

  // 목 모드에서는 AI를 부르지 않는다. 목 스냅샷은 백엔드가 계산한 값을 저장한 것이지
  // 모델이 쓴 문장이 아니고, 가짜 AI 문장을 만들어 넣으면 배지가 거짓말을 하게 된다.
  if (process.env.GAK_DATA_SOURCE === "mock") {
    return NextResponse.json(
      unavailable("목 데이터 모드에서는 AI 진단을 부르지 않습니다"),
    );
  }

  const base = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";
  const params = new URLSearchParams({ teamId });
  for (const key of ["windowDays", "minMatches", "formSize"]) {
    const value = searchParams.get(key);
    if (value) params.set(key, value);
  }
  params.delete("teamId");
  const qs = params.toString();
  const url = `${base}/api/teams/${teamId}/diagnosis${qs ? `?${qs}` : ""}`;

  try {
    // 같은 지표에 대한 답은 잘 안 변한다. 진단 탭을 껐다 켤 때마다 모델을 부르면
    // 느리고, 비싸고, 문장이 미묘하게 바뀌어 사용자에게는 앱이 불안정해 보인다.
    const res = await fetch(url, { next: { revalidate: 300 } });
    if (!res.ok) {
      return NextResponse.json(unavailable("AI 진단을 불러오지 못했습니다"));
    }
    return NextResponse.json((await res.json()) as AiDiagnosis);
  } catch {
    return NextResponse.json(unavailable("백엔드에 연결하지 못했습니다"));
  }
}

function unavailable(reason: string): AiDiagnosis {
  return {
    available: false,
    unavailableReason: reason,
    headline: null,
    sub: null,
    evidence: [],
    unknowns: [],
  };
}
