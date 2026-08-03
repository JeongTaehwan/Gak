/**
 * 표기 헬퍼 — **문자열을 만드는 일만** 한다.
 *
 * 예전엔 여기서 대회 종류와 승/무/패를 판정했다. 지금은 그 판정이 전부 백엔드에 있다.
 * 같은 규칙이 Java와 TS 양쪽에 있으면 한쪽만 고쳤을 때 화면과 AI 진단이 서로 다른
 * 답을 말하게 되기 때문이다. 여기 남은 건 "숫자를 사람이 읽는 말로 옮기는" 일뿐이다.
 *
 * 날짜는 전부 **UTC 기준**으로 옮긴다 — 백엔드가 UTC로 저장·계산하므로, 화면이 현지
 * 시간대로 옮겨 적으면 서버가 "8/17"이라 센 경기가 화면에서 "8/16"이 된다.
 */
import type {
  CompetitionType,
  FixtureStatus,
  MatchLoad,
  SampleConfidence,
} from "@/lib/api/types";
import type { Competition, CompetitionKey } from "@/lib/timeline/types";

const DOW_KO = ["일", "월", "화", "수", "목", "금", "토"] as const;

/** ISO → "3/17" (UTC 기준 — 백엔드의 집계 기준과 같게). */
export function toDateLabel(iso: string): string {
  const d = new Date(iso);
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()}`;
}

/** ISO → 요일 "일". */
export function toDow(iso: string): string {
  return DOW_KO[new Date(iso).getUTCDay()];
}

/** ISO → "2024/08". 기간 표기용. */
export function toMonthLabel(iso: string): string {
  const d = new Date(iso);
  return `${d.getUTCFullYear()}/${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
}

/**
 * 대회 성격 → 화면의 색 그룹.
 * 유럽대항전은 백엔드에서 HYBRID(조별리그+녹아웃)로 온다 — API의 League/Cup 두 값으론
 * 표현할 수 없어 시드가 직접 정한 값이다.
 */
const KEY_BY_TYPE: Record<CompetitionType, CompetitionKey> = {
  LEAGUE: "league",
  CUP: "cup",
  HYBRID: "europe",
};

export function toCompetition(match: MatchLoad): Competition {
  return {
    key: KEY_BY_TYPE[match.competitionType] ?? "league",
    label: match.competitionShortName,
    fullLabel: match.competitionName,
  };
}

/** 우리 관점 스코어 "4-3". 결과가 확정되지 않았으면 null(0-0으로 접지 않는다). */
export function toScore(match: MatchLoad): string | null {
  if (match.goalsFor == null || match.goalsAgainst == null) {
    return null;
  }
  return `${match.goalsFor}-${match.goalsAgainst}`;
}

/**
 * 상태 문구. 평범하게 끝난 경기(FT)는 붙이지 않는다 — 대부분이 그렇고,
 * 모든 줄에 "종료"가 붙으면 정작 특별한 줄(예정·연장)이 눈에 띄지 않는다.
 */
const STATUS_NOTE: Partial<Record<FixtureStatus, string>> = {
  NS: "예정",
  LIVE: "진행 중",
  AET: "연장",
  PEN: "연장",
  PST: "연기",
  CANC: "취소",
  ABD: "중단",
};

export function toStatusNote(status: FixtureStatus): string | null {
  return STATUS_NOTE[status] ?? null;
}

/**
 * 승부차기 표기 — "PK 승 4-2".
 *
 * 결과 집계에서 승부차기는 무승부(D)다. 120분을 뛴 부하가 폼에서 지워지면 안 되기
 * 때문이다. 하지만 화면까지 진출 여부를 감추면 사실을 빠뜨리는 것이라, 집계와 표시를
 * 갈라 여기서 따로 적는다.
 */
export function toShootoutNote(match: MatchLoad): string | null {
  const { shootoutFor: forGoals, shootoutAgainst: against } = match;
  if (forGoals == null || against == null) {
    return null;
  }
  const verdict = forGoals > against ? "승" : forGoals < against ? "패" : "";
  return `PK ${verdict} ${forGoals}-${against}`.replace("  ", " ");
}

/** "21일 7경기 · 원정 3 · 연장 1" — 있는 것만 잇는다. */
export function toSpanSummary(span: {
  spanDays: number;
  matchCount: number;
  awayCount: number;
  extraTimeMatchCount: number;
}): string {
  return [
    `${span.spanDays}일 ${span.matchCount}경기`,
    span.awayCount > 0 ? `원정 ${span.awayCount}` : null,
    span.extraTimeMatchCount > 0 ? `연장 ${span.extraTimeMatchCount}` : null,
  ]
    .filter(Boolean)
    .join(" · ");
}

/**
 * 폼 요약 — 표본이 작으면 비율 대신 개수로 말한다.
 * "3경기 2승 1패"는 표본이 작다는 사실까지 같이 보여 주지만,
 * "승점률 66.7%"는 소수점 한 자리의 정밀함으로 그 사실을 가린다.
 */
export function toFormSummary(form: {
  sampleSize: number;
  wins: number;
  draws: number;
  losses: number;
  pointsRate: number | null;
}): string {
  if (form.sampleSize === 0) {
    return "확정된 경기 없음";
  }
  const base = `${form.sampleSize}경기 ${toRecordLabel(form)}`;
  return form.pointsRate == null
    ? base
    : `${base} · 승점률 ${Math.round(form.pointsRate * 100)}%`;
}

/** "3승 1무 2패" — 0인 항목은 적지 않는다("0무"는 정보가 아니라 잡음이다). */
export function toRecordLabel(form: {
  wins: number;
  draws: number;
  losses: number;
}): string {
  return (
    [
      form.wins > 0 ? `${form.wins}승` : null,
      form.draws > 0 ? `${form.draws}무` : null,
      form.losses > 0 ? `${form.losses}패` : null,
    ]
      .filter(Boolean)
      .join(" ") || "기록 없음"
  );
}

/** 표본 등급을 한 마디로. 화면이 숫자 옆에 붙여 "얼마나 믿을 값인지"를 밝힌다. */
export const CONFIDENCE_LABEL: Record<SampleConfidence, string> = {
  NONE: "표본 없음",
  LOW: "표본 적음",
  MODERATE: "표본 보통",
  SUFFICIENT: "표본 충분",
};

/**
 * 팀 코드가 없을 때 이름에서 3글자를 만든다(팀 마크용).
 * 백엔드도 같은 상황에서 코드를 만들지만, 그 값이 비어 오는 경우까지 화면이 빈
 * 동그라미를 그리지 않도록 여기서 한 번 더 받아 둔다.
 */
export function toTeamCode(name: string, code: string | null): string {
  if (code && code.trim()) {
    return code.trim().toUpperCase();
  }
  const compact = name.replace(/\s+/g, "");
  return compact.slice(0, 3) || "?";
}
