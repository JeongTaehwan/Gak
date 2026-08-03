"use client";

import { useEffect, useState } from "react";
import type { AiDiagnosis } from "@/lib/api/types";

/**
 * AI 진단을 뒤늦게 받아 온다 — **점진적 향상(progressive enhancement)**.
 *
 * ## 설계 의도
 *
 * 화면은 이 훅 없이도 완성돼 있다. 규칙 기반 결론이 서버 렌더 시점에 이미 그려져 있고,
 * 이 훅은 그 위에 얹을 게 있으면 얹는다. 그래서:
 *
 * - **로딩 스피너가 없다.** 기다릴 게 없기 때문이다. 사용자는 이미 읽을 문장을 갖고 있고,
 *   배지 옆에 작은 "AI 분석 중" 표시만 붙는다. 스켈레톤을 깔면 있는 내용을 일부러
 *   가리는 셈이다.
 * - **실패가 에러 상태를 만들지 않는다.** 못 받으면 그냥 안 바뀐다. 사용자가 잃는 게
 *   없으므로 "다시 시도" 버튼도, 빨간 배너도 필요 없다.
 * - **재시도하지 않는다.** 실패 대부분이 즉시 고쳐지지 않는 종류다(키 없음, 표본 부족,
 *   백엔드 다운). 자동 재시도는 비용만 쓰고 같은 답을 받는다.
 *
 * ## 언마운트 처리
 *
 * 진단 탭을 열었다 바로 닫으면 응답이 뒤늦게 돌아온다. 그때 상태를 갱신하면 React 가
 * 경고를 내고, 무엇보다 **다음에 탭을 열었을 때 이전 요청의 답이 스칠 수** 있다.
 * `AbortController` 로 끊는다.
 *
 * @param teamId  조회할 팀. null 이면 부르지 않는다(탭이 안 열린 상태)
 */
export function useAiDiagnosis(teamId: number | null): {
  state: "idle" | "loading" | "done";
  diagnosis: AiDiagnosis | null;
} {
  const [state, setState] = useState<"idle" | "loading" | "done">("idle");
  const [diagnosis, setDiagnosis] = useState<AiDiagnosis | null>(null);

  useEffect(() => {
    if (teamId == null) return;

    const controller = new AbortController();
    setState("loading");

    fetch(`/api/diagnosis?teamId=${teamId}`, { signal: controller.signal })
      .then((res) => res.json() as Promise<AiDiagnosis>)
      .then((result) => {
        setDiagnosis(result);
        setState("done");
      })
      .catch((e) => {
        // abort 는 실패가 아니다 — 컴포넌트가 사라진 것뿐이라 상태를 건드리지 않는다.
        if (e instanceof DOMException && e.name === "AbortError") return;
        // 그 외의 실패도 화면에는 아무 일도 일어나지 않는다. 규칙 기반 문장이 남는다.
        setState("done");
      });

    return () => controller.abort();
  }, [teamId]);

  return { state, diagnosis };
}
