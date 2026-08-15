#!/usr/bin/env bash

# Profile A：开发机（CPU/核显/无 CUDA）不需要任何真实视觉模型权重。
# MOCK 推理与 CPU 图片适用性门禁均可无权重运行；适用性门禁权重缺失时以 UNCERTAIN 放行。

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/setup-cn-mirrors.sh"

mkdir -p "${HF_HOME}"
echo "开发机（CPU）不加载 Grounding DINO / SAM2，无需下载权重。"
echo "已确保模型缓存目录：${HF_HOME}"
echo
echo "图片适用性门禁权重为可选；如需要，放到："
echo "  ${PROJECT_ROOT}/data/ai-service/no-cuda-models/image-applicability/model.onnx"
echo "  ${PROJECT_ROOT}/data/ai-service/no-cuda-models/image-applicability/model.json"
