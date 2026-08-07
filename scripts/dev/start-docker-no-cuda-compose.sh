#!/usr/bin/env bash
set -Eeuo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.no-cuda.yml"
ACTION="${1:-up}"

log() {
  printf '[no-cuda-compose] %s\n' "$*"
}

fail() {
  printf '[no-cuda-compose] 错误：%s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "未找到命令：$1"
}

compose() {
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" "$@"
}

prepare() {
  [[ -f "${ENV_FILE}" ]] || fail "缺少 ${ENV_FILE}，请先执行 cp .env.example .env"
  [[ -f "${COMPOSE_FILE}" ]] || fail "缺少 ${COMPOSE_FILE}"
  require_command docker
  docker info >/dev/null 2>&1 || fail "Docker 守护进程不可用"
  docker compose version >/dev/null 2>&1 || fail "Docker Compose 插件不可用"

  mkdir -p \
    "${PROJECT_ROOT}/data/postgresql" \
    "${PROJECT_ROOT}/data/minio" \
    "${PROJECT_ROOT}/data/ai-service/cache" \
    "${PROJECT_ROOT}/data/ai-service/output" \
    "${PROJECT_ROOT}/data/ai-service/no-cuda-models"

  bash "${PROJECT_ROOT}/scripts/dev/validate-env.sh"
}

wait_for_ai_service() {
  local status=""
  local attempt
  for attempt in $(seq 1 30); do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' urban-safe-ai-service 2>/dev/null || true)"
    case "${status}" in
      healthy)
        log "FastAPI 模拟服务已就绪"
        return
        ;;
      unhealthy|exited|dead)
        compose logs --tail 100 ai-service >&2 || true
        fail "人工智能服务启动失败，容器状态：${status}"
        ;;
    esac
    sleep 2
  done
  compose logs --tail 100 ai-service >&2 || true
  fail "等待人工智能服务就绪超时，最后状态：${status:-unknown}"
}

start_all() {
  prepare
  log "校验无 CUDA Compose 配置"
  compose config >/dev/null
  log "构建并启动 PostgreSQL、MinIO 与无 CUDA FastAPI"
  compose up -d --build
  wait_for_ai_service
  compose ps
  cat <<'EOF_NOTICE'

无 CUDA Compose 模式已启动：
- PostgreSQL、MinIO、MinIO 初始化器和 FastAPI 均由同一个 Compose 文件管理；
- FastAPI 只安装基础依赖，适用于 MOCK 请求和业务联调；
- REAL 请求不会静默降级，应稳定返回模型不可用；
- 查看日志：bash scripts/dev/start-docker-no-cuda-compose.sh logs
- 查看状态：bash scripts/dev/start-docker-no-cuda-compose.sh status
- 停止服务：bash scripts/dev/start-docker-no-cuda-compose.sh down
EOF_NOTICE
}

stop_all() {
  prepare
  compose down
}

restart_all() {
  stop_all
  start_all
}

status_all() {
  prepare
  compose ps
}

logs_all() {
  prepare
  compose logs -f postgresql minio minio-init ai-service
}

config_all() {
  prepare
  compose config
}

case "${ACTION}" in
  up)
    start_all
    ;;
  down)
    stop_all
    ;;
  restart)
    restart_all
    ;;
  status|ps)
    status_all
    ;;
  logs)
    logs_all
    ;;
  config)
    config_all
    ;;
  *)
    cat >&2 <<'EOF_USAGE'
用法：
  bash scripts/dev/start-docker-no-cuda-compose.sh [up|down|restart|status|logs|config]
EOF_USAGE
    exit 2
    ;;
esac
