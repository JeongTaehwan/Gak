# 저장해 둔 API-Football 응답 (replay)

무료 티어는 **하루 100요청**이다. 개발 중 앱을 몇 번 재시작하거나 테스트를 몇 번 돌리면
그날 예산이 사라진다. 그래서 이 폴더의 파일을 읽어 동작하는 재생 모드가 **기본값**이다.

```
gak.api-football.mode = replay   # 기본. 파일을 읽는다. 요청 0회
gak.api-football.mode = real     # 실제 호출. 호출마다 1요청
```

## 파일명 규약

| 파일 | 내용 |
|---|---|
| `fixtures-league{리그id}-season{시즌}.json` | `GET /fixtures?league=&season=` 응답 |
| `leagues-raw.json` | `GET /leagues` 응답(940개 대회). 시드 id 검증용 |

`ReplayResources.fixturesFileName()` 이 이 규약의 단일 출처다. 저장(capture)과 재생이 같은
함수를 쓰므로 이름이 어긋날 일이 없다.

## 파일 늘리는 법 — 요청을 낭비하지 않고

`real` 모드로 한 번 돌리면서 `capture-dir` 를 지정하면, 받은 원본이 그대로 여기 떨어진다.
동기화에 이미 쓴 응답을 저장만 더 하는 것이라 **추가 요청이 들지 않는다**.

```bash
./gradlew bootRun --args='--spring.profiles.active=local \
  --gak.api-football.mode=real \
  --gak.api-football.capture-dir=src/test/resources/apifootball \
  --gak.sync.max-competitions-per-run=1'
```

한 번에 대회 하나씩만 받도록 `max-competitions-per-run=1` 을 걸어 두는 걸 권한다.
무료 플랜이 최신 시즌을 막으면(`"Free plans do not have access to this season"`)
`--gak.sync.season-override=2023` 처럼 접근 가능한 시즌으로 내려서 받는다.

## 현재 들어 있는 표본

파이프라인의 갈래를 전부 한 번씩 태우도록 골랐다.

- **`fixtures-league39-season2024.json`** (프리미어리그, LEAGUE)
  종료(FT) / 진행 중(2H, elapsed) / 예정(NS, 득점 null) / 연기(PST) 가 섞여 있다.
- **`fixtures-league2-season2024.json`** (챔피언스리그, HYBRID)
  조별(League Stage) + 녹아웃(Round of 16) 라운드, 승부차기까지 간 경기(연장·PK 스코어),
  대진 미확정이라 **경기장이 null** 인 경기.
- **`fixtures-league39-season2019.json`** (오류 응답)
  **HTTP 200 인데 body 에 `errors` 가 실린** 실제 형태.
  `errors` 는 성공 시 `[]`(배열), 실패 시 `{}`(객체)로 와서 같은 키의 타입이 바뀐다.
  이걸 확인하지 않으면 `response: []` 를 "경기 0건"으로 오해해 SUCCESS 를 남기게 된다.
