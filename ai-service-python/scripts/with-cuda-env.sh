#!/usr/bin/env bash

# 为模型安装、评估、验证和服务启动统一准备 CUDA-only 动态库环境。
# 示例：
#   ./scripts/with-cuda-env.sh python -m tools.model_pipeline install ...
#   ./scripts/with-cuda-env.sh python -m pytest -q

set -euo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "用法：with-cuda-env.sh <command> [args...]" >&2
  exit 2
fi

PYTHON_ENVIRONMENT_PREFIX="${URBAN_SAFE_PYTHON_ENV:-/home/xq/miniforge3/envs/urban}"
PYTHON_EXECUTABLE="${PYTHON_ENVIRONMENT_PREFIX}/bin/python"
if [[ ! -x "${PYTHON_EXECUTABLE}" ]]; then
  echo "Python 环境不存在：${PYTHON_EXECUTABLE}" >&2
  exit 1
fi

# WSL 中 Python 可能继承 Windows TEMP 路径，pytest 捕获和部分图像/模型工具会在
# 跨文件系统临时文件上出现偶发删除或截断失败；默认固定到 Linux 本地临时目录。
export TMPDIR="${TMPDIR:-/tmp}"
if [[ ! -d "${TMPDIR}" || ! -w "${TMPDIR}" ]]; then
  echo "临时目录不可用：${TMPDIR}" >&2
  exit 1
fi

PYTHON_SITE_PACKAGES="$(${PYTHON_EXECUTABLE} - <<'PY'
import site
paths = site.getsitepackages()
if not paths:
    raise SystemExit("无法定位 site-packages")
print(paths[0])
PY
)"

LIBRARY_DIRECTORIES=(
  "/usr/lib/wsl/lib"
  "${PYTHON_ENVIRONMENT_PREFIX}/lib"
)
while IFS= read -r directory; do
  LIBRARY_DIRECTORIES+=("${directory}")
done < <(find "${PYTHON_SITE_PACKAGES}/nvidia" -mindepth 2 -maxdepth 2 -type d -name lib 2>/dev/null | sort)

JOINED_LIBRARY_PATH=""
for directory in "${LIBRARY_DIRECTORIES[@]}"; do
  if [[ -d "${directory}" ]]; then
    JOINED_LIBRARY_PATH="${JOINED_LIBRARY_PATH:+${JOINED_LIBRARY_PATH}:}${directory}"
  fi
done
export LD_LIBRARY_PATH="${JOINED_LIBRARY_PATH}${LD_LIBRARY_PATH:+:${LD_LIBRARY_PATH}}"

"${PYTHON_EXECUTABLE}" - <<'PY'
import onnxruntime as ort

providers = ort.get_available_providers()
if "CUDAExecutionProvider" not in providers:
    raise SystemExit(
        "CUDAExecutionProvider 不可用；请检查 NVIDIA 驱动、CUDA/cuDNN 动态库和 onnxruntime-gpu"
    )
PY

# 文档中的 python 自动替换为当前 mamba 环境解释器，避免误用系统 Python。
if [[ "$1" == "python" ]]; then
  shift
  set -- "${PYTHON_EXECUTABLE}" "$@"
fi
exec "$@"
