#!/usr/bin/env bash
#
# Gak(각) 개발 서버 동시 실행
#   ./scripts/dev.sh
#
# 백엔드(:8080, Spring Boot) + 프론트(:3000, Next.js)를 함께 띄우고,
# 로그를 [backend] / [frontend] 접두사로 구분해 출력한다.
# Ctrl+C 한 번으로 두 서버를 모두 종료한다.

set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

CYAN=$'\033[36m'
MAGENTA=$'\033[35m'
YELLOW=$'\033[33m'
RESET=$'\033[0m'
BE="${CYAN}[backend] ${RESET}"
FE="${MAGENTA}[frontend]${RESET}"

# --- 사전 점검: 없으면 이유를 알려주고 멈춘다(로그 뒤섞임 방지) ---
if ! command -v java >/dev/null 2>&1; then
  echo "${YELLOW}⚠ java 를 찾을 수 없습니다. 백엔드 실행에는 JDK 21 이 필요합니다.${RESET}"
  echo "  설치 후 다시 실행하세요."
  exit 1
fi
if [ ! -f "$ROOT/backend/src/main/resources/application-local.yml" ]; then
  echo "${YELLOW}⚠ backend/src/main/resources/application-local.yml 이 없습니다.${RESET}"
  echo "  application-local.yml.example 을 복사해 로컬 DB 접속 정보를 채우세요:"
  echo "    cp backend/src/main/resources/application-local.yml.example \\"
  echo "       backend/src/main/resources/application-local.yml"
  exit 1
fi
if [ ! -d "$ROOT/frontend/node_modules" ]; then
  echo "${YELLOW}⚠ frontend/node_modules 이 없습니다. 먼저 의존성을 설치하세요:${RESET}"
  echo "    (cd frontend && npm install)"
  exit 1
fi

# 종료 처리: Ctrl+C(INT) / 종료(TERM) / 스크립트 exit 시
# 현재 프로세스 그룹 전체를 정리한다(두 서버 + 로그 파이프 포함).
cleanup() {
  trap - EXIT INT TERM   # 핸들러 재진입 방지
  echo
  echo "종료합니다…"
  kill 0                 # 이 스크립트가 속한 프로세스 그룹 전체에 신호
}
trap cleanup EXIT INT TERM

echo "▶ 백엔드  http://localhost:8080"
echo "▶ 프론트  http://localhost:3000"
echo "  (Ctrl+C 로 둘 다 종료. 백엔드 첫 실행은 Gradle 의존성 내려받느라 시간이 걸립니다.)"
echo

# 백엔드: Gradle 래퍼로 bootRun. local 프로파일로 application-local.yml 을 읽는다.
# --console=plain 으로 Gradle 진행바를 끄고 로그를 접두사와 함께 깔끔히 출력.
(
  cd "$ROOT/backend"
  exec ./gradlew bootRun --console=plain --args='--spring.profiles.active=local'
) 2>&1 | sed -u "s|^|${BE}|" &

# 프론트: next dev
(
  cd "$ROOT/frontend"
  exec npm run dev
) 2>&1 | sed -u "s|^|${FE}|" &

wait
