/**
 * 목 데이터(맨유 2023-24, 52경기) → API-Football 응답 파일 4개.
 *
 * 이 파일들을 백엔드 replay 모드로 태우면 **백엔드의 진짜 계산 로직**이 밀집 구간·폼·
 * 이동거리를 계산해 준다. 그 결과를 저장한 것이 프론트의 개발용 스냅샷
 * (`frontend/lib/api/mock/diagnostics-manutd-2324.json`)이다.
 *
 * 목을 위해 TS로 밀집도·폼을 다시 계산하지 않는 이유가 이 우회의 전부다 — 같은 규칙이
 * Java와 TS 양쪽에 있으면 한쪽만 고치는 날이 오고, 그날부터 목 화면과 실 화면이 다른
 * 답을 말한다.
 *
 * 사용법은 `scripts/generate-mock-snapshot.md` 참고.
 *
 *   node scripts/mock/generate-replay.mjs <출력 디렉터리>
 */
import { writeFileSync, mkdirSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const mockPath = resolve(here, "../../frontend/lib/api/mock/manutd-2324.ts");
const injuriesPath = resolve(
  here,
  "../../backend/src/test/resources/apifootball/injuries-team33-season2023.json",
);

// Node 22.6+ 는 .ts 를 타입만 벗겨 내고 그대로 실행한다(별도 빌드 도구 불필요).
// manutd-2324.ts 의 import 는 전부 `import type` 이라 경로 별칭(@/...) 해석도 필요 없다.
const { MANUTD_2324_FIXTURES } = await import(mockPath);

const out = process.argv[2];
if (!out) {
  console.error("사용법: node scripts/mock/generate-replay.mjs <출력 디렉터리>");
  process.exit(1);
}
mkdirSync(out, { recursive: true });

const byLeague = new Map();

for (const fx of MANUTD_2324_FIXTURES) {
  // 목에서는 원정 경기장 이름이 비어 있다. 실제 API 응답에는 항상 이름이 들어오고,
  // 백엔드는 이름 없는 경기장을 "미정"으로 보고 버린다 → 이동거리가 통째로 빠진다.
  // 실제 응답에 가깝게 채워 둔다. 좌표는 백엔드가 도시 시드에서 알아서 붙인다
  // (시드에 없는 도시는 null 로 남고, 그게 "부분합" 경로를 실제로 태워 준다).
  const v = fx.fixture.venue;
  const venue = v.name === null && v.city ? { ...v, name: `${v.city} Stadium` } : v;

  const item = { ...fx, fixture: { ...fx.fixture, venue } };
  const list = byLeague.get(fx.league.id) ?? [];
  list.push(item);
  byLeague.set(fx.league.id, list);
}

for (const [leagueId, response] of byLeague) {
  const envelope = {
    get: "fixtures",
    parameters: { league: String(leagueId), season: "2023" },
    errors: [],
    results: response.length,
    paging: { current: 1, total: 1 },
    response,
  };
  const name = `fixtures-league${leagueId}-season2023.json`;
  writeFileSync(`${out}/${name}`, JSON.stringify(envelope, null, 1));
  console.log(`${name}  ${response.length}경기`);
}

// ── 결장(/injuries) ─────────────────────────────────────────────────────────
// 실제 응답은 진짜 경기 id(1035046 …)를 쓰는데 목 데이터는 합성 id(1000000+i)를 쓴다.
// 둘 다 맨유 2023-24 실제 시즌이라 **날짜로는 정확히 맞물린다**. 그래서 날짜를 열쇠로
// 경기 id 만 목 쪽으로 갈아끼운다 — 결장 사실 자체(선수·사유·확정 여부)는 손대지 않는다.
//
// 실 파이프라인에서는 이런 변환이 필요 없다. 실제 경기와 실제 결장이 같은 id 를 쓰기
// 때문이다. 여기서만 필요한 목 데이터 준비 작업이라 생성기 안에 둔다.
const idByDate = new Map(
  MANUTD_2324_FIXTURES.map((f) => [f.fixture.date.slice(0, 10), f.fixture.id]),
);

const injuries = JSON.parse(readFileSync(injuriesPath, "utf8"));
const remapped = [];
const unmatchedDates = new Set();

for (const item of injuries.response) {
  const date = item.fixture.date.slice(0, 10);
  const mockFixtureId = idByDate.get(date);
  if (mockFixtureId === undefined) {
    unmatchedDates.add(date);
    continue;
  }
  remapped.push({ ...item, fixture: { ...item.fixture, id: mockFixtureId } });
}

const injuryEnvelope = {
  get: "injuries",
  parameters: { team: "33", season: "2023" },
  errors: [],
  results: remapped.length,
  paging: { current: 1, total: 1 },
  response: remapped,
};
const injuryName = "injuries-team33-season2023.json";
writeFileSync(`${out}/${injuryName}`, JSON.stringify(injuryEnvelope, null, 1));
console.log(
  `${injuryName}  결장 ${remapped.length}건` +
    (unmatchedDates.size
      ? ` (날짜가 목 일정에 없어 제외 ${unmatchedDates.size}일)`
      : ""),
);
