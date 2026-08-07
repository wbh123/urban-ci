#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${repository_root}/.env"
example_file="${repository_root}/.env.example"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少根目录 .env。请执行：cp .env.example .env" >&2
  exit 1
fi

declare -A env_values=()
line_number=0
while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
  line_number=$((line_number + 1))
  line="${raw_line%$'\r'}"

  # 去除行首空白，仅用于识别空行和注释；配置值本身不执行 Shell 展开。
  trimmed="${line#"${line%%[![:space:]]*}"}"
  if [[ -z "${trimmed}" || "${trimmed}" == \#* ]]; then
    continue
  fi

  if [[ "${line}" != *=* ]]; then
    echo ".env 第 ${line_number} 行格式错误，必须使用 KEY=VALUE" >&2
    exit 1
  fi

  key="${line%%=*}"
  value="${line#*=}"
  key="${key//[[:space:]]/}"

  if [[ ! "${key}" =~ ^[A-Za-z_][A-Za-z0-9_]*$ ]]; then
    echo ".env 第 ${line_number} 行变量名不合法：${key}" >&2
    exit 1
  fi

  # 支持可选的成对单引号或双引号，但绝不执行变量替换、命令替换或反斜杠转义。
  if [[ ${#value} -ge 2 ]]; then
    first="${value:0:1}"
    last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi

  env_values["${key}"]="${value}"
done < "${env_file}"

required_variables=(
  URBAN_SAFE_TIMEZONE
  URBAN_SAFE_SERVER_PORT
  URBAN_SAFE_AI_SERVICE_PORT
  URBAN_SAFE_POSTGRES_IMAGE
  URBAN_SAFE_DB_HOST
  URBAN_SAFE_DB_PORT
  URBAN_SAFE_DB_NAME
  URBAN_SAFE_DB_USER
  URBAN_SAFE_DB_PASSWORD
  URBAN_SAFE_AUTH_JWT_ISSUER
  URBAN_SAFE_AUTH_JWT_AUDIENCE
  URBAN_SAFE_AUTH_JWT_SECRET
  URBAN_SAFE_AUTH_ACCESS_TOKEN_TTL_SECONDS
  URBAN_SAFE_BOOTSTRAP_ADMIN_ENABLED
  URBAN_SAFE_BOOTSTRAP_ADMIN_USERNAME
  URBAN_SAFE_BOOTSTRAP_ADMIN_PASSWORD
  URBAN_SAFE_BOOTSTRAP_ADMIN_REAL_NAME
  URBAN_SAFE_CORS_ALLOWED_ORIGINS
  URBAN_SAFE_CORS_ALLOWED_METHODS
  URBAN_SAFE_CORS_ALLOWED_HEADERS
  URBAN_SAFE_CORS_ALLOW_CREDENTIALS
  URBAN_SAFE_MAP_ENABLED
  URBAN_SAFE_MAP_PROVIDER
  URBAN_SAFE_MAP_DEFAULT_CENTER_LONGITUDE
  URBAN_SAFE_MAP_DEFAULT_CENTER_LATITUDE
  URBAN_SAFE_MAP_DEFAULT_ZOOM
  URBAN_SAFE_AMAP_JS_API_KEY
  URBAN_SAFE_AMAP_SECURITY_JS_CODE
  URBAN_SAFE_AMAP_SERVICE_HOST
  URBAN_SAFE_AMAP_WEB_SERVICE_KEY
  URBAN_SAFE_AMAP_WEB_SERVICE_BASE_URL
  URBAN_SAFE_AMAP_CONNECT_TIMEOUT_MS
  URBAN_SAFE_AMAP_READ_TIMEOUT_MS
  URBAN_SAFE_AMAP_CACHE_TTL_SECONDS
  URBAN_SAFE_AMAP_LIVE_TEST_ENABLED
  MINIO_SERVER_IMAGE
  MINIO_CLIENT_IMAGE
  URBAN_SAFE_MINIO_HOST
  URBAN_SAFE_MINIO_API_PORT
  URBAN_SAFE_MINIO_CONSOLE_PORT
  URBAN_SAFE_MINIO_ROOT_USER
  URBAN_SAFE_MINIO_ROOT_PASSWORD
  URBAN_SAFE_MINIO_APP_USER
  URBAN_SAFE_MINIO_APP_PASSWORD
  URBAN_SAFE_MINIO_POLICY_NAME
  URBAN_SAFE_MINIO_ASSETS_BUCKET
  URBAN_SAFE_MINIO_REPORTS_BUCKET
  URBAN_SAFE_MINIO_MODELS_BUCKET
  URBAN_SAFE_STORAGE_PROVIDER
  URBAN_SAFE_STORAGE_LOCAL_DIRECTORY
  URBAN_SAFE_STORAGE_MAX_IMAGE_SIZE_BYTES
  URBAN_SAFE_STORAGE_MAX_REQUEST_SIZE_BYTES
  URBAN_SAFE_STORAGE_PREVIEW_EXPIRY_SECONDS
)

errors=0
for variable in "${required_variables[@]}"; do
  value="${env_values[${variable}]-}"
  if [[ -z "${value}" ]]; then
    echo "缺少配置：${variable}" >&2
    errors=$((errors + 1))
  elif [[ "${value}" == *"请替换"* ]]; then
    echo "配置仍为模板占位值：${variable}" >&2
    errors=$((errors + 1))
  fi
done

root_user="${env_values[URBAN_SAFE_MINIO_ROOT_USER]-}"
app_user="${env_values[URBAN_SAFE_MINIO_APP_USER]-}"
storage_provider="${env_values[URBAN_SAFE_STORAGE_PROVIDER]-}"
max_image_size="${env_values[URBAN_SAFE_STORAGE_MAX_IMAGE_SIZE_BYTES]-0}"
max_request_size="${env_values[URBAN_SAFE_STORAGE_MAX_REQUEST_SIZE_BYTES]-0}"

if [[ -n "${root_user}" && "${root_user}" == "${app_user}" ]]; then
  echo "MinIO 根账号与应用账号不能相同" >&2
  errors=$((errors + 1))
fi

if [[ "${storage_provider}" != "MINIO" ]]; then
  echo "当前开发基线要求 URBAN_SAFE_STORAGE_PROVIDER=MINIO" >&2
  errors=$((errors + 1))
fi

if [[ "${max_image_size}" =~ ^[0-9]+$ && "${max_request_size}" =~ ^[0-9]+$ ]]; then
  if (( max_image_size <= 0 || max_request_size <= 0 )); then
    echo "上传大小配置必须大于 0" >&2
    errors=$((errors + 1))
  elif (( max_request_size < max_image_size )); then
    echo "URBAN_SAFE_STORAGE_MAX_REQUEST_SIZE_BYTES 不能小于图片大小限制" >&2
    errors=$((errors + 1))
  fi
else
  echo "上传大小配置必须是正整数" >&2
  errors=$((errors + 1))
fi

if (( errors > 0 )); then
  echo "配置校验失败，共 ${errors} 项。参考：${example_file}" >&2
  exit 1
fi

echo "配置校验通过：${env_file}"
echo "- PostgreSQL 镜像：${env_values[URBAN_SAFE_POSTGRES_IMAGE]}"
echo "- PostgreSQL：${env_values[URBAN_SAFE_DB_HOST]}:${env_values[URBAN_SAFE_DB_PORT]}/${env_values[URBAN_SAFE_DB_NAME]}"
echo "- MinIO API：http://${env_values[URBAN_SAFE_MINIO_HOST]}:${env_values[URBAN_SAFE_MINIO_API_PORT]}"
echo "- MinIO Console：http://${env_values[URBAN_SAFE_MINIO_HOST]}:${env_values[URBAN_SAFE_MINIO_CONSOLE_PORT]}"
echo "- 对象存储账号：${app_user}"
echo "- 业务存储桶：${env_values[URBAN_SAFE_MINIO_ASSETS_BUCKET]}"
