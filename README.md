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

## 현재 범위

스캐폴딩 단계다. **엔티티/계층 골격 + 예측 시점 규칙**까지 구현되어 있다.
다음 단계에서 API 동기화 파이프라인과 화면(디자인 확정본)을 붙인다.
