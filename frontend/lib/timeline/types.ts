/**
 * 타임라인 뷰모델 — 화면이 소비하는 유일한 형태.
 *
 * 컴포넌트는 API-Football 원형(FixtureResponse)을 직접 만지지 않는다. buildTimeline
 * 이 원형을 이 뷰모델로 한 번 변환하고, 컴포넌트는 여기 있는 값만 그린다(dumb view).
 * 덕분에 목→실제 API 교체 시에도 컴포넌트는 그대로다.
 */

export type CompetitionKey = "league" | "cup" | "europe";

export interface Competition {
  key: CompetitionKey;
  /** 뱃지에 찍히는 한글 라벨: "리그" · "FA컵" · "리그컵" · "챔스". */
  label: string;
}

export type MatchResult = "W" | "D" | "L";

/** 대화·근거 클릭이 타임라인의 어떤 경기를 강조할지 고르는 태그. */
export type HighlightTag = "congestion" | "form" | "europe";

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
  homeAway: "H" | "A" | "N";
  /** 상대 팀명 — 한글 매핑 우선, 없으면 영문 원문 fallback. */
  opponent: string;
  /** 맨유 관점 "4-3". */
  score: string;
  result: MatchResult;
  /** "연장" · "승부차기" 등, 없으면 null. */
  note: string | null;
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
  startIdx: number;
  endIdx: number;
  fromLabel: string;
  toLabel: string;
  spanDays: number;
  matchCount: number;
  awayCount: number;
  extraTimeCount: number;
  /** "21일 7경기 · 원정 3 · 연장 1". */
  summary: string;
}

export interface Timeline {
  rows: TimelineRow[];
  spans: CongestionSpan[];
  /** 범례에 실제로 등장한 대회만. */
  competitionsPresent: Competition[];
  /** 최근 6경기 결과(날짜 오름차순). */
  form: MatchResult[];
}
