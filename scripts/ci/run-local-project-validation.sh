#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
MODE="${1:-all}"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
REPORT_DIR="${REPORT_DIR:-${ROOT_DIR}/downloads/local-validation/${TIMESTAMP}}"
LOG_FILE="${REPORT_DIR}/validation.log"
SUMMARY_FILE="${REPORT_DIR}/summary.txt"
SKIP_INSTALL="${SKIP_INSTALL:-0}"
SKIP_PLAYWRIGHT_INSTALL="${SKIP_PLAYWRIGHT_INSTALL:-0}"
PYTHON_BIN="${PYTHON_BIN:-python3}"

mkdir -p "${REPORT_DIR}"
exec > >(tee -a "${LOG_FILE}") 2>&1

STARTED_AT="$(date '+%Y-%m-%d %H:%M:%S %z')"

on_error() {
  local exit_code=$?
  {
    echo "status=FAILED"
    echo "mode=${MODE}"
    echo "started_at=${STARTED_AT}"
    echo "finished_at=$(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "exit_code=${exit_code}"
    echo "log=${LOG_FILE}"
  } > "${SUMMARY_FILE}"
  echo
  echo "[FAILED] 本地验证失败，详见：${LOG_FILE}"
  exit "${exit_code}"
}
trap on_error ERR

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "缺少命令：${cmd}" >&2
    exit 127
  fi
}

section() {
  echo
  echo "============================================================"
  echo "$1"
  echo "============================================================"
}

run_frontend() {
  section "[1/3] 前端契约、单元测试、构建与浏览器端到端测试"
  require_cmd node
  require_cmd npm
  require_cmd npx

  echo "node: $(node --version)"
  echo "npm : $(npm --version)"

  pushd "${ROOT_DIR}/frontend" >/dev/null
  if [[ "${SKIP_INSTALL}" != "1" ]]; then
    npm ci
  fi
  npm run api:generate
  npm run api:check
  npm run type-check
  npm run test
  npm run build
  npm run lint

  if [[ "${SKIP_PLAYWRIGHT_INSTALL}" != "1" ]]; then
    npx playwright install chromium
  fi
  npm run test:e2e
  popd >/dev/null
}

run_backend() {
  section "[2/3] Java 后端 Maven 全量测试"
  require_cmd java
  require_cmd mvn

  java -version
  mvn -version

  pushd "${ROOT_DIR}/backend-java" >/dev/null
  mvn -B -ntp test
  popd >/dev/null
}

run_ai() {
  section "[3/3] Python 人工智能服务、Phase 7 与无 CUDA 合同测试"
  require_cmd "${PYTHON_BIN}"

  local requirements_file="${ROOT_DIR}/ai-service-python/requirements-no-cuda.txt"
  if [[ ! -f "${requirements_file}" ]]; then
    requirements_file="${ROOT_DIR}/ai-service-python/requirements.txt"
  fi

  "${PYTHON_BIN}" --version
  if [[ "${SKIP_INSTALL}" != "1" ]]; then
    "${PYTHON_BIN}" -m pip install -r "${requirements_file}"
  fi

  pushd "${ROOT_DIR}/ai-service-python" >/dev/null
  "${PYTHON_BIN}" -m compileall -q app modeling training tools
  "${PYTHON_BIN}" -m tools.model_pipeline --help >/dev/null
  "${PYTHON_BIN}" -m pytest -q
  popd >/dev/null

  pushd "${ROOT_DIR}" >/dev/null
  "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_evaluate_phase7_validation.py
  "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_phase7_workflow_dsl.py

  if [[ -f "docker/docker-compose.no-cuda.yml" ]]; then
    "${PYTHON_BIN}" -m unittest scripts/dev/tests/test_no_cuda_compose_contract.py
    bash -n scripts/dev/start-docker-no-cuda-compose.sh
    if command -v docker >/dev/null 2>&1; then
      docker compose --env-file .env.example -f docker/docker-compose.no-cuda.yml config >/dev/null
    else
      echo "[WARN] 未检测到 docker，跳过 docker compose config；其余无 CUDA 合同检查已执行。"
    fi
  fi
  popd >/dev/null
}

case "${MODE}" in
  all)
    run_frontend
    run_backend
    run_ai
    ;;
  frontend)
    run_frontend
    ;;
  backend)
    run_backend
    ;;
  ai|no-cuda)
    run_ai
    ;;
  *)
    cat >&2 <<'USAGE'
用法：
  bash scripts/ci/run-local-project-validation.sh [all|frontend|backend|ai|no-cuda]

环境变量：
  SKIP_INSTALL=1              跳过 npm ci / pip install
  SKIP_PLAYWRIGHT_INSTALL=1   跳过 Playwright Chromium 安装
  PYTHON_BIN=python3          指定 Python 命令
  REPORT_DIR=/path/to/report  指定本地报告目录
USAGE
    exit 2
    ;;
esac

trap - ERR
{
  echo "status=SUCCESS"
  echo "mode=${MODE}"
  echo "started_at=${STARTED_AT}"
  echo "finished_at=$(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "log=${LOG_FILE}"
} > "${SUMMARY_FILE}"

echo
echo "[SUCCESS] 本地验证通过。"
echo "日志：${LOG_FILE}"
echo "摘要：${SUMMARY_FILE}"
