/**
 * 좌측 대화 — 하드코딩 답변 대본. (AI 연동은 나중; 지금은 정적 문구)
 *
 * 근거 문구는 맨유 2023-24 실데이터에 맞췄다. evidence.highlight 를 누르면 우측
 * 타임라인에서 해당 성격의 경기들이 강조된다(대화 근거 → 타임라인 강조).
 */
import type { HighlightTag } from "@/lib/timeline/types";

export type ChatHighlight = HighlightTag | null;

export interface Evidence {
  text: string;
  /** "보기 →" 클릭 시 강조할 태그(없으면 링크 없음). */
  highlight?: HighlightTag;
}

export interface QA {
  key: string;
  question: string;
  answer: string;
  highlight: ChatHighlight;
  evidence: Evidence[];
}

export const INTRO =
  "맨체스터 유나이티드, 2023-24. 무슨 각인지 물어봐 — 데이터로 답한다.";

export const QUESTIONS: QA[] = [
  {
    key: "why",
    question: "대체 왜 이러냐?",
    answer: "멘탈 아니고 일정이다. 가을 내내 밀집 구간이 세 번 겹쳤다.",
    highlight: "congestion",
    evidence: [
      {
        text: "9월 중순~12월 중순, 14일에 5경기 이상인 밀집 구간이 3번 (각 6~7경기)",
        highlight: "congestion",
      },
      {
        text: "그 사이 챔스 조별리그 6경기 — 바이에른 원정 3-4, 홈 0-1로 조 최하위 탈락",
        highlight: "europe",
      },
      { text: "리그컵도 뉴캐슬에 홈 0-3, 4라운드에서 일찍 정리" },
    ],
  },
  {
    key: "schedule",
    question: "일정이 문제인가?",
    answer: "맞다. 인터내셔널 브레이크로 숨 돌리기 전까지는 3~4일 간격의 연속이었다.",
    highlight: "congestion",
    evidence: [
      {
        text: "밀집 구간 안에서는 경기 간격이 3~4일 — 회복할 시간이 없다",
        highlight: "congestion",
      },
      {
        text: "구간과 구간 사이 2주 공백(대표팀 차출)이 타임라인에서 확 벌어져 보인다",
        highlight: "congestion",
      },
      { text: "밀집이 풀린 뒤 1~2월 리그에서 성적이 다시 붙었다", highlight: "form" },
    ],
  },
  {
    key: "form",
    question: "최근 폼은?",
    answer: "시즌 막판은 들쭉날쭉. 리그는 8위로 마쳤지만 컵에서는 끝을 봤다.",
    highlight: "form",
    evidence: [
      { text: "최근 6경기 결과는 위 폼 스트릭 참고 (날짜순)", highlight: "form" },
      {
        text: "리그 최종 8위 — 클럽 프리미어리그 시대 최저 승점",
      },
      {
        text: "그래도 FA컵은 리버풀·코번트리를 연장·승부차기로 넘고 결승행",
        highlight: "europe",
      },
    ],
  },
  {
    key: "next",
    question: "다음 경기 무슨 각?",
    answer: "다음 경기? 이 시즌은 이미 끝났다 — 마지막 각은 FA컵 결승이었고, 제대로 잡았다.",
    highlight: null,
    evidence: [
      { text: "5/25 FA컵 결승, 맨체스터 시티 2-1 승 — 시즌 유일한 우승" },
      { text: "8강 리버풀 4-3(연장), 4강 코번트리 승부차기 — 험한 길로 올라간 결승", highlight: "europe" },
      { text: "예측·적중률 트래킹은 다음 시즌 실경기부터 (준비 중)" },
    ],
  },
];
