// requirements.md 1·5장 — 자유 질문의 답과 답변 불가 상태
import type {
  AnswerBasis,
  AnswerStatus,
  TeamAnswer,
} from "@/lib/api/types";
import type { Evidence } from "@/lib/chat/script";
import { seasonLabel } from "@/lib/timeline/format";

/**
 * 답변 아래 붙는 한 줄 — **이 답이 무엇을 세고 무엇을 뺐는가.**
 *
 * 분모를 밝히는 자리다. "44경기 기준"과 "52경기 기준"은 같은 평균이라도 다른 말이고,
 * 아직 치르지 않은 경기를 뺐다는 사실은 화면에 그 경기들이 보이기 때문에 더 중요하다.
 * 값은 전부 서버가 계산해 실어 보낸 것이라 여기서는 문자열로 옮기기만 한다.
 */
export function basisNote(basis: AnswerBasis): string {
  // 시즌조차 정해지지 않은 응답(백엔드에 닿지 못한 경우)에는 분모가 없다. 0을 적으면
  // "0경기를 봤다"가 되는데, 우리는 그것도 모른다.
  if (basis.season == null) return "";

  const parts: string[] = [];

  const season = seasonLabel(basis.season, basis.calendarSeason);
  if (season) parts.push(season);

  if (basis.seasonFixtures > 0) {
    parts.push(`전체 ${basis.seasonFixtures}경기 중 ${basis.analyzedFixtures}경기 기준`);
  }
  // "예정 경기를 무승부로 접지 않았다"가 이 줄로 드러난다.
  if (basis.upcomingFixtures > 0) {
    parts.push(`예정 ${basis.upcomingFixtures}경기 제외`);
  }
  if (basis.excludedFixtures > 0) {
    parts.push(`연기·취소 ${basis.excludedFixtures}경기 제외`);
  }
  if (!basis.leagueRecord) {
    parts.push("선택 대상 1부 리그 기록 없음");
  }
  return parts.join(" · ");
}

/**
 * 근거 세 칸(`metric`·`value`·`claim`)을 말풍선이 그리는 한 줄로.
 *
 * 지표 이름과 값을 **문장 뒤에 그대로 붙인다.** 주장만 남기면 검산할 수 없고, 이 앱에서
 * 검산할 수 없는 문장은 소문과 구분되지 않는다.
 */
export function toChatEvidence(answer: TeamAnswer): Evidence[] {
  return answer.evidence.map((e) => ({
    text: `${e.claim} (${e.metric} ${e.value})`,
  }));
}

/**
 * 답을 내지 못했을 때 말풍선에 넣을 문장.
 *
 * ⚠️ 서버가 보내 준 문구를 그대로 쓴다. 여기서 다시 쓰면 같은 실패 원인에 두 가지 문구가
 * 생기고, 최종 문구가 정해질 때(open-questions IN-OQ-06) 한쪽만 고치게 된다. 서버가
 * 문구를 못 보낸 경우에만 최소한의 대체 문장을 쓴다.
 */
export function unansweredText(answer: TeamAnswer): string {
  return answer.statusMessage ?? FALLBACK[answer.status] ?? FALLBACK.ANALYSIS_FAILED;
}

/** 서버가 문구를 비워 보냈을 때만 쓰는 대체 문장. 잠정 문구다. */
const FALLBACK: Record<AnswerStatus, string> = {
  ANSWERED: "",
  INSUFFICIENT_DATA: "이 질문에 답할 근거 데이터가 없습니다.",
  OUT_OF_SCOPE: "이 앱이 다루지 않는 내용입니다.",
  ANALYSIS_FAILED: "분석 처리에 실패했습니다.",
  UNINTELLIGIBLE: "질문의 뜻을 판별하지 못했습니다. 다시 표현해 주세요.",
};
