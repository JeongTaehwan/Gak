import { getTeamFixtures } from "@/lib/api/client";
import {
  MANCHESTER_UNITED_ID,
  TEAM_NAME_KO,
} from "@/lib/api/mock/manutd-2324";
import { buildTimeline } from "@/lib/timeline/buildTimeline";
import { MainScreen } from "@/components/MainScreen";

/**
 * 메인 페이지 (서버 컴포넌트) = 목↔실제 교체 경계.
 *   getTeamFixtures() 가 API-Football shape 을 돌려주고(지금은 목, 나중엔 백엔드
 *   fetch), buildTimeline() 이 뷰모델로 변환한다. 클라이언트(MainScreen)는 뷰모델만
 *   받으므로, 데이터 소스가 바뀌어도 화면 코드는 그대로다.
 */
export default async function Home() {
  const fixtures = await getTeamFixtures({
    teamId: MANCHESTER_UNITED_ID,
    season: 2023,
  });

  const timeline = buildTimeline(fixtures, {
    ourTeamId: MANCHESTER_UNITED_ID,
    koMap: TEAM_NAME_KO,
  });

  return <MainScreen timeline={timeline} />;
}
