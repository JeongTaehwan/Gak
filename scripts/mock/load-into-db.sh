#!/usr/bin/env bash
#
# 목 52경기를 격리 스키마에 적재하고 백엔드를 그 위에 띄운다.
#
# ## 왜 필요한가
#
# replay 표본 데이터(`backend/src/test/resources/apifootball/`)는 파이프라인 갈래를
# 태우려고 만든 6~10경기라 **확정 경기가 2건**뿐이다. AI 진단은 표본이 5건 미만이면
# 모델을 아예 부르지 않으므로(→ `AiDiagnosisService.insufficientSample`), 이 데이터로는
# AI 호출 경로를 한 번도 못 지난다.
#
# 맨유 2023-24 실제 52경기를 태우면 확정 6건 · 밀집 구간 3개 · 결장 44/52경기가 되어
# 게이트를 통과한다. 8/21 개막에 맞춰 현재 시즌을 받으면 이 스크립트는 필요 없어진다.
#
# ## 왜 격리 스키마인가
#
# `public` 은 개발용 실데이터다. 목 52경기를 거기 부으면 시즌·경기가 섞이고, 되돌리려면
# 무엇이 원래 있던 건지 가려내야 한다. 별도 스키마에 넣으면 정리가 `DROP SCHEMA` 한 줄이고
# `public` 은 처음부터 손대지 않는다.
#
# ## 쓰는 법
#
#   ./scripts/mock/load-into-db.sh          # 적재 + 백엔드 기동(:8080)
#   ./scripts/mock/load-into-db.sh --clean  # 스키마 삭제
#
# 백엔드가 :8080 을 그대로 쓰므로 프론트 설정은 건드리지 않아도 된다.
# (단 `frontend/.env.local` 의 `GAK_DATA_SOURCE=mock` 은 지워야 백엔드를 본다)
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SCHEMA="${GAK_MOCK_SCHEMA:-mockgen}"
WORKDIR="${GAK_MOCK_WORKDIR:-/tmp/gak-mock-db}"
LOCAL_YML="$REPO/backend/src/main/resources/application-local.yml"

# DB 접속 정보는 gitignore 된 로컬 설정에서 읽는다 — 이 파일에 비밀번호를 적지 않는다.
read_yml() { grep -oPm1 "^\s*$1:\s*[\"']?\K[^\"'#]+" "$LOCAL_YML" | tr -d '[:space:]'; }
DB_URL="$(read_yml url)"
DB_USER="$(read_yml username)"
export PGPASSWORD="$(read_yml password)"
DB_HOST="$(sed -E 's|.*//([^:/]+).*|\1|' <<<"$DB_URL")"
DB_PORT="$(sed -E 's|.*:([0-9]+)/.*|\1|' <<<"$DB_URL")"
DB_NAME="$(sed -E 's|.*/([^?]+).*|\1|' <<<"$DB_URL")"
psql_() { psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc "$1"; }

if [[ "${1:-}" == "--clean" ]]; then
	psql_ "DROP SCHEMA IF EXISTS $SCHEMA CASCADE;" >/dev/null
	rm -rf "$WORKDIR"
	echo "정리 완료 — $SCHEMA 스키마와 $WORKDIR 삭제. public 은 그대로."
	exit 0
fi

echo "1) 목 52경기 → API-Football 응답 파일"
rm -rf "$WORKDIR" && mkdir -p "$WORKDIR"
node "$REPO/scripts/mock/generate-replay.mjs" "$WORKDIR/replay" 2>/dev/null

echo "2) 격리 스키마 준비 ($SCHEMA)"
psql_ "CREATE SCHEMA IF NOT EXISTS $SCHEMA;" >/dev/null
echo "   public 테이블 $(psql_ "select count(*) from information_schema.tables where table_schema='public';")개는 손대지 않음"

echo "3) 백엔드 기동 (:8080, 스키마=$SCHEMA)"
if ss -lptn 'sport = :8080' | grep -q LISTEN; then
	kill "$(ss -lptn 'sport = :8080' | grep -oP 'pid=\K[0-9]+' | head -1)" && sleep 4
fi
setsid nohup "$REPO/backend/gradlew" -p "$REPO/backend" bootRun --console=plain --args="\
  --spring.profiles.active=local \
  --spring.datasource.url=jdbc:postgresql://$DB_HOST:$DB_PORT/$DB_NAME?currentSchema=$SCHEMA \
  --gak.sync.enabled=false \
  --gak.sync.season-override=2023 \
  --gak.api-football.replay-locations[0]=file:$WORKDIR/replay/" \
  > "$WORKDIR/backend.log" 2>&1 < /dev/null &
disown
for _ in $(seq 1 40); do grep -q "Started GakApplication" "$WORKDIR/backend.log" 2>/dev/null && break; sleep 2; done
grep -E "AI 진단 (활성|비활성)" "$WORKDIR/backend.log" | tail -1 | sed 's/^.*: /   /'

echo "4) 동기화 (replay — 외부 요청 0회)"
for c in 39 45 48 2; do curl -s -X POST "http://localhost:8080/api/admin/sync/$c" >/dev/null; done
curl -s -X POST "http://localhost:8080/api/admin/sync/injuries/33?season=2023" >/dev/null

echo "5) 게이트 확인"
curl -s http://localhost:8080/api/teams/33/diagnostics | python3 -c "
import json,sys
d=json.load(sys.stdin); f=d['form']; c=d['congestion']
ok = f['confidence'] in ('MODERATE','SUFFICIENT') and c['detectable']
print(f\"   경기 {len(d['matches'])}건 · 확정 {f['sampleSize']}건 · 밀집 구간 {len(c['spans'])}개\")
print('   →', '게이트 통과 — AI 호출됨' if ok else '게이트 차단')
"
echo
echo "AI 진단 호출:  curl -s http://localhost:8080/api/teams/33/diagnosis | python3 -m json.tool --no-ensure-ascii"
echo "로그:          $WORKDIR/backend.log"
echo "정리:          ./scripts/mock/load-into-db.sh --clean"
