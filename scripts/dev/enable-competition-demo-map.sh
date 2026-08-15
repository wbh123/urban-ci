#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：请在 urban-safe-priority Git 仓库内执行。" >&2
  exit 2
fi

ENV_FILE="${ROOT_DIR}/.env"
EXAMPLE_FILE="${ROOT_DIR}/.env.example"

if [[ ! -f "${ENV_FILE}" ]]; then
  if [[ ! -f "${EXAMPLE_FILE}" ]]; then
    echo "错误：缺少 .env 和 .env.example。" >&2
    exit 2
  fi
  cp "${EXAMPLE_FILE}" "${ENV_FILE}"
  echo "已由 .env.example 创建本地 .env，请检查数据库密码、地图密钥等真实配置。"
fi

set_env_value() {
  local key="$1"
  local value="$2"
  if grep -q "^${key}=" "${ENV_FILE}"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "${ENV_FILE}"
  else
    printf '\n%s=%s\n' "${key}" "${value}" >> "${ENV_FILE}"
  fi
}

set_env_value "URBAN_SAFE_MAP_ENABLED" "true"
set_env_value "URBAN_SAFE_AMAP_BOUNDARY_CANDIDATE_ENABLED" "true"

echo "已启用："
echo "  URBAN_SAFE_MAP_ENABLED=true"
echo "  URBAN_SAFE_AMAP_BOUNDARY_CANDIDATE_ENABLED=true"

WEB_KEY="$(grep '^URBAN_SAFE_AMAP_WEB_SERVICE_KEY=' "${ENV_FILE}" | tail -n 1 | cut -d= -f2- || true)"
JS_KEY="$(grep '^URBAN_SAFE_AMAP_JS_API_KEY=' "${ENV_FILE}" | tail -n 1 | cut -d= -f2- || true)"

if [[ -z "${JS_KEY}" ]]; then
  echo "警告：URBAN_SAFE_AMAP_JS_API_KEY 为空，浏览器地图无法进入 LIVE 模式。"
fi
if [[ -z "${WEB_KEY}" ]]; then
  echo "警告：URBAN_SAFE_AMAP_WEB_SERVICE_KEY 为空，候选边界查询仍会返回“未配置”。"
fi

echo "配置已写入 ${ENV_FILE}。Spring Boot 需要重启后读取新配置。"
