#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
OPENAPI_SOURCE="${REPOSITORY_ROOT}/backend-java/model/src/main/resources/openapi-interface.yaml"
APPLICATION_YAML="${REPOSITORY_ROOT}/backend-java/starter/src/main/resources/application.yaml"
OUTPUT_DIR="${1:-${REPOSITORY_ROOT}/build/apifox}"
OPENAPI_OUTPUT="${OUTPUT_DIR}/urban-safe-priority-openapi.json"
ALL_COLLECTION="${OUTPUT_DIR}/urban-safe-priority-all.postman_collection.json"
PRIMARY_COLLECTION="${OUTPUT_DIR}/urban-safe-priority-apifox.postman_collection.json"

command -v node >/dev/null 2>&1 || {
  echo "缺少 Node.js，无法运行 OpenAPI 转换工具。" >&2
  exit 1
}
command -v npx >/dev/null 2>&1 || {
  echo "缺少 npx，无法运行 OpenAPI 转换工具。" >&2
  exit 1
}
command -v python3 >/dev/null 2>&1 || {
  echo "缺少 Python 3，无法生成 ApiFox 集合。" >&2
  exit 1
}

mkdir -p "${OUTPUT_DIR}"
python3 "${SCRIPT_DIR}/prepare_local_config.py" "${APPLICATION_YAML}"

npx --yes @redocly/cli@1.27.1 lint "${OPENAPI_SOURCE}"
npx --yes @redocly/cli@1.27.1 bundle \
  "${OPENAPI_SOURCE}" \
  --output "${OPENAPI_OUTPUT}"

python3 "${SCRIPT_DIR}/enrich_openapi.py" "${OPENAPI_OUTPUT}"
npx --yes --package openapi-to-postmanv2@6.3.0 openapi2postmanv2 \
  --spec "${OPENAPI_OUTPUT}" \
  --output "${ALL_COLLECTION}" \
  --pretty \
  --options "folderStrategy=Tags,parametersResolution=Example,includeAuthInfoInExample=false"

python3 "${SCRIPT_DIR}/generate_apifox_assets.py" \
  --application "${APPLICATION_YAML}" \
  --output "${OUTPUT_DIR}"
python3 "${SCRIPT_DIR}/finalize_apifox_export.py" \
  --output "${OUTPUT_DIR}" \
  --openapi "${OPENAPI_OUTPUT}"

cat <<EOF

ApiFox 导入文件已生成：
1. 推荐直接导入：${PRIMARY_COLLECTION}
2. 全部 OpenAPI 接口集合：${ALL_COLLECTION}
3. 可选 OpenAPI 文档：${OPENAPI_OUTPUT}
4. 核心快速验收：${OUTPUT_DIR}/urban-safe-priority-smoke.postman_collection.json
5. 含图片完整验收：${OUTPUT_DIR}/urban-safe-priority-full.postman_collection.json

推荐只导入第 1 个 Collection 文件。它同时包含全部 OpenAPI 接口和自动验收目录，
并已内置项目变量、登录后置提取及逐接口鉴权，无需再导入或选择 Postman Environment。
EOF
