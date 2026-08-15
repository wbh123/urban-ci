#!/usr/bin/env bash
set -euo pipefail

DEMO_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${DEMO_SCRIPT_DIR}/../.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
COMPOSE_FILE="${PROJECT_ROOT}/docker/docker-compose.yml"
RUN_DIR="${PROJECT_ROOT}/tmp/manual-validation"
LOG_DIR="${RUN_DIR}/logs"
PID_DIR="${RUN_DIR}/pids"

mkdir -p "${LOG_DIR}" "${PID_DIR}"

fail() {
  echo "[FAIL] $*" >&2
  exit 1
}

info() {
  echo "[INFO] $*"
}

pass() {
  echo "[PASS] $*"
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "缺少命令：$1"
}

env_value() {
  local key="$1"
  [[ -f "${ENV_FILE}" ]] || return 1
  local line
  line="$(grep -E "^${key}=" "${ENV_FILE}" | tail -n 1 || true)"
  [[ -n "${line}" ]] || return 1
  local value="${line#*=}"
  if [[ ${#value} -ge 2 ]]; then
    local first="${value:0:1}"
    local last="${value: -1}"
    if [[ ("${first}" == '"' && "${last}" == '"') || ("${first}" == "'" && "${last}" == "'") ]]; then
      value="${value:1:${#value}-2}"
    fi
  fi
  printf '%s' "${value}"
}

expect_env() {
  local key="$1"
  local expected="$2"
  local actual
  actual="$(env_value "${key}" || true)"
  if [[ "${actual}" != "${expected}" ]]; then
    echo "[FAIL] ${key} 应为 '${expected}'，当前为 '${actual:-<未配置>}'" >&2
    return 1
  fi
  pass "${key}=${expected}"
}

resolve_ai_python() {
  if [[ -n "${URBAN_SAFE_PYTHON_ENV:-}" && -x "${URBAN_SAFE_PYTHON_ENV}/bin/python" ]]; then
    echo "${URBAN_SAFE_PYTHON_ENV}/bin/python"
  elif [[ -x "${PROJECT_ROOT}/ai-service-python/.venv-demo-rtx3060/bin/python" ]]; then
    echo "${PROJECT_ROOT}/ai-service-python/.venv-demo-rtx3060/bin/python"
  elif [[ -x "${PROJECT_ROOT}/ai-service-python/.venv/bin/python" ]]; then
    echo "${PROJECT_ROOT}/ai-service-python/.venv/bin/python"
  else
    echo "python3"
  fi
}

port_open() {
  local host="$1"
  local port="$2"
  python3 - "${host}" "${port}" <<'PY'
import socket, sys
host, port = sys.argv[1], int(sys.argv[2])
s = socket.socket()
s.settimeout(0.3)
try:
    s.connect((host, port))
except OSError:
    raise SystemExit(1)
finally:
    s.close()
PY
}

wait_http() {
  local url="$1"
  local attempts="${2:-60}"
  local sleep_seconds="${3:-2}"
  local i
  for i in $(seq 1 "${attempts}"); do
    if curl -fsS "${url}" >/dev/null 2>&1; then
      return 0
    fi
    sleep "${sleep_seconds}"
  done
  return 1
}

pid_file() {
  echo "${PID_DIR}/$1.pid"
}

log_file() {
  echo "${LOG_DIR}/$1.log"
}

process_alive() {
  local name="$1"
  local file
  file="$(pid_file "${name}")"
  [[ -f "${file}" ]] || return 1
  local pid
  pid="$(cat "${file}")"
  [[ "${pid}" =~ ^[0-9]+$ ]] || return 1
  kill -0 "${pid}" 2>/dev/null
}

start_group_process() {
  local name="$1"
  local workdir="$2"
  shift 2
  local file log
  file="$(pid_file "${name}")"
  log="$(log_file "${name}")"

  if process_alive "${name}"; then
    info "${name} 已运行，PID=$(cat "${file}")"
    return 0
  fi

  rm -f "${file}"
  (
    cd "${workdir}"
    exec setsid "$@" >>"${log}" 2>&1
  ) &
  local pid=$!
  echo "${pid}" > "${file}"
  info "${name} 已启动，PID=${pid}，日志=${log}"
}

stop_group_process() {
  local name="$1"
  local file
  file="$(pid_file "${name}")"
  if [[ ! -f "${file}" ]]; then
    info "${name} 无 PID 文件，跳过"
    return 0
  fi
  local pid
  pid="$(cat "${file}")"
  if [[ "${pid}" =~ ^[0-9]+$ ]] && kill -0 "${pid}" 2>/dev/null; then
    kill -- "-${pid}" 2>/dev/null || kill "${pid}" 2>/dev/null || true
    for _ in $(seq 1 20); do
      kill -0 "${pid}" 2>/dev/null || break
      sleep 0.25
    done
    if kill -0 "${pid}" 2>/dev/null; then
      kill -9 -- "-${pid}" 2>/dev/null || kill -9 "${pid}" 2>/dev/null || true
    fi
    pass "${name} 已停止"
  fi
  rm -f "${file}"
}
