#!/usr/bin/env bash

# Profile B：比赛机 RTX 3060 6GB 环境准备。
# 环境独立：URBAN_SAFE_PYTHON_ENV 显式指定时使用该环境；否则在项目内创建
# ai-service-python/.venv-demo-rtx3060（不依赖任何用户目录）。安装锁定依赖。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/setup-cn-mirrors.sh"

AI_SERVICE="${PROJECT_ROOT}/ai-service-python"
DEMO_VENV="${AI_SERVICE}/.venv-demo-rtx3060"

if [[ -n "${URBAN_SAFE_PYTHON_ENV:-}" && -x "${URBAN_SAFE_PYTHON_ENV}/bin/python" ]]; then
  PYTHON_BIN="${URBAN_SAFE_PYTHON_ENV}/bin/python"
  echo "使用显式 Python 环境：${PYTHON_BIN}"
else
  if [[ ! -x "${DEMO_VENV}/bin/python" ]]; then
    echo "创建比赛机独立环境：${DEMO_VENV}"
    "${PYTHON_BIN:-python3}" -m venv "${DEMO_VENV}"
  fi
  PYTHON_BIN="${DEMO_VENV}/bin/python"
fi

# CUDA torch 只从官方 index 安装，不得使用未知镜像。
# 唯一事实来源：2026-08-11 在 RTX 3060 Laptop GPU 6GB 验证通过的环境
#   torch 2.9.1 / torchvision 0.24.1 / torch.version.cuda=12.9 → whl/cu129
PYTORCH_INDEX="https://download.pytorch.org/whl/cu129"
TORCH_EXPECT="2.9.1"
TORCHVISION_EXPECT="0.24.1"
CUDA_EXPECT="12.9"

if ! "${PYTHON_BIN}" -c 'import torch; raise SystemExit(0 if torch.cuda.is_available() else 1)'; then
  echo "未检测到可用 CUDA 的 torch，按已验证官方 index 安装："
  "${PYTHON_BIN}" -m pip install \
    "torch==${TORCH_EXPECT}" \
    "torchvision==${TORCHVISION_EXPECT}" \
    --index-url "${PYTORCH_INDEX}"
fi

# 安装锁定依赖（已验证组合），禁止随意升级。
"${PYTHON_BIN}" -m pip install -r "${AI_SERVICE}/requirements-vision-lock.txt"

# 强制校验与已验证环境一致；允许官方 CUDA Wheel 的本地版本后缀（如 2.9.1+cu129），
# 但 base version 与 CUDA Runtime 必须严格匹配。
"${PYTHON_BIN}" - "${TORCH_EXPECT}" "${TORCHVISION_EXPECT}" "${CUDA_EXPECT}" <<'PY'
import sys
import torch
import torchvision

expect_torch, expect_tv, expect_cuda = sys.argv[1], sys.argv[2], sys.argv[3]
failed = []

def base_version(value: str) -> str:
    return str(value).split("+", 1)[0]

torch_full = str(torch.__version__)
tv_full = str(torchvision.__version__)
torch_base = base_version(torch_full)
tv_base = base_version(tv_full)

if torch_base != expect_torch:
    failed.append(f"torch base={torch_base} (full={torch_full}) != {expect_torch}")
if tv_base != expect_tv:
    failed.append(f"torchvision base={tv_base} (full={tv_full}) != {expect_tv}")
if torch.version.cuda != expect_cuda:
    failed.append(f"torch.version.cuda={torch.version.cuda} != {expect_cuda}")
if not torch.cuda.is_available():
    failed.append("torch.cuda.is_available()=False")
if failed:
    print("环境校验失败（与已验证环境不一致）：" + "; ".join(failed), file=sys.stderr)
    sys.exit(1)
print(
    "环境校验通过：torch", torch_full,
    "(base", torch_base + ")",
    "torchvision", tv_full,
    "(base", tv_base + ")",
    "CUDA", torch.version.cuda,
    "GPU", torch.cuda.get_device_name(0),
)
PY

if [[ ! -f "${PROJECT_ROOT}/.env" ]]; then
  cp "${PROJECT_ROOT}/config/ai/profiles/demo-rtx3060.env.example" "${PROJECT_ROOT}/.env"
  echo "已从 demo-rtx3060.env.example 生成 .env（请按需修改）"
else
  echo "已存在 .env，跳过生成"
fi

echo
echo "下一步："
echo "  bash scripts/ai/download-models-demo.sh   # 下载模型（CANDIDATE）"
echo "  bash scripts/ai/verify-demo-rtx3060.sh    # 基准 + FastAPI 冒烟"
echo "  批准后模型才进入 REAL：python -m tools.approve_vision_model --approver <姓名>"