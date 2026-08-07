#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
IMAGE_NAME="${URBAN_SAFE_NO_CUDA_IMAGE:-urban-safe-ai-service:no-cuda}"
CONTAINER_NAME="urban-safe-ai-service"
NETWORK_NAME="urban-safe-network"
ACTION="${1:-up}"

log() {
  printf '[no-cuda] %s\n' "$*"
}

fail() {
  printf '[no-cuda] 错误：%s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "未找到命令：$1"
}

read_env_value() {
  local key="$1"
  local default_value="${2:-}"
  local line value

  line="$(grep -E "^[[:space:]]*${key}=" "${ENV_FILE}" | tail -n 1 || true)"
  if [[ -z "${line}" ]]; then
    printf '%s' "${default_value}"
    return
  fi

  value="${line#*=}"
  value="${value%$'\r'}"
  if [[ "${value}" == \"*\" && "${value}" == *\" ]]; then
    value="${value:1:${#value}-2}"
  elif [[ "${value}" == \'*\' && "${value}" == *\' ]]; then
    value="${value:1:${#value}-2}"
  fi
  printf '%s' "${value:-${default_value}}"
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

remove_ai_container() {
  if docker container inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    log "删除已存在的人工智能服务容器 ${CONTAINER_NAME}"
    docker rm -f "${CONTAINER_NAME}" >/dev/null
  fi
}

build_no_cuda_image() {
  local pip_index_url
  pip_index_url="${PIP_INDEX_URL:-https://mirrors.aliyun.com/pypi/simple/}"

  log "构建无计算统一设备架构人工智能服务镜像 ${IMAGE_NAME}"
  docker build \
    --build-arg "PIP_INDEX_URL=${pip_index_url}" \
    --tag "${IMAGE_NAME}" \
    --file - \
    "${PROJECT_ROOT}" <<'DOCKERFILE'
FROM python:3.11-slim

ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL} \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_DEFAULT_TIMEOUT=120

WORKDIR /app

COPY ai-service-python/requirements.txt ./requirements.txt
RUN python -m pip install --no-cache-dir --index-url "${PIP_INDEX_URL}" \
    -r requirements.txt

COPY ai-service-python/app ./app
RUN mkdir -p /app/output /app/models /root/.cache

EXPOSE 8001
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
DOCKERFILE
}

wait_for_ai_service() {
  local status=""
  local attempt

  for attempt in $(seq 1 30); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
    case "${status}" in
      healthy)
        log "人工智能服务已就绪：http://localhost:$(read_env_value URBAN_SAFE_AI_SERVICE_PORT 8001)"
        return
        ;;
      unhealthy|exited|dead)
        docker logs --tail 100 "${CONTAINER_NAME}" >&2 || true
        fail "人工智能服务启动失败，容器状态：${status}"
        ;;
    esac
    sleep 2
  done

  docker logs --tail 100 "${CONTAINER_NAME}" >&2 || true
  fail "等待人工智能服务就绪超时，最后状态：${status:-unknown}"
}

start_all() {
  local ai_port timezone

  [[ -f "${ENV_FILE}" ]] || fail "缺少 ${ENV_FILE}，请先执行 cp .env.example .env"
  require_command docker
  docker info >/dev/null 2>&1 || fail "Docker 守护进程不可用"
  docker compose version >/dev/null 2>&1 || fail "Docker Compose 插件不可用"

  mkdir -p \
    "${PROJECT_ROOT}/data/postgresql" \
    "${PROJECT_ROOT}/data/minio" \
    "${PROJECT_ROOT}/data/ai-service/cache" \
    "${PROJECT_ROOT}/data/ai-service/output" \
    "${PROJECT_ROOT}/data/ai-service/no-cuda-models"

  log "校验根目录配置"
  bash "${PROJECT_ROOT}/scripts/dev/validate-env.sh"
  compose config >/dev/null

  log "启动 PostgreSQL、MinIO 和初始化器"
  compose up -d --build postgresql minio minio-init

  build_no_cuda_image
  remove_ai_container

  ai_port="$(read_env_value URBAN_SAFE_AI_SERVICE_PORT 8001)"
  timezone="$(read_env_value URBAN_SAFE_TIMEZONE Asia/Shanghai)"

  log "启动无计算统一设备架构 FastAPI 容器"
  docker run -d \
    --name "${CONTAINER_NAME}" \
    --restart unless-stopped \
    --network "${NETWORK_NAME}" \
    --env-file "${ENV_FILE}" \
    --env "TZ=${timezone}" \
    --env "PYTHONUNBUFFERED=1" \
    --env "AI_SERVICE_HOST=0.0.0.0" \
    --env "AI_SERVICE_PORT=8001" \
    --env "AI_MODEL_ROOT=/app/models" \
    --env "AI_MODEL_CATALOG_PATH=runtime-catalog.json" \
    --env "AI_REAL_MODEL_STATUS=UNAVAILABLE" \
    --publish "${ai_port}:8001" \
    --volume "${PROJECT_ROOT}/data/ai-service/cache:/root/.cache" \
    --volume "${PROJECT_ROOT}/data/ai-service/output:/app/output" \
    --volume "${PROJECT_ROOT}/data/ai-service/no-cuda-models:/app/models:ro" \
    --health-cmd="python -c \"import urllib.request; urllib.request.urlopen('http://127.0.0.1:8001/internal/api/v1/ai/ready', timeout=3)\"" \
    --health-interval=10s \
    --health-timeout=5s \
    --health-retries=10 \
    --health-start-period=10s \
    "${IMAGE_NAME}" >/dev/null

  wait_for_ai_service
  status_all

  cat <<EOF_NOTICE

无计算统一设备架构模式已启动。
- PostgreSQL、MinIO 和 FastAPI 均运行在 Docker 中。
- FastAPI 仅安装基础依赖，适用于 MOCK 请求与业务联调。
- REAL 请求不会静默降级；由于未安装真实模型运行时，应返回模型不可用。
- 查看日志：bash scripts/dev/start-docker-no-cuda.sh logs
- 停止服务：bash scripts/dev/start-docker-no-cuda.sh down
EOF_NOTICE
}

stop_all() {
  [[ -f "${ENV_FILE}" ]] || fail "缺少 ${ENV_FILE}"
  require_command docker
  remove_ai_container
  log "停止基础设施容器"
  compose down
}

status_all() {
  [[ -f "${ENV_FILE}" ]] || fail "缺少 ${ENV_FILE}"
  require_command docker
  compose ps postgresql minio minio-init
  printf '\n'
  docker ps -a \
    --filter "name=^/${CONTAINER_NAME}$" \
    --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
}

logs_ai() {
  require_command docker
  docker container inspect "${CONTAINER_NAME}" >/dev/null 2>&1 \
    || fail "容器 ${CONTAINER_NAME} 不存在"
  docker logs -f "${CONTAINER_NAME}"
}

case "${ACTION}" in
  up)
    start_all
    ;;
  down)
    stop_all
    ;;
  restart)
    stop_all
    start_all
    ;;
  status|ps)
    status_all
    ;;
  logs)
    logs_ai
    ;;
  *)
    cat >&2 <<'EOF_USAGE'
用法：
  bash scripts/dev/start-docker-no-cuda.sh [up|down|restart|status|logs]
EOF_USAGE
    exit 2
    ;;
esac
