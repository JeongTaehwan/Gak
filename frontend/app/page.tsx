import {
  BackendUnavailableError,
  BackendResponseError,
  MANCHESTER_UNITED_ID,
  getTeamDiagnostics,
  getTeamNews,
  getTeamPredictions,
  getTeamSelection,
  getTeamStandings,
} from "@/lib/api/client";
import { redirect } from "next/navigation";
import { buildTimeline } from "@/lib/timeline/buildTimeline";
import { MainScreen } from "@/components/MainScreen";
import { ConnectionError } from "@/components/layout/ConnectionError";
import { NoSeasons } from "@/components/input/NoSeasons";
import { readScreenState, toHref } from "@/lib/url/screenState";
import type { TeamSelection } from "@/lib/api/types";

/**
 * 메인 페이지 (서버 컴포넌트) = 데이터 경계.
 *
 *   getTeamSelection() 이 **시즌과 선택 가능 팀을 먼저 확정**하고,
 *   그 teamId + season 으로 나머지 네 응답을 받아 온다.
 *   buildTimeline() 이 진단 응답을 화면용 뷰모델로 옮긴다 — 보기 모드(전 대회/리그만)는
 *   URL 이 쥐므로 여기서 넘긴다. 리그 범위의 수치도 응답에 이미 들어 있다(백엔드 재판정).
 *   클라이언트(MainScreen)는 뷰모델만 받으므로 백엔드 응답 모양을 알지 못한다.
 *
 * 밀집도·폼 같은 판정은 여기서도, 뷰모델에서도 하지 않는다 — 전부 백엔드가 끝냈다.
 *
 * ## 왜 선택 요청만 따로 먼저 부르나
 *
 * 다섯 요청을 한꺼번에 부치면 **시즌이 정해지기 전에** 네 화면이 각자 시즌을 고르게 된다.
 * 그게 정확히 이번에 없앤 상태다 — 2023-24 타임라인 옆에 다른 시즌 순위표가 뜨는 것.
 * 왕복 하나를 더 쓰는 대신, 그 뒤 네 개는 같은 시즌을 받아 나란히 나간다.
 *
 * ## URL 이 팀·시즌·탭을 쥔다
 *
 * `season` 은 자동 판정 결과를 **주소에 고정**하기 위한 값이다. 비워 두면 같은 URL 이
 * 시간이 지나 다른 시즌을 연다. 주소에 없으면 서버가 판정해 주고, 화면은 그 값으로
 * 링크를 만든다(`lib/url/screenState.ts`).
 *
 * 백엔드에 닿지 못하면 **목 데이터로 몰래 넘어가지 않는다.** 그러면 화면은 멀쩡한데
 * 보고 있는 숫자가 가짜인 상태가 되고, 그게 가장 눈치채기 어려운 실패다. 대신 왜
 * 안 되는지와 무엇을 하면 되는지를 화면에 적는다. **팀 목록을 받지 못한 것도 마찬가지라
 * "선택 가능 팀 0개"로 옮기지 않는다** — 실패와 빈 목록은 다른 사실이다.
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

export default async function Home({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const state = readScreenState(await searchParams, MANCHESTER_UNITED_ID);

  // ① 시즌과 팀을 먼저 확정한다. 나머지는 전부 이 결과를 입력으로 받는다.
  let selection: TeamSelection;
  try {
    selection = await getTeamSelection({
      season: state.season ?? undefined,
      teamId: state.teamId,
    });
  } catch (e) {
    return failureView(e);
  }

  // 선택 기준 대회 경기가 하나도 없다 = 볼 시즌이 없다.
  // "선택 가능 팀 0개"와 다른 상태라 화면도 다른 말을 한다.
  if (selection.season == null) {
    return <NoSeasons />;
  }
  const season = selection.season;

  // ② 자동 판정한 시즌을 **주소에 고정한다.**
  //
  // 이 한 번의 리다이렉트가 "같은 URL은 시간이 지나도 같은 시즌을 재현한다"를 만든다.
  // 주소에 season 이 없으면 열 때마다 다시 판정되므로, 회고 링크를 공유해 두고 몇 달 뒤
  // 열면 다른 시즌이 나온다 — 링크는 그대로인데 내용이 바뀌는, 알아채기 어려운 실패다.
  if (state.season == null) {
    redirect(toHref({ ...state, season }));
  }

  try {
    // ③ 같은 teamId + season 으로 네 응답을 나란히.
    const [{ diagnostics, source }, accuracy, standings, news] =
      await Promise.all([
        getTeamDiagnostics({ teamId: state.teamId, season }),
        getTeamPredictions(state.teamId, season),
        getTeamStandings(state.teamId, season),
        // 소식은 실패해도 예외를 던지지 않는다 — 없으면 그 층만 빈다.
        getTeamNews(state.teamId),
      ]);

    // 전 대회 뷰모델이 기준이다 — 헤더·대화·진단·예측은 보기와 무관하게 이 값을 쓴다.
    // 보기(view)가 바꾸는 것은 타임라인이 그리는 경기와 그 수치뿐이다.
    const timeline = buildTimeline(diagnostics, "all");
    const scoped =
      state.view === "league" ? buildTimeline(diagnostics, "league") : timeline;

    return (
      <MainScreen
        selection={selection}
        timeline={timeline}
        scopedTimeline={scoped}
        accuracy={accuracy}
        standings={standings}
        news={news}
        source={source}
        tab={state.tab}
        view={state.view}
      />
    );
  } catch (e) {
    return failureView(e);
  }
}

/**
 * 백엔드 실패를 화면으로. **여기서 목 데이터로 넘어가지 않는다** — 화면은 멀쩡한데
 * 숫자가 가짜인 상태가 아무것도 안 보이는 것보다 나쁘다.
 *
 * 우리가 아는 실패만 화면으로 바꾸고 나머지는 그대로 던진다.
 */
function failureView(e: unknown): React.ReactElement {
  if (e instanceof BackendUnavailableError) {
    return <ConnectionError kind="unreachable" detail={e.url} />;
  }
  if (e instanceof BackendResponseError) {
    return <ConnectionError kind="rejected" detail={`${e.status} — ${e.detail}`} />;
  }
  throw e;
}
