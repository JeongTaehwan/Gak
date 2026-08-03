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

/**
 * 결장 사유의 갈래.
 *
 * ⚠️ API 엔드포인트 이름은 `/injuries` 지만 부상만 오지 않는다 — 실제 응답(맨유 2023
 * 시즌 346건)에 징계 23건·질병 2건·기타(감독 결정 등) 10건이 섞여 있었다. 그래서
 * 화면에서도 "부상"이 아니라 "결장"으로 부르고, 갈래를 나눠 보여 준다.
 */
export type AbsenceReason =
  | "INJURY"
  | "SUSPENSION"
  | "ILLNESS"
  | "NATIONAL_DUTY"
  | "OTHER";

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
  /**
   * 이 경기에 빠진 확정 결장 인원.
   * **결장 데이터가 없는 경기는 null** — 0(아무도 안 빠짐)과 "모름"은 다르다.
   */
  absentCount: number | null;
}

export interface AbsentPlayer {
  playerId: number;
  /** 영문 원본. 선수는 수가 많고 이적이 잦아 한글 매핑을 두지 않는다. */
  playerName: string;
  matches: number;
  mainReason: AbsenceReason;
}

/**
 * 결장 요약.
 *
 * `coveredMatches`가 중요하다. API가 우리 경기 전부의 결장을 주지는 않는다 — 맨유
 * 2023 시즌은 52경기 중 44경기만 데이터가 있었다(컵대회 누락). "경기당 평균"을 쓰려면
 * 분모가 52가 아니라 44여야 하고, 화면은 그 사실을 밝혀야 한다.
 */
export interface AbsenceSummary {
  /** 결장 데이터가 하나라도 있는가. false면 "결장 0명"이 아니라 "모름"이다. */
  covered: boolean;
  coveredMatches: number;
  analyzedMatches: number;
  /** 확정 결장 연인원(경기 × 선수). 같은 선수가 5경기 빠지면 5다. */
  totalOut: number;
  distinctPlayers: number;
  maxOutInOneMatch: number;
  byReason: Partial<Record<AbsenceReason, number>>;
  topAbsentees: AbsentPlayer[];
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
  absences: AbsenceSummary;
  omissions: Omission[];
}

// ─────────────────────────────────────────────────────────────────────────────
// 예측 적중률 (`GET /api/teams/{id}/predictions`)
// ─────────────────────────────────────────────────────────────────────────────

export interface PredictionRecord {
  predictionId: number;
  fixtureId: number;
  kickoff: string;
  competitionName: string;
  competitionShortName: string;
  opponentName: string;
  home: boolean;
  status: FixtureStatus;
  pick: Pick;
  /** 실제 결과. 미채점이면 null. */
  resolvedResult: Pick | null;
  /** 적중 여부. 미채점이면 null. */
  isHit: boolean | null;
  createdAt: string;
  /**
   * 킥오프까지 남았던 시간(분). **항상 양수다** — 예측은 킥오프 이전에만 만들 수 있다.
   * 화면이 이걸 보여 주는 건 그 규칙이 지켜졌다는 걸 눈으로 확인시키기 위해서다.
   */
  leadTimeMinutes: number;
}

export interface PickAccuracy {
  predicted: number;
  hits: number;
  /** 표본 부족 시 null. */
  hitRate: number | null;
}

/**
 * 적중률.
 *
 * 승점률과 같은 규칙으로 표본이 5건 미만이면 `hitRate`가 null이다. 적중률은 이 앱이
 * 파는 숫자라 특히 그렇다 — 3번 맞히고 "적중률 100%"라고 적는 순간 앱이 스스로를
 * 과장하기 시작한다.
 */
export interface PredictionAccuracy {
  teamId: number;
  teamName: string;
  /** 채점 완료 = 비율의 분모. */
  scored: number;
  /** 채점을 기다리는 예측. 예측이 있는데 이게 안 줄면 채점이 멈춘 것이다. */
  pending: number;
  hits: number;
  misses: number;
  hitRate: number | null;
  confidence: SampleConfidence;
  byPick: Partial<Record<Pick, PickAccuracy>>;
  recent: PredictionRecord[];
}
