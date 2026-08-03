/**
 * 타임라인 뷰모델 — 화면이 소비하는 유일한 형태.
 *
 * 컴포넌트는 백엔드 응답(`TeamDiagnostics`)을 직접 만지지 않는다. `buildTimeline`이
 * 응답을 이 뷰모델로 한 번 옮기고, 컴포넌트는 여기 있는 값만 그린다(dumb view).
 *
 * ⚠️ 이 계층이 하는 일은 **표기**뿐이다 — 날짜 문자열, 여백 픽셀, "21일 7경기" 같은
 *    요약 문구. 승/무/패나 밀집 구간 같은 **판정은 하지 않는다**. 그건 백엔드가 이미
 *    끝냈고, 여기서 다시 하면 두 곳이 서로 다른 답을 내기 시작한다.
 */
import type {
  FixtureStatus,
  Omission,
  SampleConfidence,
} from "@/lib/api/types";

export type CompetitionKey = "league" | "cup" | "europe";

export interface Competition {
  key: CompetitionKey;
  /** 뱃지에 찍히는 짧은 라벨: "리그" · "FA컵" · "챔스" (백엔드 시드가 정한 표기). */
  label: string;
  /** 툴팁·문장용 긴 이름: "UEFA 챔피언스리그". */
  fullLabel: string;
}

export type MatchResult = "W" | "D" | "L";

/**
 * 대화·진단 근거 클릭이 타임라인의 어떤 경기를 강조할지 고르는 태그.
 * `travel`은 원정 경기 — "이 기간에 얼마나 돌아다녔나"를 눈으로 보게 한다.
 */
export type HighlightTag = "congestion" | "form" | "europe" | "travel";

/** 앞 경기와의 간격(카드 사이 여백)을 표현하는 값. */
export interface Gap {
  days: number;
  /** 비선형 스케일로 환산한 픽셀 높이. */
  px: number;
  /** 3~4일 이하 짧은 휴식 → 경고 톤. */
  shortRest: boolean;
  /** "14일 휴식" 또는 "3일". */
  label: string;
}

/** 밀집 구간 안에서 이 경기의 위치(브래킷 렌더링용). */
export type CongestionPos = "start" | "mid" | "end";

export interface RowCongestion {
  spanId: number;
  pos: CongestionPos;
}

/** 타임라인의 한 줄 = 경기 하나. */
export interface TimelineRow {
  id: number;
  /** ISO 원본(정렬/디버깅용). */
  date: string;
  /** "3/17". */
  dateLabel: string;
  /** 요일 "일". */
  dow: string;
  competition: Competition;
  /**
   * 홈/원정. **중립(N)은 없다** — API가 중립 플래그를 주지 않아 백엔드가 다루지 않는다.
   * 경기장 이름으로 추측하지 않는다(웸블리는 토트넘의 홈이었던 적이 있다).
   */
  homeAway: "H" | "A";
  /** 상대 팀명 — 한글 매핑 우선, 없으면 영문 원문(백엔드가 정해 준다). */
  opponent: string;
  /** 우리 관점 "4-3". 결과 미확정이면 null. */
  score: string | null;
  /** 우리 관점 승/무/패. **아직 안 치른 경기는 null** — 무승부로 접지 않는다. */
  result: MatchResult | null;
  /** 아직 결과가 없는 경기(예정·진행 중)인가. */
  pending: boolean;
  /** "예정" · "진행 중" · "연장" 등 상태 문구. 평범한 종료 경기는 null. */
  statusNote: string | null;
  /** "PK 승 4-2" — 승부차기가 있었을 때만. 결과 집계(result)와는 별개의 표시다. */
  shootoutNote: string | null;
  /** 앞 경기와의 간격(첫 경기는 null). */
  gap: Gap | null;
  /** 밀집 구간 소속(아니면 null). */
  congestion: RowCongestion | null;
  /** 강조 매칭 태그. */
  tags: HighlightTag[];
}

/** 병합된 밀집 구간 하나. */
export interface CongestionSpan {
  id: number;
  /** 구간 첫/마지막 경기의 id. 서버가 준 경계를 그대로 들고 있는다. */
  startFixtureId: number;
  endFixtureId: number;
  fromLabel: string;
  toLabel: string;
  spanDays: number;
  matchCount: number;
  awayCount: number;
  extraTimeCount: number;
  /** "21일 7경기 · 원정 3 · 연장 1". */
  summary: string;
}

/** 밀집 판정의 상태 — 구간이 없을 때 "여유로움"과 "판정 불가"를 가른다. */
export interface CongestionStatus {
  detectable: boolean;
  windowDays: number;
  minMatches: number;
  analyzedMatchCount: number;
  busiestWindowMatchCount: number;
  /** 전체에서 가장 짧았던 경기 간격(일). 경기가 2건 미만이면 null. */
  shortestGapDays: number | null;
  /** 간격의 중앙값(일). 평균이 아닌 이유는 서버 `CongestionReport` 주석 참고. */
  medianGapDays: number | null;
  /** "경기 3건뿐이라 판정하지 않았습니다" 또는 "가장 빡빡한 14일에 2경기". */
  note: string;
}

/** 누적 이동거리 — 부분합일 수 있어 "몇 경기를 쟀는지"를 함께 들고 다닌다. */
export interface Travel {
  awayMatches: number;
  measuredMatches: number;
  unknownCoordinateMatches: number;
  totalKm: number | null;
  /** "원정 1경기 262km" 또는 "좌표가 없어 재지 못함". */
  summary: string;
}

/** 최근 폼 — 개수와 표본 크기를 함께 들고 다닌다. */
export interface Form {
  /** 날짜 오름차순 승/무/패. 확정된 경기만. */
  recent: MatchResult[];
  sampleSize: number;
  requested: number;
  confidence: SampleConfidence;
  wins: number;
  draws: number;
  losses: number;
  points: number;
  maxPoints: number;
  /** "3승 1무 2패" — 0인 항목은 빠진다. */
  recordLabel: string;
  /** "6경기 3승 1무 2패 · 승점률 56%" — 표본이 작으면 비율이 빠진다. */
  summary: string;
  /** 승점률(0~1). 표본이 5경기 미만이면 null. */
  pointsRate: number | null;
}

/** 화면 상단이 쓰는 팀 요약. */
export interface TeamSummary {
  id: number;
  name: string;
  /** 팀 마크에 찍는 3글자. 백엔드에 없으면 이름에서 만든다. */
  code: string;
  /** "전 대회 통합 · 2024/08 – 2024/09 · 3경기". */
  subtitle: string;
}

/**
 * 진단 화면의 근거 카드 하나 (시안 1a 모드 2).
 * 큰 숫자 + 라벨 + 설명, 클릭하면 타임라인에서 해당 경기들이 강조된다.
 */
export interface DiagnosisCard {
  key: string;
  /** "3승 1무 2패" · "4,493km" — 크게 찍히는 값. */
  value: string;
  /** 값의 성격에 따른 색 계열. 화면이 토큰 클래스로 옮긴다. */
  tone: "win" | "loss" | "draw" | "warn" | "volt";
  label: string;
  detail: string;
  /** 클릭 시 강조할 태그. 강조할 게 없으면 null. */
  highlight: HighlightTag | null;
}

/** 계산하지 못했거나 애초에 수집하지 않는 지표 — "아는 척 안 함" 블록. */
export interface UnknownItem {
  label: string;
  /** 계산 생략 사유(백엔드 omission) 또는 "수집하지 않음". */
  reason: string;
  /** 우리가 아예 다루지 않는 영역인가(부상·전술 등). */
  structural: boolean;
}

export interface Diagnosis {
  /** 한 줄 결론. 지금은 규칙 기반 문장이고, AI 연동 시 이 자리만 바뀐다. */
  headline: string;
  sub: string;
  /** 결론이 AI가 쓴 것인지 — 화면이 출처를 밝힌다. */
  authored: "rule" | "ai";
  cards: DiagnosisCard[];
  unknowns: UnknownItem[];
}

export interface Timeline {
  team: TeamSummary;
  rows: TimelineRow[];
  spans: CongestionSpan[];
  /** 범례에 실제로 등장한 대회만. */
  competitionsPresent: Competition[];
  congestion: CongestionStatus;
  form: Form;
  travel: Travel;
  diagnosis: Diagnosis;
  /** 계산하지 못한 지표와 그 이유 — 화면이 정직하게 밝힌다. */
  omissions: Omission[];
  /** 아직 결과가 없는(예정) 경기 수. 꼬리말이 무엇을 말할지 정한다. */
  upcomingCount: number;
  /** 연기·취소로 계산에서 빠진 경기 수. */
  excludedCount: number;
}

export type { FixtureStatus, Omission, SampleConfidence };
