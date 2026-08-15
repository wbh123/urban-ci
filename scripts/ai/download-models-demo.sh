#!/usr/bin/env bash

# Profile B 精度优先候选：下载 Grounding DINO Base + SAM 2.1 Hiera Base+。
# 业务模型编号仍为 AI-VISION-LOCAL-001，内部版本 1.1.0。
# 下载仅生成 CANDIDATE，不覆盖当前 active runtime-catalog.json（Tiny 1.0.0 可继续回滚）。
# 中国大陆网络默认使用韧性下载入口：ModelScope / huggingface_hub 失败后，direct 会逐源
# 校验 model.safetensors 固定 SHA；镜像 HTTP 200 但内容错误时自动切换下一 endpoint。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/setup-cn-mirrors.sh"

AI_SERVICE="${PROJECT_ROOT}/ai-service-python"
PYTHON_BIN="$(resolve_ai_python)"

if [[ "${PYTHON_BIN}" == "python3" ]] || ! "${PYTHON_BIN}" -c 'import modelscope, huggingface_hub' 2>/dev/null; then
  echo "未找到含 modelscope/huggingface_hub 的 Python 环境（请先运行 setup-demo-rtx3060.sh）" >&2
  exit 1
fi

export HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
export HF_HUB_OFFLINE=0
export HF_HUB_DISABLE_XET=1

cd "${AI_SERVICE}"
"${PYTHON_BIN}" -m tools.download_vision_models_resilient \
  --model-root "${PROJECT_ROOT}/data/model-cache"

echo
echo "Base/Base+ v1.1.0 下载完成（CANDIDATE），当前 active Tiny 不受影响。"
echo "注意：benchmark/approve 工具中的相对路径统一相对项目根目录解析，不相对当前 shell 目录解析。"
echo "下一步先执行："
echo "  cd ${PROJECT_ROOT}/ai-service-python"
echo "  ./.venv-demo-rtx3060/bin/python -m tools.benchmark_vision --version 1.1.0 --iterations 20 --model-root data/model-cache --report data/model-benchmarks/rtx3060-base-1.1.0-report.md"
echo "确认显存、稳定性与真实图片效果后，再人工批准："
echo "  ./.venv-demo-rtx3060/bin/python -m tools.approve_vision_model --version 1.1.0 --model-root data/model-cache --report data/model-benchmarks/rtx3060-base-1.1.0-report.md --approver <姓名>"