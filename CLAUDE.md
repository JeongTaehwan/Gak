# 각 (Gak) — 프로젝트 가이드

축구 팀 진단·예측 웹앱. **한국어 전용** 서비스.

리그·컵·유럽대항전을 **가로질러** 한 팀의 실제 일정을 하나로 통합하고, 일정
밀집도를 근거로 부진 원인을 **진단**하며, 다음 경기를 **예측**하고 **적중률**을
추적한다.

## 모노레포 구조

```
gak/
├── backend/    Spring Boot 3.5 · Java 21 · Gradle   (REST API)
└── frontend/   Next.js 15 · TypeScript · Tailwind v4 · @usetaehwan/ui
```

- DB: PostgreSQL(로컬 개발), 배포 시 Neon
- 외부 API: API-Football(`v3.football.api-sports.io`), Anthropic API(진단용)

---

## 🚩 2026-08-21 (시즌 개막) 전에 반드시 할 일

지금 화면에 뜨는 데이터는 **실제 경기가 아니다.** `src/test/resources/apifootball/`의
replay 파일은 파이프라인 갈래를 태우려고 만든 표본 10경기이고, 시즌 라벨만 2024다.
개막에 맞춰 실제 데이터를 받을 때 아래를 순서대로 한다.

1. **현재 시즌 캡처** — `mode: real` + `capture-dir`로 한 번 돌린다(대회당 요청 1회).
   ```bash
   ./gradlew bootRun --args='--spring.profiles.active=local \
     --gak.api-football.mode=real \
     --gak.api-football.capture-dir=src/test/resources/apifootball \
     --gak.sync.max-competitions-per-run=1'
   ```
2. **⚠️ `season-override: 2024` 를 지운다** (`application-local.yml`).
   **이걸 잊는 게 가장 위험하다.** 앱은 정상으로 보이는데 화면에는 2년 전 일정만 계속
   나온다 — 아무 에러도 안 뜨므로 눈치채기까지 오래 걸린다.
   확인: `select distinct season from fixture;` 또는 `/api/admin/sync/logs`의 season 값.
3. **예측·채점을 실제로 돌려 본다.** 미래 경기가 생기면 그때부터 예측을 걸 수 있고
   (지금은 모든 경기가 과거라 `POST /api/predictions`가 400), 경기가 끝나면 채점
   스케줄러가 매시 30분에 집어 간다.
4. **`/teams` 응답도 함께 캡처한다.** 팀 한글명 시드(`team-names-ko.json`)가 지금
   **영문 이름 문자열을 키로** 쓰는데, 이름은 언제든 바뀐다(`Bayern Munich` ↔
   `Bayern München`). 팀 id로 바꾸고 싶지만 대조할 원본이 없어 미뤄 뒀다 — 대회 시드가
   `CompetitionSeedTest`로 검증되는 것과 같은 장치를 팀에도 붙인다.
5. **결장(부상) 데이터의 리그 단위 조회가 되는지 확인한다.**
   지금 실제 응답으로 검증된 건 **팀 단위 1요청**뿐이다(`/injuries?team=33&season=2023`).
   이대로면 20개 대회로 확장이 안 된다 — 대회당 팀이 20개면 그것만 400요청이고,
   하루 예산은 100이다.
   현재 시즌을 캡처할 때 아래를 **함께 시도**해서 되는지 본다.
   ```bash
   curl -s -H "x-apisports-key: $API_FOOTBALL_KEY" \
     "https://v3.football.api-sports.io/injuries?league=39&season=2026" \
     | head -c 400
   ```
   - **되면** → 대회 단위 동기화(대회당 1요청)로 바꾸고 경기 동기화와 같은 스케줄에 태운다.
   - **안 되면**(`errors`에 파라미터 오류) → 팀 단위로 남기되 **노출 중인 팀만** 받는
     식으로 범위를 좁힌다. 전 팀 동기화는 예산상 불가능하다는 걸 그때 확정한다.
   - 어느 쪽이든 확인 전까지 스케줄러를 만들지 않는다. 되는지도 모르는 채 예산을
     태우는 게 제일 나쁘다. (그래서 지금은 수동 실행만 열려 있다)

---

## 계층 규칙 (backend) — 단방향

```
controller → service → repository → domain(entity)
```

- 화살표 **역방향 의존 금지**. domain은 아무것도 모르고, repository는 domain만,
  service는 repository+domain, controller는 service만 안다.
- **controller**: HTTP 관심사(요청/응답 변환, 상태코드)만. 규칙 판단 금지.
- **service**: 유스케이스와 규칙 조율. 트랜잭션 경계.
- **repository**: Spring Data JPA 인터페이스. 쿼리만.
- **domain**: 엔티티 + 불변식. 프레임워크/DB에 대한 지식 최소화.
- 부가 패키지: `config/`(설정 빈), `dto/`(요청·응답). 엔티티를 API로 직접
  노출하지 않고 DTO로 변환해서 내려준다.
- 패키지 루트: `page.usetaehwan.gak`

## 데이터 설계 원칙 — 사실만 저장, 파생값은 계산

- 저장하는 것: **API가 준 사실** + **우리가 만든 예측 기록**, 그게 전부다.
- 저장하지 **않고** 그때그때 계산: 경기 간격, 일정 밀집도, 폼, 승점률, 이동거리.
  - 이동거리 = 두 `Venue` 좌표 + 하버사인. 좌표(`latitude`/`longitude`)가
    `null`이면 계산·표기를 **생략**한다(에러가 아니다).
- 좌표는 API가 주지 않으므로 시드(`resources/seeds/city-coordinates.json`)로
  주요 도시만 채운다. 나머지는 `null`.
- 팀 한글명은 시드(`resources/seeds/team-names-ko.json`)로 매핑. 없으면 화면에서
  영문 원본(`Team.name`)으로 fallback.
- 팀 `code`가 없으면 팀명 첫 3자음으로 자동 생성(서비스 계층). 링 컬러는 `code`
  해시로 **프론트에서** 계산한다 — 저장하지 않는다.
- `CompetitionType`(LEAGUE/CUP/HYBRID)은 **시드가 직접 정한다**
  (`resources/seeds/competitions.json`). API의 `league.type`은 League/Cup 두 값뿐이라
  조별리그+녹아웃인 유럽대항전(HYBRID)을 표현하지 못한다.
- **결장은 `Injury`가 아니라 `Absence`다.** API 엔드포인트 이름은 `/injuries`지만 실제
  응답에는 징계(`Suspended`·`Red Card`)·질병·대표팀 차출·감독 결정이 섞여 온다
  (맨유 2023 시즌 346건 중 35건). 전부 "부상"으로 세면 화면이 "부상 5명"이라 말하는데
  둘은 징계인 상태가 된다. 사유 원문(`reasonRaw`)은 보관하고 갈래(`AbsenceReason`)로
  따로 나눈다. `Questionable`(불투명)은 결장자 수에 **넣지 않는다** — 합치면 부풀어 오른다.
  그리고 결장 데이터가 없는 경기의 `absentCount`는 **0이 아니라 null**이다.
- **대회는 id로만 다룬다.** 이름은 유일하지 않다 — "Serie A"는 이탈리아(135)와
  브라질(71)에 모두 있고, 여자부·유소년·하부 리그가 거의 같은 이름을 쓴다
  (DFB Pokal 81 / 여자 947 / 유소년 715). 시드 id는 테스트가 실제 `/leagues` 응답과
  대조해 검증한다(`CompetitionSeedTest`).

## 외부 API 동기화 — 우리 DB가 source of truth

- 사용자 요청마다 API를 부르지 않는다. 백엔드가 주기 동기화하고, 프론트는 우리 DB만 본다.
  무료 티어가 **하루 100요청**이라 이게 선택이 아니라 제약이다.
- 계층: `service/sync/` (동기화 유스케이스) → `external/apifootball/` (외부 API 접근).
  `external`은 인프라 계층으로, service만 의존한다.
- **`gak.api-football.mode`의 기본값은 `replay`** — 저장해 둔 응답 파일
  (`src/test/resources/apifootball/`)을 읽는다. 실 호출(`real`)은 명시적으로 켠 사람만 한다.
  자세한 건 그 폴더의 `README.md`.
- HTTP 200이어도 body에 `errors`가 올 수 있다. 상태 코드만 보고 성공으로 처리하지 말 것.
- 동기화 이력(`SyncLog`)이 스케줄러의 입력이다 — 오늘 쓴 요청 수와 대회별 마지막 성공
  시각을 여기서 읽는다. 메모리 카운터를 쓰지 않는다(재시작해도 유지돼야 하므로).

## ⚠️ 핵심 불변식 — 예측은 킥오프 이전에만

**`Prediction`은 반드시 `fixture.kickoff` 이전에만 생성된다.** 사후 예측이 들어가면
적중률이 의미를 잃는다. 이 시점 제약이 이 앱의 존재 이유다.

- 강제 위치: `service` 계층(`PredictionService`)이 주입된 `Clock`으로 "지금"을
  판정하고, 도메인 팩터리 `Prediction.create(...)`가 최종 검증한다.
- 클라이언트가 보낸 시각을 **신뢰하지 않는다**. 서버 시계로만 판정한다.
- 위반 시 `IllegalArgumentException` → HTTP 400.
- 이 규칙을 우회하는 코드(과거 시각 주입, 검증 생략 경로 등)를 절대 만들지 말 것.
- `Prediction`은 **어느 팀 관점인지(`team`)를 반드시 함께 저장한다.** 같은 "W"가 홈 승리도
  원정 승리도 되므로 주어가 없으면 채점이 불가능하다. 그 팀이 그 경기에 실제로 뛰는지도
  팩터리가 검증한다 — 아니면 영영 채점 안 되는 기록이 되어 적중률의 분모만 갉아먹는다.

### 채점 — 위 규칙의 반대쪽 짝

- `PredictionScoringService`가 끝난 경기의 결과로 적중 여부를 매긴다(매시 30분,
  동기화 20분 뒤). 채점이 없으면 예측은 영원히 `isHit = null`로 남는다.
- **한 번만 채점한다**(`isHit is null`로만 뽑는다) → 멱등. 나중에 스코어가 정정돼도
  다시 매기지 않는다 — 한 번 매긴 성적이 조용히 뒤집히는 쪽이 더 나쁘다.
- **결과를 모르면 보류한다.** 상태가 FT여도 득점이 안 들어온 순간이 있는데, 그때 채점하면
  `isHit=false`로 굳어 "틀렸다"가 된다. 모르는 것과 틀린 것은 다르다.
- 포함/제외 규칙은 `SchedulePolicy.countsForForm` 하나만 본다(폼 집계와 같은 함수).

---

## ⚠️ 계산은 백엔드에만 둔다 — 사실의 출처는 하나

**밀집도·폼·승패 판정·대회 성격 판별을 프론트에서 다시 계산하지 않는다.**
백엔드가 계산해 내려주고, 프론트는 그린다.

- 같은 규칙이 Java와 TS 양쪽에 있으면 한쪽만 고치는 날이 온다. 그날부터 화면의 승패와
  (같은 데이터를 읽는) AI 진단의 승패가 갈린다 — 사용자에게는 앱이 자기 말을 뒤집는
  것으로 보인다.
- 프론트 `lib/timeline/`이 하는 일은 **표기뿐**이다: 날짜 문자열, 간격→여백 픽셀,
  "21일 7경기" 같은 요약 문구. 판정하지 않는다.
- 화면이 읽는 엔드포인트는 하나다 — `GET /api/teams/{teamId}/diagnostics`.
  경기 목록과 밀집도를 따로 부르면 두 응답이 다른 스냅샷을 볼 수 있다.

### 응답 계약에서 이미 정해진 것들

| 주제 | 결정 |
| --- | --- |
| 구간 경계 | **fixtureId**로 준다. 인덱스는 "누구의 어떤 목록에서 몇 번째"에 의존해 API 경계를 넘으면 안 된다 |
| 미확정 경기 | `result`/`goals`가 **null**. 안 치른 경기를 무승부(D)로 접지 않는다 |
| 승부차기 | 결과 집계는 **무승부(D)**(120분을 뛴 부하가 폼에서 지워지면 안 되므로). 진출 여부는 `shootoutFor`/`shootoutAgainst`로 **따로** 준다 — 집계와 표시를 분리한다 |
| 중립 경기(N) | **지원하지 않는다.** API가 중립 플래그를 주지 않는다. 경기장 이름으로 추측하지 말 것(웸블리는 토트넘의 홈이었던 적이 있고 결승 장소는 매년 바뀐다). H/A만 다룬다 |
| 모르는 값 | null + `omissions`에 이유. 0이나 "-"로 조용히 채우지 않는다 |

### 목 데이터

`frontend/lib/api/mock/diagnostics-manutd-2324.json`은 **백엔드가 실제로 계산해 내려준
응답을 저장한 스냅샷**이다. 손으로 쓴 값이 아니다 — 목을 위해 TS로 다시 계산하면 위
규칙을 정확히 어기게 된다. 다시 만드는 법: `scripts/generate-mock-snapshot.md`.

`GAK_DATA_SOURCE=mock`으로 전환한다. **백엔드가 죽었을 때 자동으로 넘어가지 않는다** —
화면은 멀쩡한데 숫자가 가짜인 상태가 가장 눈치채기 어려운 실패다.

---

## 디자인 시스템 방침 (frontend)

- **@usetaehwan/ui 재사용이 원칙.** 새 컴포넌트를 직접 만들기 전에 패키지에 있는지
  먼저 확인한다.
- 색·간격 등은 **토큰만 참조**한다(`@usetaehwan/ui/tokens.css`의 CSS 변수:
  `var(--color-...)`, 간격 토큰 등). 색상값·픽셀값 **하드코딩 금지**.
- Tailwind v4는 `node_modules`를 스캔하지 않으므로 `app/globals.css`의
  `@source "../node_modules/@usetaehwan/ui/dist";` 디렉티브가 **필수**다. 없으면
  UI 컴포넌트가 무스타일로 렌더된다.
- Next.js는 이 워크스페이스 기준 버전을 쓴다. 코드 작성 전 필요한 API는
  `node_modules/next/dist/docs/` 문서를 먼저 확인한다(학습 데이터와 다를 수 있음).

### 로고·파비콘

- 파비콘은 `app/icon.svg`(App Router 규약 — 자동으로 `<link rel="icon">`이 된다).
- 로고는 `components/brand/Logo.tsx` — 가로형 `LogoLockup` / 심볼 단독 `LogoSymbol`.
- **SVG 안에 글자(`<text>`)를 넣지 않는다.** 웹폰트가 안 뜨면 SVG는 대체 글꼴을
  `viewBox`에 욱여넣어 글자가 눌리거나 잘린다. 심볼은 도형만으로 그리고, "각 GAK"는
  평범한 HTML 텍스트로 둔다 — 폰트가 없으면 모양만 달라지고 배치는 멀쩡하다.
- **volt(`#C8FF1E`)는 다크 배경 전용이다.** 흰 배경 대비가 1.18:1 이라 읽히지 않는다(WCAG AA 본문 기준 4.5:1). 잉크는 20:1.
  밝은 배경에서는 `tone="light"`로 잉크(`--color-ink`) 단색을 쓴다.
- 파비콘만 예외적으로 잉크 타일(배경)을 직접 들고 있다. 탭 배경색은 우리가 정할 수
  없기 때문이다.

---

## 시크릿 취급 — 절대 금지

- `application.yml`에는 **환경변수 참조만**(`${DATABASE_URL}` 형태). 값 하드코딩 금지.
- 로컬 값은 `backend/src/main/resources/application-local.yml`(gitignore)에,
  프론트는 `frontend/.env.local`(gitignore)에 넣는다. 템플릿은 각각 `.example`.
- 필요한 키(이름만): `DATABASE_URL`, `API_FOOTBALL_KEY`, `ANTHROPIC_API_KEY`.
- API-Football/Anthropic 키 같은 시크릿은 **백엔드에서만** 다룬다. 브라우저에
  노출되는 `NEXT_PUBLIC_*`에 비밀 키를 넣지 않는다.
- 실제 키/비밀번호를 코드·문서·커밋에 남기지 않는다.

## 워크플로 — 승인제

- **main 직접 커밋 금지.** 항상 브랜치에서 작업한다.
- 커밋·푸시·배포는 **사용자 승인** 후에만. 임의로 커밋/푸시하지 않는다.
- GitHub 레포 생성·푸시는 사용자가 직접 한다.

## 실행 (로컬)

```bash
# backend (Java 21 필요)
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'

# frontend
cd frontend && npm install && npm run dev
```
