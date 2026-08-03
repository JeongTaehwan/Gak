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
  /**
   * 이 경기 **직전**의 상대 리그 순위.
   *
   * 컵 대회이거나 시즌 초라 순위를 말할 수 없으면 **null**이다. 0이나 -1이 아니다 —
   * 화면이 그걸 순위로 그리면 안 된다.
   */
  opponentRank: number | null;
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

/**
 * 최근 폼 구간에서 **어떤 상대를 만났나**.
 *
 * "6경기 4패"까지만 말하면 팀이 무너진 것처럼 읽힌다. "4패인데 그중 1경기만 상위권
 * 상대였다"가 되면 같은 숫자가 다른 말을 한다.
 *
 * 순위는 **그 경기 시점**의 것이다. 시즌 최종 순위를 쓰면 그 경기 시점에 존재하지
 * 않던 정보로 과거를 판단하게 된다 — 이 앱이 예측에서 이미 막아 둔 문제다.
 */
export interface OpponentStrength {
  /** 순위를 알아낸 경기 수. 0이면 이 지표를 그리지 않는다. */
  measured: number;
  /** 순위를 못 매긴 경기 수(컵 대회이거나 시즌 초). **분모에서 빠졌다는 뜻**이다. */
  unmeasured: number;
  /** 만난 상대들의 평균 순위. measured가 0이면 null. */
  averageRank: number | null;
  /** 순위의 분모(그 리그 팀 수). "20팀 중 3위"와 "8팀 중 3위"는 다르다. */
  tableSize: number | null;
  /** "상위권"의 경계 순위(표 크기의 30%). 20팀이면 6. */
  topCut: number | null;
  vsTop: StrengthSplit;
  vsRest: StrengthSplit;
  /** 승점 삭감을 반영했는가. false면 순위가 실제와 다를 수 있다. */
  deductionsKnown: boolean;
}

export interface StrengthSplit {
  matches: number;
  wins: number;
  draws: number;
  losses: number;
  points: number;
  maxPoints: number;
  /** 표본이 얇으면 **null** — 2경기 1승을 "50%"로 적으면 소수점이 빈약함을 가린다. */
  pointsRate: number | null;
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
  opponentStrength: OpponentStrength;
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

/**
 * AI가 쓴 진단 서술 — `GET /api/teams/{teamId}/diagnosis`.
 *
 * `available: false`가 **정상 응답**이다. 키가 없거나, 표본이 부족하거나, 모델이 느렸을
 * 때 전부 여기로 온다. 그때 화면은 규칙 기반 문장(`lib/diagnosis/summarize.ts`)을
 * 그대로 두고 배지도 "규칙 기반"으로 유지한다.
 */
export interface AiDiagnosis {
  available: boolean;
  /** 못 만든 이유. 사용자에게 그대로 보여줄 수 있는 한국어. */
  unavailableReason: string | null;
  headline: string | null;
  sub: string | null;
  /** 결론의 근거가 된 지표. 백엔드 스키마가 비어 있지 않음을 강제한다. */
  evidence: AiEvidence[];
  /** 이 결론을 더 확실히 하려면 필요하지만 우리가 갖고 있지 않은 정보. */
  unknowns: string[];
}

export interface AiEvidence {
  claim: string;
  /** 근거가 된 지표 이름 — "구간 내 최단 간격". */
  metric: string;
  /** 그 값 — "2일". */
  value: string;
}

/**
 * 순위표 — `GET /api/teams/{teamId}/standings`.
 *
 * **진단의 상대 강도와 출처가 다르다.** 저쪽은 우리가 경기 결과로 계산한 "그 경기 시점"
 * 순위이고, 이건 API가 준 지금 순위다. 둘이 어긋날 수 있는데 버그가 아니라 서로 다른
 * 질문에 답하는 값이다 — 그래서 화면이 `updatedAt`으로 "언제 기준"인지 밝혀야 한다.
 */
export interface StandingsTable {
  available: boolean;
  /** 없을 때 사유. 화면에 그대로 띄울 수 있는 한국어. */
  unavailableReason: string | null;
  competitionId: number;
  competitionName: string | null;
  season: number | null;
  rows: StandingRow[];
  /** 이 표를 마지막으로 받아 온 시각. null이면 표가 없다. */
  updatedAt: string | null;
}

export interface StandingRow {
  rank: number;
  teamId: number;
  teamName: string;
  teamCode: string | null;
  played: number;
  points: number;
  goalsFor: number;
  goalsAgainst: number;
  goalsDiff: number;
  /** API가 붙인 순위의 의미("Promotion - Champions League" 등). 없을 수 있다. */
  description: string | null;
  /** 지금 보고 있는 팀인가 — 20줄 중 어디를 봐야 하는지. */
  highlighted: boolean;
}

/* ─────────────────────────────────────────────────────────────
   뉴스 — `GET /api/teams/{teamId}/news`
   ───────────────────────────────────────────────────────────── */

/**
 * 소식의 갈래. **다섯 개뿐이고 여기서 늘지 않는다.**
 *
 * 서버가 LLM으로 붙이지만 출력 공간이 닫혀 있어, 최악의 실패가 "배지가 잘못 붙음"으로
 * 묶인다. `null`은 오류가 아니라 **아직 안 붙었거나 못 붙은 정상 상태**다.
 */
export type NewsCategory = "TRANSFER" | "SQUAD" | "MATCH" | "CLUB" | "OTHER";

/** 출처 등급. 같은 문장이라도 누가 말했느냐로 무게가 다르다. */
export type SourceTier = "OFFICIAL" | "MEDIA";

/**
 * 소식 한 건. **우리가 쓴 문장이 아니다.**
 *
 * 본문·요약은 없다. 서버가 저장하지 않고, 애초에 파싱하지도 않는다 —
 * 제목만으로 원문을 대체하지 않는 것이 이 층이 존재할 수 있는 근거다.
 */
export interface NewsItem {
  title: string;
  /** 원문 링크. 사용자는 여기서 우리 사이트를 떠난다 — 그게 설계다. */
  link: string;
  publishedAt: string;
  /** 화면에 **반드시** 보여야 한다. 누가 쓴 글인지 감추지 않는다. */
  sourceName: string;
  tier: SourceTier;
  /** null이면 배지 없이 그린다. */
  category: NewsCategory | null;
}

/**
 * 우리가 가진 소식의 범위.
 *
 * 목록이 비었을 때 **왜 비었는지**를 화면이 말할 수 있게 하는 값이다.
 * 개수만 봐서는 "버그"·"아직 수집 전"·"보관 기간 밖"·"보는 시즌과 시점이 다름"을
 * 구분할 수 없다.
 */
export interface NewsCoverage {
  from: string | null;
  to: string | null;
  totalItems: number;
  retentionDays: number;
}

export interface TeamNewsResponse {
  items: NewsItem[];
  coverage: NewsCoverage;
}
