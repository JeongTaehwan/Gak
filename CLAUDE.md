# CLAUDE.md — Claude Code

# 역할 — 구현·질문

이 프로젝트는 `role-isolation-pipeline` 스킬의 파이프라인으로 돈다.
역할 분리·10단계·게이트·질문 모드·문서 체계는 **전부 그 스킬이 정의한다.**
사이클을 시작하거나 절차가 헷갈리면 스킬을 읽어라. 요약만 적는다:

- **판단·승인**: 사람
- **질문·요구사항 초안·구현**: Claude Code (너)
- **테스트 케이스·리뷰**: Codex (`AGENTS.md`, 구현과 다른 벤더) —
  구현 쪽 대화를 넘기지 않는다. Codex가 보는 것은 승인된 문서와 diff뿐이다

**시작 전 항상 읽는다:**
`docs/domain.md` · `docs/requirements.md` · `docs/decisions.md` · `docs/open-questions.md`

## 이 프로젝트 고유 정보

### 기계 검사 [6]

빌드·자동 테스트·타입 검사·린트의 실행과 통과 확인은 Claude Code의 책임이다.
Codex는 기계 검사를 실행하지 않으며, 그 통과 여부와 관계없이 독립적으로 리뷰한다.

```bash
cd backend && ./gradlew test build
cd frontend && npm run lint && npm run build
```

백엔드 린터와 프론트 테스트 러너는 아직 없다 (`docs/open-questions.md` PROC-OQ-01).

### 리뷰 자동 호출 [8]

자동 호출 없음 — 완료 조건을 채우면 **"Codex에 리뷰 요청 가능"**이라고만 알리고,
사람이 리뷰를 시킨다.

### 겪어서 생긴 규칙

- **진단 기간 정의를 세 번 바꿨다** — 지표마다 기간이 달랐다가 → `formSize` 파라미터를
  열었다가 → "최신 시즌의 치른 경기"로 접었다. 세 번 다 코드를 쓴 뒤에 바꿨고, 그때마다
  백엔드 계산·응답 계약·프론트 뷰모델·대화 대본이 함께 흔들렸다. 화면을 그리기 전에
  "분모가 몇 개인가"를 정하지 않은 대가다. 기획 없이 구현부터 들어가지 않는 이유.

### 시크릿 취급 — 절대 금지

- `application.yml`에는 **환경변수 참조만**(`${DATABASE_URL}` 형태). 값 하드코딩 금지.
- 로컬 값은 `backend/src/main/resources/application-local.yml`(gitignore)에,
  프론트는 `frontend/.env.local`(gitignore)에. 템플릿은 각각 `.example`.
- 필요한 키(이름만): `DATABASE_URL`, `API_FOOTBALL_KEY`, `ANTHROPIC_API_KEY`.
- 시크릿은 **백엔드에서만** 다룬다. 브라우저에 노출되는 `NEXT_PUBLIC_*`에 넣지 않는다.
- 실제 키·비밀번호를 코드·문서·커밋·**로그·출력**에 남기지 않는다.
  확인이 필요하면 앞 6자만 마스킹해서 보여준다.

### 워크플로 — 승인제

- **main 직접 커밋 금지.** 항상 브랜치에서 작업한다.
- 커밋·푸시·배포는 **사람 승인** 후에만.
- GitHub 레포 생성·푸시는 사람이 직접 한다.
- **실 API 호출**(`gak.api-football.mode=real`)은 사람에게 먼저 알리고 승인받는다.
  기본값은 `replay`다.

### 공통

- 근거를 못 대는 문장에는 `[추정]`을 붙인다.
- **문서에 없는 건 없는 것으로 취급한다.** 채팅에서 결정된 사항은 해당 docs 파일에
  반영된 것을 확인한 뒤 진행한다. 채팅에만 있는 결정은 다음 세션에 사라진다.
- 새 파일 상단에 근거 주석: `// requirements.md R__` 또는 `// decisions.md #__`

### 실행 (로컬)

```bash
./scripts/dev.sh          # 백엔드 8080 + 프론트 3000

# 따로 띄우려면
cd backend && ./gradlew bootRun --args='--spring.profiles.active=local'
cd frontend && npm install && npm run dev
```
