#!/usr/bin/env bash

# Profile B 验证：自动读取 runtime-catalog.json 当前启用的 AI-VISION-LOCAL-001 版本，
# 因此 Tiny 1.0.0 与批准后的 Base/Base+ 1.1.0 都使用同一套比赛机验收流程。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/setup-cn-mirrors.sh"

AI_SERVICE="${PROJECT_ROOT}/ai-service-python"
PYTHON_BIN="$(resolve_ai_python)"
MODEL_ROOT="${PROJECT_ROOT}/data/model-cache"
CATALOG="${MODEL_ROOT}/runtime-catalog.json"

if [[ ! -x "${PYTHON_BIN}" ]]; then
  echo "未找到 Python 环境（请先运行 setup-demo-rtx3060.sh）" >&2
  exit 1
fi
if [[ ! -f "${CATALOG}" ]]; then
  echo "缺少 active runtime catalog：${CATALOG}" >&2
  exit 1
fi

ACTIVE_VERSION="$("${PYTHON_BIN}" - "${CATALOG}" <<'PY'
import json, pathlib, sys
p = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
items = [x for x in p.get("models", []) if x.get("modelId") == "AI-VISION-LOCAL-001" and x.get("enabled", True)]
if len(items) != 1:
    raise SystemExit(f"active catalog 必须恰好启用一个 AI-VISION-LOCAL-001，实际 {len(items)}")
print(items[0]["version"])
PY
)"
MANIFEST="${MODEL_ROOT}/AI-VISION-LOCAL-001/${ACTIVE_VERSION}/manifest.json"
[[ -f "${MANIFEST}" ]] || { echo "active manifest 不存在：${MANIFEST}" >&2; exit 1; }
echo "当前 active 视觉模型：AI-VISION-LOCAL-001 v${ACTIVE_VERSION}"

export URBAN_SAFE_AI_MODEL_ROOT="${MODEL_ROOT}"
export URBAN_SAFE_AI_MODEL_CATALOG_PATH=runtime-catalog.json
export URBAN_SAFE_AI_VISUAL_DEVICE=cuda
export URBAN_SAFE_AI_VISION_DTYPE=float16
export URBAN_SAFE_AI_VISION_OFFLINE=true
export URBAN_SAFE_AI_VISION_HF_HOME="${HF_HOME}"
export HF_HUB_OFFLINE=1
export HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"

REPORT="${PROJECT_ROOT}/data/model-benchmarks/rtx3060-${ACTIVE_VERSION}-active-report.md"

echo "== [1/3] CUDA 环境 =="
"${PYTHON_BIN}" - <<'PY'
import torch
print("torch", torch.__version__)
print("torch.cuda.is_available() =", torch.cuda.is_available())
if not torch.cuda.is_available():
    raise SystemExit("CUDA 不可用")
print("GPU 名称:", torch.cuda.get_device_name(0))
props = torch.cuda.get_device_properties(0)
print(f"总显存: {props.total_memory / 1024 / 1024:.0f} MiB")
PY

echo "== [2/3] active v${ACTIVE_VERSION} 基准（20 次 + P50/P95 + 显存）=="
cd "${AI_SERVICE}"
"${PYTHON_BIN}" -m tools.benchmark_vision \
  --version "${ACTIVE_VERSION}" \
  --iterations 20 \
  --model-root "${MODEL_ROOT}" \
  --report "${REPORT}"

echo "== [3/3] FastAPI REAL 冒烟与 5 并发 =="
MANIFEST_STATUS="$("${PYTHON_BIN}" -c 'import json; print(json.load(open("'"${MANIFEST}"'", encoding="utf-8")).get("status", ""))')"
[[ "${MANIFEST_STATUS}" == "APPROVED" ]] || { echo "active catalog 指向的模型不是 APPROVED：${MANIFEST_STATUS}" >&2; exit 1; }

PORT=18082
"${PYTHON_BIN}" -m uvicorn app.main:app --host 127.0.0.1 --port "${PORT}" &
SERVER_PID=$!
trap 'kill "${SERVER_PID}" 2>/dev/null || true' EXIT

READY_FILE=$(mktemp)
for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${PORT}/internal/api/v1/ai/ready" > "${READY_FILE}" 2>/dev/null; then
    break
  fi
  sleep 2
done

"${PYTHON_BIN}" - "${READY_FILE}" <<'PY'
import json, pathlib, sys
payload = json.loads(pathlib.Path(sys.argv[1]).read_text())
assert payload["realModelCount"] >= 1, f"REAL 模型未就绪：{payload}"
print("READY realModelCount =", payload["realModelCount"])
PY

"${PYTHON_BIN}" -c 'from tools.benchmark_vision import _make_crack_image; open("/tmp/demo-infer.png","wb").write(_make_crack_image())'
curl -fsS \
  -F "file=@/tmp/demo-infer.png;type=image/png" \
  -F 'metadata={"requestId":"bench-001","mode":"REAL","requestedModelId":"AI-VISION-LOCAL-001"}' \
  "http://127.0.0.1:${PORT}/internal/api/v1/ai/inferences" \
  > /tmp/demo-infer-result.json
"${PYTHON_BIN}" - <<'PY'
import json
payload = json.load(open("/tmp/demo-infer-result.json"))
print("status:", payload.get("status"), "model:", payload.get("model", {}).get("modelId"),
      "version:", payload.get("model", {}).get("version"), "detectionCount:", payload.get("summary", {}).get("detectionCount"))
assert payload["status"] == "SUCCEEDED", payload
assert payload["model"]["modelId"] == "AI-VISION-LOCAL-001", payload
PY

echo "== [3/3b] 5 个并发 REAL 请求（GPU 最大并发=1）=="
CURL_PIDS=()
for i in 1 2 3 4 5; do
  curl -fsS \
    -F "file=@/tmp/demo-infer.png;type=image/png" \
    -F "metadata={\"requestId\":\"conc-$i\",\"mode\":\"REAL\",\"requestedModelId\":\"AI-VISION-LOCAL-001\"}" \
    "http://127.0.0.1:${PORT}/internal/api/v1/ai/inferences" > "/tmp/demo-conc-$i.json" &
  CURL_PIDS+=("$!")
done
wait "${CURL_PIDS[@]}"
for i in 1 2 3 4 5; do
  "${PYTHON_BIN}" -c 'import json; d=json.load(open(f"/tmp/demo-conc-'"$i"'.json")); assert d["status"]=="SUCCEEDED", d; print("  并发", '"$i"', "SUCCEEDED")'
done

echo
echo "Profile B active v${ACTIVE_VERSION} 验证通过。报告：${REPORT}"
