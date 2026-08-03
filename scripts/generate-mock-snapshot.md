# 개발용 목 스냅샷 다시 만들기

프론트의 `frontend/lib/api/mock/diagnostics-manutd-2324.json` 은 **백엔드가 실제로
계산해 내려준 응답을 그대로 저장한 파일**이다. 손으로 쓴 가짜 데이터가 아니다.

## 왜 이렇게 만드나

목을 위해 밀집도·폼을 TypeScript 로 다시 계산하면, 같은 규칙이 Java 와 TS 양쪽에
생긴다. 한쪽만 고치는 날이 오고, 그날부터 목 화면과 실 화면이 **다른 답**을 말한다.
그래서 계산은 백엔드에만 두고, 목은 그 출력을 찍어 둔 사진으로 만든다.

원재료는 `frontend/lib/api/mock/manutd-2324.ts` 의 맨유 2023-24 실제 52경기다.
이걸 API-Football 응답 모양으로 바꿔 백엔드 replay 모드에 태우면, 동기화 → 계산까지
**실제와 같은 경로**를 그대로 지난다.

```
manutd-2324.ts  ──(1)──▶  fixtures-league*-season2023.json
                              │
                              (2) 백엔드 replay 동기화 + 진단 계산
                              ▼
                          diagnostics-manutd-2324.json
```

## 언제 다시 만드나

- 백엔드 응답 DTO(`dto/analysis/*`)에 필드를 더하거나 이름을 바꿨을 때
- 계산 규칙(밀집 기준, 폼 집계 등)을 바꿨을 때

스냅샷은 사진이라 **자동으로 따라오지 않는다.** 안 고치면 목 화면에서만 값이 비고,
그건 실 화면에서는 안 보이는 종류의 버그다.

## 순서

DB 를 새로 만들 권한이 없어도 되도록, 같은 DB 안의 **별도 스키마**에서 돌리고 끝나면
지운다. `public` 스키마(개발용 실데이터)는 건드리지 않는다.

```bash
# 0) 작업용 경로
SCRATCH=$(mktemp -d)

# 1) 목 52경기 → API-Football 응답 파일 4개(리그·FA컵·리그컵·챔스)
node scripts/mock/generate-replay.mjs "$SCRATCH/replay"

# 2) 격리용 스키마
psql -h localhost -U gak -d gak -c "CREATE SCHEMA IF NOT EXISTS mockgen;"

# 3) 백엔드를 8081 에 임시로 띄운다 (스키마·응답파일·시즌만 갈아끼운다)
cd backend
./gradlew bootRun --console=plain --args="\
  --spring.profiles.active=local \
  --server.port=8081 \
  --spring.datasource.url=jdbc:postgresql://localhost:5432/gak?currentSchema=mockgen \
  --gak.sync.enabled=false \
  --gak.sync.season-override=2023 \
  --gak.api-football.replay-locations[0]=file:$SCRATCH/replay/"
```

다른 터미널에서:

```bash
# 4) 네 대회를 동기화한다 (replay 라 외부 요청 0회)
for c in 39 45 48 2; do curl -s -X POST http://localhost:8081/api/admin/sync/$c; echo; done

# 5) 진단 결과를 그대로 스냅샷으로 저장
curl -s http://localhost:8081/api/teams/33/diagnostics \
  | python3 -m json.tool --no-ensure-ascii \
  > frontend/lib/api/mock/diagnostics-manutd-2324.json

# 6) 정리 — 임시 백엔드를 Ctrl+C 로 끄고
psql -h localhost -U gak -d gak -c "DROP SCHEMA IF EXISTS mockgen CASCADE;"
rm -rf "$SCRATCH"
```

## 확인

목으로 화면을 띄워 본다.

```bash
echo "GAK_DATA_SOURCE=mock" >> frontend/.env.local
(cd frontend && npm run dev)
```

52경기 타임라인에 밀집 구간 3개(9~10월, 10~11월, 11~12월)가 앰버 브래킷으로 보이고,
4/21 코번트리전에 `PK 승 4-2` 가 붙어 있으면 제대로 만들어진 것이다.
다 봤으면 `.env.local` 의 그 줄을 지워 실제 백엔드로 되돌린다.

---

## 곁가지 — AI 진단을 로컬에서 실제로 호출해 보려면

같은 재료(목 52경기)를 **스냅샷이 아니라 DB에** 넣고 백엔드를 그 위에 띄우는 스크립트가
따로 있다.

```bash
./scripts/mock/load-into-db.sh          # 적재 + 백엔드 기동(:8080)
./scripts/mock/load-into-db.sh --clean  # 격리 스키마 삭제
```

replay 표본 데이터는 **확정 경기가 2건**뿐이라 AI 진단이 표본 게이트에 걸려 모델을
아예 부르지 않는다(5건 필요). 목 52경기를 태우면 확정 6건 · 밀집 구간 3개가 되어
게이트를 통과하므로, AI 호출 경로를 실제로 지나 볼 수 있다.

`public` 스키마(개발용 실데이터)는 건드리지 않는다. 8/21 개막에 맞춰 현재 시즌을
받으면 표본이 자연히 차므로 이 스크립트는 필요 없어진다.
