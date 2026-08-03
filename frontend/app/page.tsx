import {
  BackendUnavailableError,
  BackendResponseError,
  MANCHESTER_UNITED_ID,
  getTeamDiagnostics,
} from "@/lib/api/client";
import { buildTimeline } from "@/lib/timeline/buildTimeline";
import { MainScreen } from "@/components/MainScreen";
import { ConnectionError } from "@/components/layout/ConnectionError";

/**
 * 메인 페이지 (서버 컴포넌트) = 데이터 경계.
 *
 *   getTeamDiagnostics() 가 백엔드에서 진단 결과를 받아오고,
 *   buildTimeline() 이 그걸 화면용 뷰모델로 옮긴다.
 *   클라이언트(MainScreen)는 뷰모델만 받으므로 백엔드 응답 모양을 알지 못한다.
 *
 * 밀집도·폼 같은 판정은 여기서도, 뷰모델에서도 하지 않는다 — 전부 백엔드가 끝냈다.
 *
 * 백엔드에 닿지 못하면 **목 데이터로 몰래 넘어가지 않는다.** 그러면 화면은 멀쩡한데
 * 보고 있는 숫자가 가짜인 상태가 되고, 그게 가장 눈치채기 어려운 실패다. 대신 왜
 * 안 되는지와 무엇을 하면 되는지를 화면에 적는다.
 */
/**
 * 요청마다 렌더한다(정적 프리렌더 금지).
 *
 * 이게 없으면 Next 가 빌드 시점에 이 페이지를 한 번 그려 굳혀 버린다. 그런데 빌드할 때
 * 백엔드가 안 떠 있으면 **"연결 실패" 화면이 그대로 구워져** 배포되고, 백엔드가 살아난
 * 뒤에도 재검증 주기가 돌 때까지 그 화면이 나간다. 응답 자체는 아래 fetch 가 60초 캐시하므로
 * 매 요청이 백엔드를 때리지도 않는다.
 */
export const dynamic = "force-dynamic";

export default async function Home() {
  try {
    const { diagnostics, source } = await getTeamDiagnostics({
      teamId: MANCHESTER_UNITED_ID,
    });
    return (
      <MainScreen timeline={buildTimeline(diagnostics)} source={source} />
    );
  } catch (e) {
    if (e instanceof BackendUnavailableError) {
      return <ConnectionError kind="unreachable" detail={e.url} />;
    }
    if (e instanceof BackendResponseError) {
      return (
        <ConnectionError
          kind="rejected"
          detail={`${e.status} — ${e.detail}`}
        />
      );
    }
    throw e;
  }
}
