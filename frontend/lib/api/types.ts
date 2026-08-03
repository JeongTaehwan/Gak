/**
 * 백엔드(`GET /api/teams/{id}/diagnostics`) 응답의 계약.
 *
 * ⚠️ 이 파일은 **서버가 주는 모양**을 그대로 옮긴 것이다. 손으로 옮겨 적은 타입이라
 *    서버 record를 고치면 여기도 같이 고쳐야 한다. 어긋나면 화면에 `undefined`가
 *    조용히 그려지므로, 서버 쪽 `TeamDiagnosticsControllerTest`가 JSON 키 이름을
 *    함께 못박아 둔다(컴파일러가 잡아 주지 않는 유일한 경계).
 *
 * 여기엔 **숫자와 사실만** 있다. "3/17"·"3일 휴식"·"밀집 구간 21일 7경기" 같은 표기
 * 문자열은 없다 — 그건 `lib/timeline`이 만든다.
 *
 * 반대로 **판정은 여기 없다**. 승/무/패, 홈/원정, 대회 성격, 밀집 구간은 전부 서버가
 * 정해서 내려준다. 같은 규칙을 프론트에도 두면 한쪽만 고쳤을 때 타임라인과 (같은
 * 데이터를 읽는) AI 진단이 서로 다른 답을 말하게 된다. 사실의 출처는 하나여야 한다.
 */

/** 경기 상태. 서버 enum(`FixtureStatus`)을 그대로 받는다. */
export type FixtureStatus =
  | "NS" // 예정
  | "LIVE" // 진행 중
  | "FT" // 정규시간 종료
  | "AET" // 연장 종료
  | "PEN" // 승부차기 종료
  | "PST" // 연기
  | "CANC" // 취소
  | "ABD"; // 중단·미확정

/** 대회 성격. API가 주지 않아 시드가 정하는 값(유럽대항전 = 조별+녹아웃 = HYBRID). */
export type CompetitionType = "LEAGUE" | "CUP" | "HYBRID";

/** 우리 팀 관점 승·무·패. */
export type Pick = "W" | "D" | "L";

/** 표본 크기 등급 — 같은 "승점률 100%"라도 2경기와 20경기는 다른 말이다. */
export type SampleConfidence = "NONE" | "LOW" | "MODERATE" | "SUFFICIENT";

/** 한 경기가 이 팀 일정에 얹은 부하 = 타임라인 한 줄의 원재료. */
export interface MatchLoad {
  fixtureId: number;
  /** ISO 8601(UTC). 정렬·간격의 단일 근거. */
  kickoff: string;
  competitionId: number;
  /** "UEFA 챔피언스리그" — 문장·툴팁용 긴 이름. */
  competitionName: string;
  /** "챔스" — 뱃지처럼 폭이 좁은 자리용. */
  competitionShortName: string;
  competitionType: CompetitionType;
  opponentId: number;
  opponentName: string;
  home: boolean;
  status: FixtureStatus;
  /** 결과 미확정(예정·진행 중)이면 null. */
  result: Pick | null;
  goalsFor: number | null;
  goalsAgainst: number | null;
  /** 승부차기가 없었으면 null. 결과 집계(result)와는 별개의 값이다. */
  shootoutFor: number | null;
  shootoutAgainst: number | null;
  /** 직전 경기와의 간격(일). 목록의 첫 경기는 null. */
  gapDays: number | null;
  /** 소속 밀집 구간 id. 밀집이 아니면 null. */
  congestionSpanId: number | null;
  extraMinutes: number;
  /** 홈경기는 0, 좌표를 모르면 null(0과 구분된다). */
  travelKm: number | null;
}

/**
 * 병합된 밀집 구간 하나. 경계를 **인덱스가 아니라 경기 id로** 준다 —
 * 인덱스는 "누구의 어떤 목록에서 몇 번째"인지에 의존하는데, 서버는 연기·취소 경기를
 * 빼고 세므로 화면의 번호와 어긋난다. id는 그런 전제 없이 늘 같은 경기를 가리킨다.
 */
export interface CongestionSpanView {
  id: number;
  startFixtureId: number;
  endFixtureId: number;
  from: string;
  to: string;
  spanDays: number;
  matchCount: number;
  awayCount: number;
  extraTimeMatchCount: number;
  extraMinutes: number;
  shortestGapDays: number;
  /** 좌표를 모르는 경기가 있으면 부분합. */
  travelKm: number | null;
  travelUnknownCount: number;
}

export interface CongestionReport {
  windowDays: number;
  minMatches: number;
  /** 판정이 가능한 표본이었는가. false면 "여유로운 일정"이 아니라 "판정 불가"다. */
  detectable: boolean;
  analyzedMatchCount: number;
  busiestWindowMatchCount: number;
  shortestGapDays: number | null;
  medianGapDays: number | null;
  spans: CongestionSpanView[];
}

export interface FormSummary {
  requested: number;
  /** 결과가 확정된 경기만 센다 — 예정 경기는 폼에 들어가지 않는다. */
  sampleSize: number;
  /** 날짜 오름차순. 폼 스트릭이 그대로 그린다. */
  recent: Pick[];
  wins: number;
  draws: number;
  losses: number;
  points: number;
  maxPoints: number;
  /** 표본 5경기 미만이면 null — 개수만 준다. */
  pointsRate: number | null;
  opponentStrength: number | null;
  confidence: SampleConfidence;
}

export interface TravelSummary {
  from: string | null;
  to: string | null;
  awayMatches: number;
  measuredMatches: number;
  unknownCoordinateMatches: number;
  totalKm: number | null;
  averageKmPerMeasuredMatch: number | null;
  longestTripKm: number | null;
}

/** 이번 계산이 무엇을 보고 무엇을 뺐는지 — 숫자보다 먼저 읽혀야 하는 값. */
export interface AnalysisWindow {
  from: string | null;
  to: string | null;
  totalFixtures: number;
  analyzedFixtures: number;
  excludedFixtures: number;
}

/** 계산하지 **못한** 지표와 그 이유. 모르는 것을 0으로 채우지 않기 위한 장치. */
export interface Omission {
  metric: string;
  reason: string;
}

export interface TeamDiagnostics {
  teamId: number;
  teamName: string;
  teamCode: string | null;
  generatedAt: string;
  window: AnalysisWindow;
  matches: MatchLoad[];
  congestion: CongestionReport;
  form: FormSummary;
  travel: TravelSummary;
  omissions: Omission[];
}
