/**
 * 맨체스터 유나이티드 2023-24 시즌 — 전 대회 실제 경기 목 데이터.
 * (Premier League · FA Cup · EFL Cup · UEFA Champions League 조별리그)
 *
 * ⚠️ **화면이 직접 읽는 파일이 아니다.** 화면은 백엔드가 계산해 준 진단 응답만 그린다.
 *    이 파일은 그 진단 응답의 **개발용 스냅샷을 만드는 원재료**다 — 여기 적힌 52경기를
 *    API-Football 원본 모양 그대로 백엔드에 태우면, 백엔드의 진짜 계산 로직이 밀집
 *    구간·폼·이동거리를 계산해 준다. 그 결과를 그대로 저장한 것이
 *    `diagnostics-manutd-2324.json`이다.
 *    (만드는 법: `scripts/generate-mock-snapshot.md`)
 *
 *    이렇게 도는 이유: 목을 위해 TS로 밀집도·폼을 다시 계산하면 같은 규칙이 Java와 TS
 *    양쪽에 생긴다. 한쪽만 고치는 날 목 화면과 실 화면이 다른 답을 말하게 된다.
 *
 * 왜 "시드 + 확장기" 구조인가:
 *   API-Football `/fixtures` 원소는 필드가 많다(중첩 score/venue/teams…). 53경기를
 *   손으로 풀 shape 으로 적으면 읽기 어렵고 오타가 난다. 그래서 사람이 검수하기 쉬운
 *   납작한 시드 배열로 사실만 적고, expand()가 이를 정확한 FixtureResponse 로 부풀린다.
 *   → 소비 측(client)이 받는 건 언제나 진짜 API 와 같은 shape 이다.
 *
 * 데이터 출처: 2023-24 시즌 공식 기록(위키피디아 시즌 문서로 교차 확인).
 * 날짜가 사실이어야 "간격이 곧 일정" 시각화가 거짓말을 하지 않는다 — 경기를
 * 누락하면 없는 공백이 생기므로, 이 창(8/14–5/25) 안의 경기는 하나도 빼지 않았다.
 */
import type {
  FixtureResponse,
  FixtureStatusShort,
  Score,
} from "@/lib/api/mock/apiFootballTypes";

/** 우리 팀. */
export const MANCHESTER_UNITED_ID = 33;

/** 대회 코드 → API league.id / 표시명. (대회 종류 판별은 timeline 계층에서.) */
const LEAGUES = {
  PL: { id: 39, name: "Premier League", country: "England" },
  FA: { id: 45, name: "FA Cup", country: "England" },
  EFL: { id: 48, name: "League Cup", country: "England" },
  UCL: { id: 2, name: "UEFA Champions League", country: "World" },
} as const;
type LeagueCode = keyof typeof LEAGUES;

/** 상대팀 사전 — id(안정적 임의값) · 영문 원문 · 한글 매핑 · 홈 도시(원정 이동용). */
const OPP: Record<
  string,
  { id: number; name: string; ko: string; city: string | null }
> = {
  wolves: { id: 39, name: "Wolverhampton Wanderers", ko: "울버햄튼", city: "Wolverhampton" },
  spurs: { id: 47, name: "Tottenham Hotspur", ko: "토트넘", city: "London" },
  forest: { id: 65, name: "Nottingham Forest", ko: "노팅엄 포레스트", city: "Nottingham" },
  bayern: { id: 157, name: "Bayern München", ko: "바이에른 뮌헨", city: "Munich" },
  arsenal: { id: 42, name: "Arsenal", ko: "아스날", city: "London" },
  brighton: { id: 51, name: "Brighton & Hove Albion", ko: "브라이턴", city: "Brighton" },
  burnley: { id: 44, name: "Burnley", ko: "번리", city: "Burnley" },
  palace: { id: 52, name: "Crystal Palace", ko: "크리스탈 팰리스", city: "London" },
  galatasaray: { id: 645, name: "Galatasaray", ko: "갈라타사라이", city: "Istanbul" },
  brentford: { id: 55, name: "Brentford", ko: "브렌트포드", city: "London" },
  sheffield: { id: 62, name: "Sheffield Utd", ko: "셰필드 유나이티드", city: "Sheffield" },
  copenhagen: { id: 569, name: "FC Copenhagen", ko: "코펜하겐", city: "Copenhagen" },
  city: { id: 50, name: "Manchester City", ko: "맨체스터 시티", city: "Manchester" },
  newcastle: { id: 34, name: "Newcastle United", ko: "뉴캐슬", city: "Newcastle upon Tyne" },
  fulham: { id: 36, name: "Fulham", ko: "풀럼", city: "London" },
  luton: { id: 1359, name: "Luton Town", ko: "루턴", city: "Luton" },
  everton: { id: 45, name: "Everton", ko: "에버턴", city: "Liverpool" },
  chelsea: { id: 49, name: "Chelsea", ko: "첼시", city: "London" },
  bournemouth: { id: 35, name: "AFC Bournemouth", ko: "본머스", city: "Bournemouth" },
  liverpool: { id: 40, name: "Liverpool", ko: "리버풀", city: "Liverpool" },
  westham: { id: 48, name: "West Ham United", ko: "웨스트햄", city: "London" },
  villa: { id: 66, name: "Aston Villa", ko: "아스톤 빌라", city: "Birmingham" },
  wigan: { id: 1148, name: "Wigan Athletic", ko: "위건", city: "Wigan" },
  newport: { id: 1785, name: "Newport County", ko: "뉴포트 카운티", city: "Newport" },
  coventry: { id: 1343, name: "Coventry City", ko: "코번트리", city: "Coventry" },
};

/**
 * 시드 한 줄 = 실제 경기 하나.
 *   d   경기 날짜 YYYY-MM-DD
 *   t   현지 킥오프 HH:MM (일-단위 간격엔 무관하나 사실에 가깝게)
 *   c   대회 코드
 *   rnd league.round 문자열 (API 그대로의 형태)
 *   ha  'H' | 'A' | 'N'  (맨유 기준 홈/원정/중립)
 *   o   OPP 키
 *   gf  맨유 득점 (최종 — 연장 포함)
 *   ga  맨유 실점 (최종 — 연장 포함)
 *   st  상태코드 (FT 기본)
 *   ht  90분 스코어 [gf,ga] (연장/승부차기 경기에서만 명시)
 *   pen 승부차기 스코어 [맨유,상대] (PEN 경기에서만)
 */
interface Seed {
  d: string;
  t: string;
  c: LeagueCode;
  rnd: string;
  ha: "H" | "A" | "N";
  o: keyof typeof OPP;
  gf: number;
  ga: number;
  st?: FixtureStatusShort;
  ht?: [number, number];
  pen?: [number, number];
}

// prettier-ignore
const SEED: Seed[] = [
  { d: "2023-08-14", t: "20:00", c: "PL",  rnd: "Regular Season - 1",  ha: "H", o: "wolves",      gf: 1, ga: 0 },
  { d: "2023-08-19", t: "17:30", c: "PL",  rnd: "Regular Season - 2",  ha: "A", o: "spurs",       gf: 0, ga: 2 },
  { d: "2023-08-26", t: "15:00", c: "PL",  rnd: "Regular Season - 3",  ha: "H", o: "forest",      gf: 3, ga: 2 },
  { d: "2023-09-03", t: "16:30", c: "PL",  rnd: "Regular Season - 4",  ha: "A", o: "arsenal",     gf: 1, ga: 3 },
  { d: "2023-09-16", t: "15:00", c: "PL",  rnd: "Regular Season - 5",  ha: "H", o: "brighton",    gf: 1, ga: 3 },
  { d: "2023-09-20", t: "20:00", c: "UCL", rnd: "Group Stage - 1",     ha: "A", o: "bayern",      gf: 3, ga: 4 },
  { d: "2023-09-23", t: "15:00", c: "PL",  rnd: "Regular Season - 6",  ha: "A", o: "burnley",     gf: 1, ga: 0 },
  { d: "2023-09-26", t: "20:00", c: "EFL", rnd: "3rd Round",           ha: "H", o: "palace",      gf: 3, ga: 0 },
  { d: "2023-09-30", t: "15:00", c: "PL",  rnd: "Regular Season - 7",  ha: "H", o: "palace",      gf: 0, ga: 1 },
  { d: "2023-10-03", t: "20:00", c: "UCL", rnd: "Group Stage - 2",     ha: "H", o: "galatasaray", gf: 2, ga: 3 },
  { d: "2023-10-07", t: "15:00", c: "PL",  rnd: "Regular Season - 8",  ha: "H", o: "brentford",   gf: 2, ga: 1 },
  { d: "2023-10-21", t: "15:00", c: "PL",  rnd: "Regular Season - 9",  ha: "A", o: "sheffield",   gf: 2, ga: 1 },
  { d: "2023-10-24", t: "20:00", c: "UCL", rnd: "Group Stage - 3",     ha: "H", o: "copenhagen",  gf: 1, ga: 0 },
  { d: "2023-10-29", t: "16:30", c: "PL",  rnd: "Regular Season - 10", ha: "H", o: "city",        gf: 0, ga: 3 },
  { d: "2023-11-01", t: "20:15", c: "EFL", rnd: "4th Round",           ha: "H", o: "newcastle",   gf: 0, ga: 3 },
  { d: "2023-11-04", t: "15:00", c: "PL",  rnd: "Regular Season - 11", ha: "A", o: "fulham",      gf: 1, ga: 0 },
  { d: "2023-11-08", t: "20:00", c: "UCL", rnd: "Group Stage - 4",     ha: "A", o: "copenhagen",  gf: 3, ga: 4 },
  { d: "2023-11-11", t: "15:00", c: "PL",  rnd: "Regular Season - 12", ha: "H", o: "luton",       gf: 1, ga: 0 },
  { d: "2023-11-26", t: "14:00", c: "PL",  rnd: "Regular Season - 13", ha: "A", o: "everton",     gf: 3, ga: 0 },
  { d: "2023-11-29", t: "20:00", c: "UCL", rnd: "Group Stage - 5",     ha: "A", o: "galatasaray", gf: 3, ga: 3 },
  { d: "2023-12-02", t: "17:30", c: "PL",  rnd: "Regular Season - 14", ha: "A", o: "newcastle",   gf: 0, ga: 1 },
  { d: "2023-12-06", t: "20:15", c: "PL",  rnd: "Regular Season - 15", ha: "H", o: "chelsea",     gf: 2, ga: 1 },
  { d: "2023-12-09", t: "15:00", c: "PL",  rnd: "Regular Season - 16", ha: "H", o: "bournemouth", gf: 0, ga: 3 },
  { d: "2023-12-12", t: "20:00", c: "UCL", rnd: "Group Stage - 6",     ha: "H", o: "bayern",      gf: 0, ga: 1 },
  { d: "2023-12-17", t: "16:30", c: "PL",  rnd: "Regular Season - 17", ha: "A", o: "liverpool",   gf: 0, ga: 0 },
  { d: "2023-12-23", t: "12:30", c: "PL",  rnd: "Regular Season - 18", ha: "A", o: "westham",     gf: 0, ga: 2 },
  { d: "2023-12-26", t: "20:00", c: "PL",  rnd: "Regular Season - 19", ha: "H", o: "villa",       gf: 3, ga: 2 },
  { d: "2023-12-30", t: "15:00", c: "PL",  rnd: "Regular Season - 20", ha: "A", o: "forest",      gf: 2, ga: 1 },
  { d: "2024-01-08", t: "20:00", c: "FA",  rnd: "3rd Round",           ha: "A", o: "wigan",       gf: 2, ga: 0 },
  { d: "2024-01-14", t: "16:30", c: "PL",  rnd: "Regular Season - 21", ha: "H", o: "spurs",       gf: 2, ga: 2 },
  { d: "2024-01-28", t: "16:00", c: "FA",  rnd: "4th Round",           ha: "A", o: "newport",     gf: 4, ga: 2 },
  { d: "2024-02-01", t: "20:15", c: "PL",  rnd: "Regular Season - 22", ha: "A", o: "wolves",      gf: 4, ga: 3 },
  { d: "2024-02-04", t: "15:00", c: "PL",  rnd: "Regular Season - 23", ha: "H", o: "westham",     gf: 3, ga: 0 },
  { d: "2024-02-11", t: "13:30", c: "PL",  rnd: "Regular Season - 24", ha: "A", o: "villa",       gf: 2, ga: 1 },
  { d: "2024-02-18", t: "14:00", c: "PL",  rnd: "Regular Season - 25", ha: "A", o: "luton",       gf: 2, ga: 1 },
  { d: "2024-02-24", t: "15:00", c: "PL",  rnd: "Regular Season - 26", ha: "H", o: "fulham",      gf: 1, ga: 2 },
  { d: "2024-02-28", t: "19:30", c: "FA",  rnd: "5th Round",           ha: "A", o: "forest",      gf: 1, ga: 0 },
  { d: "2024-03-03", t: "15:30", c: "PL",  rnd: "Regular Season - 27", ha: "A", o: "city",        gf: 1, ga: 3 },
  { d: "2024-03-09", t: "15:00", c: "PL",  rnd: "Regular Season - 28", ha: "H", o: "everton",     gf: 2, ga: 0 },
  { d: "2024-03-17", t: "15:30", c: "FA",  rnd: "Quarter-finals",      ha: "H", o: "liverpool",   gf: 4, ga: 3, st: "AET", ht: [2, 2] },
  { d: "2024-03-30", t: "15:00", c: "PL",  rnd: "Regular Season - 30", ha: "A", o: "brentford",   gf: 1, ga: 1 },
  { d: "2024-04-04", t: "20:15", c: "PL",  rnd: "Regular Season - 29", ha: "A", o: "chelsea",     gf: 3, ga: 4 },
  { d: "2024-04-07", t: "15:30", c: "PL",  rnd: "Regular Season - 31", ha: "H", o: "liverpool",   gf: 2, ga: 2 },
  { d: "2024-04-13", t: "15:00", c: "PL",  rnd: "Regular Season - 32", ha: "A", o: "bournemouth", gf: 2, ga: 2 },
  { d: "2024-04-21", t: "15:00", c: "FA",  rnd: "Semi-finals",         ha: "N", o: "coventry",    gf: 3, ga: 3, st: "PEN", ht: [3, 3], pen: [4, 2] },
  { d: "2024-04-24", t: "20:00", c: "PL",  rnd: "Regular Season - 33", ha: "H", o: "sheffield",   gf: 4, ga: 2 },
  { d: "2024-04-27", t: "12:30", c: "PL",  rnd: "Regular Season - 34", ha: "H", o: "burnley",     gf: 1, ga: 1 },
  { d: "2024-05-06", t: "20:00", c: "PL",  rnd: "Regular Season - 35", ha: "A", o: "palace",      gf: 0, ga: 4 },
  { d: "2024-05-12", t: "16:00", c: "PL",  rnd: "Regular Season - 36", ha: "H", o: "arsenal",     gf: 0, ga: 1 },
  { d: "2024-05-15", t: "20:00", c: "PL",  rnd: "Regular Season - 37", ha: "H", o: "newcastle",   gf: 3, ga: 2 },
  { d: "2024-05-19", t: "16:00", c: "PL",  rnd: "Regular Season - 38", ha: "A", o: "brighton",    gf: 2, ga: 0 },
  { d: "2024-05-25", t: "15:00", c: "FA",  rnd: "Final",               ha: "N", o: "city",        gf: 2, ga: 1 },
];

const OLD_TRAFFORD = { id: 556, name: "Old Trafford", city: "Manchester" };
const WEMBLEY = { id: 4685, name: "Wembley Stadium", city: "London" };

/** null 도 명시적으로 채운 스코어 라인. */
function line(home: number | null, away: number | null) {
  return { home, away };
}

/** 시드 한 줄 → 정확한 API-Football FixtureResponse 로 확장. */
function expand(s: Seed, i: number): FixtureResponse {
  const opp = OPP[s.o];
  const league = LEAGUES[s.c];
  const isHome = s.ha === "H";
  const isNeutral = s.ha === "N";
  const status: FixtureStatusShort = s.st ?? "FT";

  // 맨유 관점 득/실 → 홈/원정 골로 환산.
  const homeGoals = isHome ? s.gf : s.ga;
  const awayGoals = isHome ? s.ga : s.gf;

  // 결과 판정(승부차기는 진출팀이 승). winner 플래그는 이 판정을 그대로 따른다.
  const wonOnPens = !!s.pen && s.pen[0] > s.pen[1];
  const munWin = wonOnPens || (s.gf > s.ga && !s.pen);
  const munLoss = (!!s.pen && s.pen[0] < s.pen[1]) || (s.gf < s.ga && !s.pen);
  const munWinner: boolean | null = munWin ? true : munLoss ? false : null;
  const oppWinner: boolean | null =
    munWinner === null ? null : !munWinner;

  const home = isHome
    ? { id: MANCHESTER_UNITED_ID, name: "Manchester United", winner: munWinner }
    : { id: opp.id, name: opp.name, winner: oppWinner };
  const away = isHome
    ? { id: opp.id, name: opp.name, winner: oppWinner }
    : { id: MANCHESTER_UNITED_ID, name: "Manchester United", winner: munWinner };

  // 90분 스코어(ht) 명시 시 fulltime 은 그것, 아니면 최종과 동일.
  const ftHome = s.ht ? (isHome ? s.ht[0] : s.ht[1]) : homeGoals;
  const ftAway = s.ht ? (isHome ? s.ht[1] : s.ht[0]) : awayGoals;

  const score: Score = {
    halftime: line(null, null),
    fulltime: line(ftHome, ftAway),
    extratime:
      status === "AET" || status === "PEN"
        ? line(homeGoals, awayGoals)
        : line(null, null),
    penalty: s.pen
      ? line(isHome ? s.pen[0] : s.pen[1], isHome ? s.pen[1] : s.pen[0])
      : line(null, null),
  };

  const venue = isNeutral
    ? WEMBLEY
    : isHome
      ? OLD_TRAFFORD
      : { id: 1000 + i, name: null, city: opp.city };

  const iso = `${s.d}T${s.t}:00+00:00`;
  return {
    fixture: {
      id: 1000000 + i,
      date: iso,
      timestamp: Math.floor(Date.parse(iso) / 1000),
      venue,
      status: {
        long:
          status === "AET"
            ? "Match Finished After Extra Time"
            : status === "PEN"
              ? "Match Finished After Penalty Shootout"
              : "Match Finished",
        short: status,
        elapsed: status === "AET" || status === "PEN" ? 120 : 90,
      },
    },
    league: {
      id: league.id,
      name: league.name,
      country: league.country,
      season: 2023,
      round: s.rnd,
    },
    teams: { home, away },
    goals: line(homeGoals, awayGoals),
    score,
  };
}

/** 맨유 2023-24 전 경기 — 날짜 오름차순의 FixtureResponse 배열. */
export const MANUTD_2324_FIXTURES: FixtureResponse[] = SEED.map(expand).sort(
  (a, b) => a.fixture.timestamp - b.fixture.timestamp,
);

/** 상대 영문명 → 한글 매핑 사전 (표기 규칙: 한글 우선, 없으면 영문 fallback). */
export const TEAM_NAME_KO: Record<string, string> = Object.fromEntries([
  ["Manchester United", "맨체스터 유나이티드"],
  ...Object.values(OPP).map((o) => [o.name, o.ko] as const),
]);
