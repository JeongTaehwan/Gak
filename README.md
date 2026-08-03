# 각 (Gak)

> 리그·컵·유럽대항전을 가로질러 팀의 실제 일정을 통합하고, 일정 밀집도로 부진
> 원인을 진단하며, 다음 경기를 예측하고 적중률을 추적하는 **축구 팀 진단·예측 웹앱**.

한국어 전용 서비스.

## 컨셉

- **통합 일정** — 한 팀이 뛰는 리그·컵·챔피언스리그 경기를 하나의 타임라인으로.
- **진단** — 일정 밀집도(짧은 간격, 이동거리 누적)를 근거로 부진 원인을 설명.
- **예측 & 적중률** — 다음 경기를 예측하고, **킥오프 이전에 남긴** 예측만 채점해
  적중률을 정직하게 추적.

## 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Spring Boot 3.5 · Java 21 · Gradle · Spring Web / Data JPA / Validation |
| Frontend | Next.js 15 · TypeScript · Tailwind v4 · [@usetaehwan/ui](https://www.npmjs.com/package/@usetaehwan/ui) |
| DB | PostgreSQL (로컬), Neon (배포) |
| 외부 API | API-Football (`v3.football.api-sports.io`), Anthropic API (진단) |

## 구조

```
gak/
├── backend/    Spring Boot REST API  (패키지: page.usetaehwan.gak)
│   └── src/main/java/page/usetaehwan/gak/
│       ├── controller/   HTTP 진입점
│       ├── service/      유스케이스 · 규칙(예측 시점 강제)
│       ├── repository/   Spring Data JPA
│       ├── domain/       엔티티 + 불변식
│       ├── dto/          요청·응답
│       └── config/       설정 빈(Clock, CORS, 예외 처리)
│   └── src/main/resources/
│       ├── application.yml            (환경변수 참조만)
│       └── seeds/                     (도시 좌표, 한글 팀명 매핑)
└── frontend/   Next.js App Router
    └── app/    (layout, page, globals.css — @source 디렉티브 필수)
```

계층은 `controller → service → repository → domain` **단방향**. 자세한 규칙과
설계 원칙은 [CLAUDE.md](CLAUDE.md) 참고.

## 로컬 실행

### 사전 준비
- Java 21 (backend), Node.js 20+ (frontend), PostgreSQL

### 1) 시크릿 설정 (실제 값은 커밋되지 않음)

```bash
# backend — 로컬 설정 템플릿 복사 후 값 채우기
cp backend/src/main/resources/application-local.yml.example \
   backend/src/main/resources/application-local.yml

# frontend
cp frontend/.env.example frontend/.env.local
```

필요한 키: `DATABASE_URL`, `API_FOOTBALL_KEY`, `ANTHROPIC_API_KEY`.

### 2) 백엔드

```bash
cd backend
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 3) 프론트엔드

```bash
cd frontend
npm install
npm run dev
```

### 4) 백엔드 없이 화면만 보기

```bash
echo "GAK_DATA_SOURCE=mock" >> frontend/.env.local
```

개발용 스냅샷(맨유 2023-24, 52경기)으로 그린다. 이 스냅샷은 **백엔드가 실제로 계산해
내려준 응답을 저장한 것**이라, 목이어도 화면이 보는 값의 규칙은 실제와 같다
(만드는 법: [scripts/generate-mock-snapshot.md](scripts/generate-mock-snapshot.md)).

백엔드가 죽었을 때 자동으로 목으로 넘어가지는 않는다 — 화면은 멀쩡한데 숫자가 가짜인
상태가 가장 눈치채기 어려운 실패라, 전환은 사람이 명시적으로 한다.

## 현재 범위

**통합 타임라인 화면이 실제 백엔드 데이터로 돈다.**

- 엔티티/계층 골격 + 예측 시점 규칙
- API-Football 동기화 파이프라인(replay 기본 — 요청 0회)
- 진단 계산: 일정 밀집도 · 최근 폼 · 이동거리
- 조회 API: `GET /api/teams/{teamId}/diagnostics`
- 통합 타임라인 화면(대회 가로지르기, 간격 시각화, 밀집 구간 브래킷)

계산은 전부 백엔드에 있고 화면은 그리기만 한다. 이유와 응답 계약은
[CLAUDE.md](CLAUDE.md) 참고.

다음: 진단·예측 화면, AI 진단 연동, 순위표(`/standings`) 동기화(상대 강도 지표).
