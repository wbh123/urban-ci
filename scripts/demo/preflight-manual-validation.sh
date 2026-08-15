#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

DEEP=false
if [[ "${1:-}" == "--deep" ]]; then DEEP=true; fi

cd "${PROJECT_ROOT}"
echo "========================================"
echo " 城安智序比赛机手动验证前置检查"
echo "========================================"

for command in bash curl docker java mvn node npm python3 nvidia-smi setsid; do require_command "${command}"; done
docker compose version >/dev/null

[[ -f "${ENV_FILE}" ]] || fail "缺少根目录 .env，请先 cp .env.example .env 并填写本机配置"
bash "${PROJECT_ROOT}/scripts/dev/validate-env.sh"

errors=0
expect_env URBAN_SAFE_AI_DEFAULT_MODE REAL || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_SERVICE_BASE_URL http://localhost:8001 || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_MODEL_ROOT data/model-cache || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_VISUAL_DEVICE cuda || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_VISION_DTYPE float16 || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_VISION_OFFLINE true || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY 1 || errors=$((errors + 1))
expect_env URBAN_SAFE_AI_VISION_SHA_MODE STRICT || errors=$((errors + 1))
(( errors == 0 )) || fail "比赛机 REAL 配置尚未收口，请按上方提示修改根目录 .env"

AI_PYTHON="$(resolve_ai_python)"
[[ -x "${AI_PYTHON}" ]] || fail "未找到比赛机 Python 环境，请先运行 bash scripts/ai/setup-demo-rtx3060.sh"
pass "AI Python：${AI_PYTHON}"

nvidia-smi --query-gpu=name,driver_version,memory.total --format=csv,noheader
"${AI_PYTHON}" - <<'PY'
import torch
name = torch.cuda.get_device_name(0) if torch.cuda.is_available() else None
print("torch =", torch.__version__)
print("CUDA runtime =", torch.version.cuda)
print("CUDA available =", torch.cuda.is_available())
print("GPU =", name)
if not torch.cuda.is_available(): raise SystemExit("CUDA 不可用")
if name is None or "3060" not in name: raise SystemExit(f"当前 GPU 不是 RTX3060：{name}")
PY
pass "RTX3060 CUDA 环境可用"

MODEL_ROOT="${PROJECT_ROOT}/data/model-cache"
CATALOG="${MODEL_ROOT}/runtime-catalog.json"
[[ -f "${CATALOG}" ]] || fail "缺少 runtime catalog：${CATALOG}"

ACTIVE_INFO="$("${AI_PYTHON}" - "${CATALOG}" <<'PY'
import json, pathlib, sys
catalog = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
items = [x for x in catalog.get("models", []) if x.get("modelId") == "AI-VISION-LOCAL-001" and x.get("enabled", True)]
assert len(items) == 1, f"runtime-catalog 必须恰好启用一个 AI-VISION-LOCAL-001，实际 {len(items)}"
item = items[0]
print(f"{item['version']}|{item['manifestPath']}")
PY
)"
ACTIVE_VERSION="${ACTIVE_INFO%%|*}"
MANIFEST_RELATIVE="${ACTIVE_INFO#*|}"
MANIFEST="${MODEL_ROOT}/${MANIFEST_RELATIVE}"
[[ -f "${MANIFEST}" ]] || fail "缺少 active 模型 manifest：${MANIFEST}"
info "当前 active 视觉模型：AI-VISION-LOCAL-001 v${ACTIVE_VERSION}"

"${AI_PYTHON}" - "${MANIFEST}" "${CATALOG}" <<'PY'
import json, pathlib, sys
manifest = json.loads(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
catalog = json.loads(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
assert manifest.get("status") == "APPROVED", manifest.get("status")
assert manifest.get("identityVerified") is True, manifest.get("identityVerified")
checkpoint = manifest.get("checkpoint") or {}
for key in ("detectorRevision", "segmenterRevision"):
    value = str(checkpoint.get(key, ""))
    assert len(value) == 40 and value not in {"main", "master"}, (key, value)
items = [x for x in catalog.get("models", []) if x.get("modelId") == "AI-VISION-LOCAL-001" and x.get("enabled", True)]
assert len(items) == 1
assert items[0].get("version") == manifest.get("version"), (items[0].get("version"), manifest.get("version"))
print(f"status=APPROVED identityVerified=true revision=fixed activeVersion={manifest.get('version')}")
PY
pass "active 模型准入与目录检查通过"

if [[ -f "${PROJECT_ROOT}/frontend/.env.local" ]]; then
  FRONT_MODE="$(grep -E '^VITE_API_MODE=' "${PROJECT_ROOT}/frontend/.env.local" | tail -n1 | cut -d= -f2- || true)"
  FRONT_API="$(grep -E '^VITE_API_BASE_URL=' "${PROJECT_ROOT}/frontend/.env.local" | tail -n1 | cut -d= -f2- || true)"
  [[ "${FRONT_MODE}" == "real" ]] || fail "frontend/.env.local 中 VITE_API_MODE 必须为 real"
  [[ "${FRONT_API}" == "http://localhost:8888" ]] || fail "frontend/.env.local 中 VITE_API_BASE_URL 必须为 http://localhost:8888"
  pass "前端真实后端模式已配置"
else
  info "frontend/.env.local 尚不存在；start-manual-validation.sh 会从模板创建并切换为 real"
fi

for port in 8001 8888 5173; do
  if port_open 127.0.0.1 "${port}"; then
    info "端口 ${port} 当前已有监听；启动脚本会识别已有服务或拒绝冲突"
  else
    pass "端口 ${port} 当前可用"
  fi
done

if [[ "${DEEP}" == "true" ]]; then
  echo
  info "执行 active v${ACTIVE_VERSION} 深度 RTX3060 验证（20次 + REAL FastAPI + 5并发）"
  bash "${PROJECT_ROOT}/scripts/ai/verify-demo-rtx3060.sh"
fi

echo
pass "手动验证前置检查完成（active v${ACTIVE_VERSION}）"
echo "下一步：bash scripts/demo/start-manual-validation.sh --build --seed"
