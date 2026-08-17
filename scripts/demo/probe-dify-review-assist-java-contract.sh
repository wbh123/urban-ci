#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command python3

DIFY_ENABLED="$(env_value URBAN_SAFE_DIFY_ENABLED || true)"
DIFY_BASE_URL="$(env_value URBAN_SAFE_DIFY_BASE_URL || true)"
DIFY_API_KEY="$(env_value URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY || true)"

[[ "${DIFY_ENABLED,,}" == "true" ]] || fail "URBAN_SAFE_DIFY_ENABLED 不是 true"
[[ -n "${DIFY_API_KEY}" ]] || fail "缺少 URBAN_SAFE_DIFY_REVIEW_ASSIST_API_KEY"
[[ -n "${DIFY_BASE_URL}" ]] || DIFY_BASE_URL="https://api.dify.ai/v1"
DIFY_BASE_URL="${DIFY_BASE_URL%/}"

body="$(python3 - <<'PY'
import json
print(json.dumps({
    "inputs": {
        "analysisJson": json.dumps({"summary": "java contract probe", "detections": []}, ensure_ascii=False),
        "inspectionRecordJson": json.dumps({"records": []}, ensure_ascii=False),
        "localModelJson": "{}",
        "buildingContextJson": json.dumps({"buildingId": "showcase-java-contract"}, ensure_ascii=False),
        "workflowCode": "DIFY-REVIEW-ASSIST-001",
        "workflowVersion": "review-assist-v1.0.0",
        "inputSchemaVersion": "1.0"
    },
    "response_mode": "blocking",
    "user": "urban-safe-java-contract-probe"
}, ensure_ascii=False))
PY
)"

response_file="${RUN_DIR}/preflight/dify-review-assist-java-contract-probe.json"
mkdir -p "$(dirname "${response_file}")"

http_code="$(curl --noproxy '*' -sS \
  -o "${response_file}" \
  -w '%{http_code}' \
  -X POST "${DIFY_BASE_URL}/workflows/run" \
  -H "Authorization: Bearer ${DIFY_API_KEY}" \
  -H 'Content-Type: application/json' \
  -d "${body}")"

echo "Dify Review Assist Java 契约镜像探针：HTTP ${http_code}"

HTTP_CODE="${http_code}" RESPONSE_FILE="${response_file}" python3 - <<'PY'
import json, os
path = os.environ["RESPONSE_FILE"]
status_code = int(os.environ["HTTP_CODE"] or 0)
try:
    payload = json.load(open(path, encoding="utf-8"))
except Exception as exc:
    print(f"[FAIL] Dify 响应不是合法 JSON：{exc}")
    print(f"响应文件：{path}")
    raise SystemExit(1)

if status_code < 200 or status_code >= 300:
    code = payload.get("code") or payload.get("error") or "UNKNOWN"
    message = payload.get("message") or payload.get("detail") or "Dify HTTP 调用失败"
    print(f"[FAIL] Dify HTTP 错误：code={code} message={message}")
    print(f"响应文件：{path}")
    raise SystemExit(1)

data = payload.get("data") or {}
run_id = payload.get("workflow_run_id") or data.get("id") or "UNKNOWN"
status = data.get("status") or payload.get("status") or "UNKNOWN"
error = data.get("error") or payload.get("error") or ""
print(f"workflow_run_id={run_id}")
print(f"status={status}")
if error:
    print(f"error={error}")
if str(status).lower() != "succeeded":
    print("[FAIL] Java 契约镜像请求失败；若最小 4 字段探针成功，则重点检查 Cloud DSL 是否声明 workflowCode/workflowVersion/inputSchemaVersion。")
    print(f"响应文件：{path}")
    raise SystemExit(1)
print("[PASS] Java 契约镜像请求 SUCCEEDED")
print(f"响应文件：{path}")
PY
