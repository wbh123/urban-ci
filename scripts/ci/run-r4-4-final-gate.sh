#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：请在 urban-safe-priority Git 仓库内执行本脚本。" >&2
  exit 2
fi

MODE="${1:-full}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
SKIP_INSTALL="${SKIP_INSTALL:-0}"
SKIP_PLAYWRIGHT_INSTALL="${SKIP_PLAYWRIGHT_INSTALL:-0}"
ALLOW_NON_MAIN="${ALLOW_NON_MAIN:-0}"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
REPORT_DIR="${REPORT_DIR:-${ROOT_DIR}/downloads/r4-4-main-validation/${TIMESTAMP}}"
LOG_FILE="${REPORT_DIR}/validation.log"
SUMMARY_FILE="${REPORT_DIR}/summary.txt"
STARTED_AT="$(date '+%Y-%m-%d %H:%M:%S %z')"

mkdir -p "${REPORT_DIR}"
exec > >(tee -a "${LOG_FILE}") 2>&1

write_summary() {
  local status="$1"
  local exit_code="$2"
  {
    echo "status=${status}"
    echo "mode=${MODE}"
    echo "branch=$(git -C "${ROOT_DIR}" branch --show-current)"
    echo "commit=$(git -C "${ROOT_DIR}" rev-parse HEAD)"
    echo "started_at=${STARTED_AT}"
    echo "finished_at=$(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "exit_code=${exit_code}"
    echo "log=${LOG_FILE}"
  } > "${SUMMARY_FILE}"
}

on_exit() {
  local exit_code=$?
  if [[ "${exit_code}" -eq 0 ]]; then
    write_summary SUCCESS 0
  else
    write_summary FAILED "${exit_code}"
    echo
    echo "[FAILED] R4-4 私有 main 验收失败，详见：${LOG_FILE}"
  fi
}
trap on_exit EXIT

section() {
  echo
  echo "================================================================"
  echo "$1"
  echo "================================================================"
}

require_cmd() {
  local cmd="$1"
  if ! command -v "${cmd}" >/dev/null 2>&1; then
    echo "错误：缺少命令 ${cmd}" >&2
    return 127
  fi
}

verify_main() {
  local branch
  branch="$(git -C "${ROOT_DIR}" branch --show-current)"
  if [[ "${branch}" != "main" && "${ALLOW_NON_MAIN}" != "1" ]]; then
    echo "错误：当前分支为 ${branch:-DETACHED}，本脚本默认只验收私有 main。" >&2
    echo "如确需在其他分支执行，请显式设置 ALLOW_NON_MAIN=1。" >&2
    exit 3
  fi
  echo "仓库：${ROOT_DIR}"
  echo "分支：${branch}"
  echo "提交：$(git -C "${ROOT_DIR}" rev-parse HEAD)"
}

install_frontend() {
  pushd "${ROOT_DIR}/frontend" >/dev/null
  if [[ "${SKIP_INSTALL}" != "1" ]]; then
    npm ci
  fi
  if [[ "${SKIP_PLAYWRIGHT_INSTALL}" != "1" ]]; then
    npx playwright install chromium
  fi
  popd >/dev/null
}

run_target_playwright() {
  section "R4-4 目标 Playwright：dashboard-roles.spec.ts"
  require_cmd node
  require_cmd npm
  require_cmd npx
  install_frontend
  pushd "${ROOT_DIR}/frontend" >/dev/null
  npx playwright test e2e/dashboard-roles.spec.ts
  popd >/dev/null
}

run_frontend_full() {
  section "前端全量门禁"
  require_cmd node
  require_cmd npm
  require_cmd npx
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

run_backend_full() {
  if [[ ! -d "${ROOT_DIR}/backend-java" ]]; then
    echo "[SKIP] 未找到 backend-java。"
    return 0
  fi
  section "Java 后端全量测试"
  require_cmd java
  require_cmd mvn
  pushd "${ROOT_DIR}/backend-java" >/dev/null
  java -version
  mvn -version
  mvn -B -ntp test
  popd >/dev/null
}

run_ai_full() {
  if [[ ! -d "${ROOT_DIR}/ai-service-python" ]]; then
    echo "[SKIP] 未找到 ai-service-python。"
    return 0
  fi
  section "Python 人工智能服务测试"
  require_cmd "${PYTHON_BIN}"
  pushd "${ROOT_DIR}" >/dev/null

  if [[ "${SKIP_INSTALL}" != "1" && -f ai-service-python/requirements.txt ]]; then
    "${PYTHON_BIN}" -m pip install -r ai-service-python/requirements.txt
  fi

  pushd ai-service-python >/dev/null
  "${PYTHON_BIN}" -m compileall -q app modeling training tools
  "${PYTHON_BIN}" -m pytest -q
  popd >/dev/null

  if [[ -f scripts/ai/tests/test_evaluate_phase7_validation.py ]]; then
    "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_evaluate_phase7_validation.py
  fi
  if [[ -f scripts/ai/tests/test_phase7_workflow_dsl.py ]]; then
    "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_phase7_workflow_dsl.py
  fi
  if [[ -f scripts/dev/tests/test_no_cuda_compose_contract.py ]]; then
    "${PYTHON_BIN}" -m unittest scripts/dev/tests/test_no_cuda_compose_contract.py
  fi
  if [[ -f scripts/dev/start-docker-no-cuda-compose.sh ]]; then
    bash -n scripts/dev/start-docker-no-cuda-compose.sh
  fi
  if [[ -f docker/docker-compose.no-cuda.yml ]] && command -v docker >/dev/null 2>&1; then
    docker compose --env-file .env.example -f docker/docker-compose.no-cuda.yml config >/dev/null
  fi

  popd >/dev/null
}

verify_main

case "${MODE}" in
  playwright)
    run_target_playwright
    ;;
  frontend)
    run_frontend_full
    ;;
  full)
    run_frontend_full
    run_backend_full
    run_ai_full
    ;;
  *)
    echo "用法：bash scripts/ci/run-r4-4-final-gate.sh [playwright|frontend|full]" >&2
    exit 2
    ;;
esac

echo
echo "[SUCCESS] R4-4 私有 main ${MODE} 验收通过。"
echo "日志：${LOG_FILE}"
echo "摘要：${SUMMARY_FILE}"
