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
- 리그/컵 판별은 API가 직접 주지 않는다. `league.standings`(순위표 존재)와 round
  문자열로 `CompetitionType`(LEAGUE/CUP/HYBRID)을 채운다.

## ⚠️ 핵심 불변식 — 예측은 킥오프 이전에만

**`Prediction`은 반드시 `fixture.kickoff` 이전에만 생성된다.** 사후 예측이 들어가면
적중률이 의미를 잃는다. 이 시점 제약이 이 앱의 존재 이유다.

- 강제 위치: `service` 계층(`PredictionService`)이 주입된 `Clock`으로 "지금"을
  판정하고, 도메인 팩터리 `Prediction.create(...)`가 최종 검증한다.
- 클라이언트가 보낸 시각을 **신뢰하지 않는다**. 서버 시계로만 판정한다.
- 위반 시 `IllegalArgumentException` → HTTP 400.
- 이 규칙을 우회하는 코드(과거 시각 주입, 검증 생략 경로 등)를 절대 만들지 말 것.

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
