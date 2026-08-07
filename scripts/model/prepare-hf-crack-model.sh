#!/usr/bin/env bash
set -euo pipefail
if [[ $# -lt 3 || $# -gt 4 ]]; then
  echo "用法: bash scripts/model/prepare-hf-crack-model.sh <validation.tsv> <test.tsv> <approved_by> [version]" >&2
  exit 2
fi
VALIDATION_SPLIT="$(realpath "$1")"; TEST_SPLIT="$(realpath "$2")"; APPROVED_BY="$3"; VERSION="${4:-1.0.0}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"; PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
AI_DIR="${PROJECT_ROOT}/ai-service-python"; SOURCE_DIR="${PROJECT_ROOT}/data/model-sources/hf-concrete-crack-unet"
PACKAGE_DIR="${PROJECT_ROOT}/data/model-packages/AI-CRACK-HF-UNET-001/${VERSION}"; MODEL_ROOT="${PROJECT_ROOT}/data/ai-service/models"
VALIDATION_JSON="${PACKAGE_DIR}/validation-evaluation.json"; TEST_JSON="${PACKAGE_DIR}/test-evaluation.json"
[[ -f "${PROJECT_ROOT}/.env" ]] || { echo "缺少根目录 .env" >&2; exit 1; }
[[ -f "${VALIDATION_SPLIT}" && -f "${TEST_SPLIT}" ]] || { echo "验证集或测试集清单不存在" >&2; exit 1; }
cd "${AI_DIR}"
python -m tools.model_pipeline download-hf --output "${SOURCE_DIR}"
python -m tools.model_pipeline export-hf --source-dir "${SOURCE_DIR}" --output "${PACKAGE_DIR}" --version "${VERSION}"
python -m tools.model_pipeline evaluate --package "${PACKAGE_DIR}" --split "${VALIDATION_SPLIT}" --output "${VALIDATION_JSON}" --search-threshold
SELECTED_THRESHOLD="$(python - "${VALIDATION_JSON}" <<'PY'
import json,sys
from pathlib import Path
print(json.loads(Path(sys.argv[1]).read_text(encoding='utf-8'))['selectedThreshold'])
PY
)"
python -m tools.model_pipeline evaluate --package "${PACKAGE_DIR}" --split "${TEST_SPLIT}" --output "${TEST_JSON}" --threshold "${SELECTED_THRESHOLD}"
python -m tools.model_pipeline promote --package "${PACKAGE_DIR}" --evaluation "${TEST_JSON}" --approved-by "${APPROVED_BY}"
python -m tools.model_pipeline install --package "${PACKAGE_DIR}" --model-root "${MODEL_ROOT}" --env-file "${PROJECT_ROOT}/.env" --replace
printf '\n模型已安装。请重启 FastAPI 和 Spring Boot，然后执行 tools.model_pipeline verify。\n'
