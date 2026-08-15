#!/usr/bin/env bash

# 中国大陆网络镜像配置（当前 shell 会话生效，不修改用户全局配置）。
# 用法：source scripts/ai/setup-cn-mirrors.sh
# 原则：只使用可信来源 —— 清华 PyPI、PyTorch 官方 download.pytorch.org、
# hf-mirror、ModelScope 官方 SDK、npmmirror、阿里云公共 Maven 镜像。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
export PROJECT_ROOT

# PyPI：普通依赖使用清华镜像；PyTorch wheel 使用官方 download.pytorch.org。
export PIP_INDEX_URL="${PIP_INDEX_URL:-https://pypi.tuna.tsinghua.edu.cn/simple}"
export PIP_TIMEOUT="${PIP_TIMEOUT:-120}"
export PIP_RETRIES="${PIP_RETRIES:-5}"

# Hugging Face：国内镜像端点 + 统一模型缓存目录。
export HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
export HF_HOME="${HF_HOME:-${PROJECT_ROOT}/data/model-cache/huggingface}"
# 下载阶段允许联网（HF_HUB_OFFLINE=0），运行时由各 profile 单独设置离线。

# npm / Node（如用到前端或 Node 工具）。
export NPM_CONFIG_REGISTRY="${NPM_CONFIG_REGISTRY:-https://registry.npmmirror.com}"

# Maven：项目级镜像配置，不修改用户全局 ~/.m2/settings.xml。
export MAVEN_SETTINGS="${PROJECT_ROOT}/tools/maven/settings-cn.xml"

# 解析 AI Python 解释器（环境独立，不硬编码用户目录）：
# 1. URBAN_SAFE_PYTHON_ENV 显式指定；
# 2. 项目内 .venv-demo-rtx3060（比赛机）；
# 3. 项目内 .venv（开发机）；
# 4. 系统 python3。
resolve_ai_python() {
  if [[ -n "${URBAN_SAFE_PYTHON_ENV:-}" && -x "${URBAN_SAFE_PYTHON_ENV}/bin/python" ]]; then
    echo "${URBAN_SAFE_PYTHON_ENV}/bin/python"
  elif [[ -x "${PROJECT_ROOT}/ai-service-python/.venv-demo-rtx3060/bin/python" ]]; then
    echo "${PROJECT_ROOT}/ai-service-python/.venv-demo-rtx3060/bin/python"
  elif [[ -x "${PROJECT_ROOT}/ai-service-python/.venv/bin/python" ]]; then
    echo "${PROJECT_ROOT}/ai-service-python/.venv/bin/python"
  else
    echo "${PYTHON_BIN:-python3}"
  fi
}

echo "已设置中国大陆镜像环境变量："
echo "  PIP_INDEX_URL=${PIP_INDEX_URL}"
echo "  HF_ENDPOINT=${HF_ENDPOINT}"
echo "  HF_HOME=${HF_HOME}"
echo "  NPM_CONFIG_REGISTRY=${NPM_CONFIG_REGISTRY}"
echo "  MAVEN_SETTINGS=${MAVEN_SETTINGS}"
