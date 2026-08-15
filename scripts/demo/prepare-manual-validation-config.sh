#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

cd "${PROJECT_ROOT}"

if [[ ! -f "${ENV_FILE}" ]]; then
  cp "${PROJECT_ROOT}/.env.example" "${ENV_FILE}"
  echo "已创建根目录 .env。请先填写数据库、MinIO、JWT、高德等本机必填配置，再重新运行本脚本。" >&2
  exit 2
fi

BACKUP="${PROJECT_ROOT}/.env.backup-manual-validation-$(date +%Y%m%d-%H%M%S)"
cp "${ENV_FILE}" "${BACKUP}"

python3 - "${ENV_FILE}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
updates = {
    "URBAN_SAFE_AI_SERVICE_PORT": "8001",
    "URBAN_SAFE_AI_SERVICE_BASE_URL": "http://localhost:8001",
    "URBAN_SAFE_AI_DEFAULT_MODE": "REAL",
    "URBAN_SAFE_AI_MODEL_ROOT": "data/model-cache",
    "URBAN_SAFE_AI_MODEL_CATALOG_PATH": "runtime-catalog.json",
    "URBAN_SAFE_AI_CUDA_DEVICE_ID": "0",
    "URBAN_SAFE_AI_VISUAL_DEVICE": "cuda",
    "URBAN_SAFE_AI_VISION_DTYPE": "float16",
    "URBAN_SAFE_AI_VISION_OFFLINE": "true",
    "URBAN_SAFE_AI_VISION_HF_HOME": "data/model-cache/huggingface",
    "URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY": "1",
    "URBAN_SAFE_AI_VISION_SHA_MODE": "STRICT",
}
lines = path.read_text(encoding="utf-8").splitlines()
seen = set()
out = []
for line in lines:
    if "=" in line and not line.lstrip().startswith("#"):
        key = line.split("=", 1)[0].strip()
        if key in updates:
            out.append(f"{key}={updates[key]}")
            seen.add(key)
            continue
    out.append(line)
for key, value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")
path.write_text("\n".join(out) + "\n", encoding="utf-8")
PY

FRONT_ENV="${PROJECT_ROOT}/frontend/.env.local"
if [[ ! -f "${FRONT_ENV}" ]]; then
  cp "${PROJECT_ROOT}/frontend/.env.example" "${FRONT_ENV}"
fi
python3 - "${FRONT_ENV}" <<'PY'
from pathlib import Path
import sys
path = Path(sys.argv[1])
updates = {
    "VITE_API_MODE": "real",
    "VITE_API_BASE_URL": "http://localhost:8888",
}
lines = path.read_text(encoding="utf-8").splitlines()
seen=set(); out=[]
for line in lines:
    if "=" in line and not line.lstrip().startswith("#"):
        key=line.split("=",1)[0].strip()
        if key in updates:
            out.append(f"{key}={updates[key]}"); seen.add(key); continue
    out.append(line)
for key,value in updates.items():
    if key not in seen:
        out.append(f"{key}={value}")
path.write_text("\n".join(out)+"\n", encoding="utf-8")
PY

echo "比赛机非敏感运行参数已准备："
echo "  FastAPI = http://localhost:8001"
echo "  mode = REAL"
echo "  model root = data/model-cache"
echo "  device = cuda / float16"
echo "  offline = true / concurrency = 1 / SHA = STRICT"
echo "  frontend = real → http://localhost:8888"
echo "备份：${BACKUP}"
echo
echo "本脚本没有修改 DeepSeek/Dify API Key，也不会打印任何密钥。"
echo "下一步：bash scripts/demo/preflight-manual-validation.sh"
