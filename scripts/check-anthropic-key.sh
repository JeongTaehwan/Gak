#!/usr/bin/env bash
#
# Anthropic 키 상태를 한 번에 확인한다 — 인증 / 크레딧 / 소속 조직.
#
# ## 왜 스크립트인가
#
# 키를 YAML 에서 셸로 꺼내는 게 생각보다 잘 틀린다. 값에 붙은 따옴표를 같이 잡으면
# 키가 2자 길어져 `invalid x-api-key` 가 나는데, 이건 "키가 잘못됐다"로 읽혀서
# 엉뚱한 곳을 파게 만든다. 한 번 제대로 짜서 가둬 둔다.
#
# ## 왜 두 번 부르나
#
# 인증 실패와 크레딧 부족은 원인도 대응도 다르다. 한 요청으로는 구분이 안 되므로
# 크레딧이 필요 없는 엔드포인트(/v1/models)로 인증을 먼저 확인하고,
# 그다음 실제 과금 경로(/v1/messages)를 최소 요청으로 두드린다.
#
#   ./scripts/check-anthropic-key.sh
#
set -euo pipefail

REPO="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOCAL_YML="$REPO/backend/src/main/resources/application-local.yml"

[[ -f "$LOCAL_YML" ]] || { echo "설정 파일 없음: $LOCAL_YML"; exit 1; }

# 따옴표·주석·공백을 정확히 걷어낸다. (grep -oPz 로 하려다 따옴표를 같이 잡는 사고를 냈다)
KEY="$(python3 - "$LOCAL_YML" <<'PY'
import re, sys, pathlib
s = pathlib.Path(sys.argv[1]).read_text()
m = re.search(r'anthropic:\s*\n(?:\s+\S+:.*\n)*?\s*key:\s*["\']?([^"\'\n#]+)', s)
print(m.group(1).strip() if m else "")
PY
)"

if [[ -z "$KEY" ]]; then
	echo "gak.anthropic.key 가 비어 있습니다 → AI 진단은 규칙 기반으로 동작합니다 (정상 상태)"
	exit 0
fi
printf '키       : %s… (%d자)\n' "${KEY:0:14}" "${#KEY}"

echo
echo "1) 인증 확인 — GET /v1/models (크레딧 불필요)"
HEADERS="$(curl -sD - -o /dev/null https://api.anthropic.com/v1/models?limit=1 \
	-H "x-api-key: $KEY" -H "anthropic-version: 2023-06-01" || true)"
STATUS="$(sed -n '1s/.* \([0-9]\{3\}\) .*/\1/p' <<<"$HEADERS")"
ORG="$(grep -i '^anthropic-organization-id:' <<<"$HEADERS" | tr -d '\r' | awk '{print $2}')"

if [[ "$STATUS" != "200" ]]; then
	echo "   ✗ 인증 실패 (HTTP $STATUS) — 키 자체가 잘못됐거나 폐기됐습니다"
	exit 1
fi
echo "   ✓ 인증 정상"
echo "   소속 조직: ${ORG:-(헤더에 없음)}"
echo "   ↑ 크레딧은 **이 조직**에 있어야 합니다. Console 에서 조직을 전환해 가며 확인하세요."

echo
echo "2) 과금 경로 확인 — POST /v1/messages (16토큰 최소 요청)"
BODY="$(curl -s https://api.anthropic.com/v1/messages \
	-H "x-api-key: $KEY" -H "anthropic-version: 2023-06-01" -H "content-type: application/json" \
	-d '{"model":"claude-opus-5","max_tokens":16,"messages":[{"role":"user","content":"OK"}]}' || true)"

python3 - "$BODY" <<'PY'
import json, sys
try:
	d = json.loads(sys.argv[1])
except Exception:
	print("   ✗ 응답을 읽지 못했습니다:", sys.argv[1][:200]); raise SystemExit(1)

if d.get("type") == "error":
	err = d["error"]
	print(f"   ✗ {err['type']}")
	print(f"     {err['message']}")
	if "credit balance" in err.get("message", ""):
		print()
		print("     → claude.ai 구독(Pro/Max)은 API 사용료를 대지 않습니다. 별개의 지갑입니다.")
		print("       console.anthropic.com → Plans & Billing → Buy credits 에서 API 크레딧을 삽니다.")
		print("       조직이 여러 개면 위에 찍힌 조직 ID 와 같은 곳인지 확인하세요.")
		print("       워크스페이스 지출 한도(Limits)가 0 이어도 같은 에러가 납니다.")
	raise SystemExit(1)

text = "".join(b.get("text", "") for b in d.get("content", []) if b.get("type") == "text")
u = d.get("usage", {})
print(f"   ✓ 응답 정상: {text.strip()!r}")
print(f"     모델 {d.get('model')} · 입력 {u.get('input_tokens')} / 출력 {u.get('output_tokens')} 토큰")
print()
print("   AI 진단을 실제로 돌려볼 준비가 됐습니다:")
print("     curl -s http://localhost:8080/api/teams/33/diagnosis | python3 -m json.tool --no-ensure-ascii")
PY
