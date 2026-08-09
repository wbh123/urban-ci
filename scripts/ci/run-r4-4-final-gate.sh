#!/usr/bin/env bash
set -Eeuo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [[ -z "${ROOT_DIR}" ]]; then
  echo "错误：请在 urban-ci Git 仓库内执行本脚本。" >&2
  exit 2
fi

FEATURE_BRANCH="${R4_4_FEATURE_BRANCH:-feat/r4-4-role-dashboard-v2}"
BASE_BRANCH="${R4_4_BASE_BRANCH:-ci-base}"
REMOTE="${R4_4_REMOTE:-origin}"
MODE="${1:-help}"
PYTHON_BIN="${PYTHON_BIN:-python3}"
SKIP_INSTALL="${SKIP_INSTALL:-0}"
SKIP_PLAYWRIGHT_INSTALL="${SKIP_PLAYWRIGHT_INSTALL:-0}"
KEEP_WORKTREES="${KEEP_WORKTREES:-0}"
TIMESTAMP="$(date '+%Y%m%d-%H%M%S')"
REPORT_DIR="${REPORT_DIR:-${ROOT_DIR}/downloads/r4-4-final-gate/${TIMESTAMP}}"
LOG_FILE="${REPORT_DIR}/gate.log"
SUMMARY_FILE="${REPORT_DIR}/summary.txt"
WORKTREE_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/urban-r4-4-gate.XXXXXX")"
ACTIVE_WORKTREES=()
NEW_WORKTREE=""

mkdir -p "${REPORT_DIR}"
exec > >(tee -a "${LOG_FILE}") 2>&1
STARTED_AT="$(date '+%Y-%m-%d %H:%M:%S %z')"

usage() {
  cat <<'USAGE'
R4-4 最终验收脚本

用法：
  bash scripts/ci/run-r4-4-final-gate.sh playwright
  bash scripts/ci/run-r4-4-final-gate.sh premerge
  bash scripts/ci/run-r4-4-final-gate.sh postmerge

模式：
  playwright  只在远端 feat/r4-4-role-dashboard-v2 精确复测 dashboard-roles.spec.ts
  premerge    先跑目标 Playwright，再在临时工作树模拟 feature -> ci-base 合并并执行全量门禁
  postmerge   验证 feature 补丁已包含于远端 ci-base，再对远端 ci-base 执行全量门禁

可选环境变量：
  SKIP_INSTALL=1             跳过 npm ci 与 Python pip 安装（仅已准备好依赖时使用）
  SKIP_PLAYWRIGHT_INSTALL=1  跳过 Playwright Chromium/系统依赖安装
  PYTHON_BIN=python3         指定 Python 可执行文件
  REPORT_DIR=/path/to/dir    指定报告输出目录
  KEEP_WORKTREES=1           失败后保留临时工作树便于排查
  R4_4_REMOTE=origin         指定 Git 远端
USAGE
}

write_summary() {
  local status="$1"
  local exit_code="$2"
  {
    echo "status=${status}"
    echo "mode=${MODE}"
    echo "feature=${REMOTE}/${FEATURE_BRANCH}"
    echo "base=${REMOTE}/${BASE_BRANCH}"
    echo "started_at=${STARTED_AT}"
    echo "finished_at=$(date '+%Y-%m-%d %H:%M:%S %z')"
    echo "exit_code=${exit_code}"
    echo "log=${LOG_FILE}"
  } > "${SUMMARY_FILE}"
}

cleanup() {
  local exit_code=$?
  if [[ "${KEEP_WORKTREES}" == "1" && "${exit_code}" -ne 0 ]]; then
    echo "[INFO] KEEP_WORKTREES=1，保留临时工作树：${WORKTREE_ROOT}"
  else
    for wt in "${ACTIVE_WORKTREES[@]:-}"; do
      [[ -n "${wt}" ]] || continue
      git -C "${ROOT_DIR}" worktree remove --force "${wt}" >/dev/null 2>&1 || true
    done
    rm -rf "${WORKTREE_ROOT}"
    git -C "${ROOT_DIR}" worktree prune >/dev/null 2>&1 || true
  fi

  if [[ "${exit_code}" -eq 0 ]]; then
    write_summary "SUCCESS" 0
  else
    write_summary "FAILED" "${exit_code}"
    echo
    echo "[FAILED] R4-4 验收失败，详见：${LOG_FILE}"
  fi
}
trap cleanup EXIT

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

fetch_refs() {
  section "刷新 R4-4 feature 与 ci-base 远端引用"
  require_cmd git
  git -C "${ROOT_DIR}" fetch --prune "${REMOTE}" \
    "+refs/heads/${FEATURE_BRANCH}:refs/remotes/${REMOTE}/${FEATURE_BRANCH}" \
    "+refs/heads/${BASE_BRANCH}:refs/remotes/${REMOTE}/${BASE_BRANCH}"
  echo "feature: $(git -C "${ROOT_DIR}" rev-parse "${REMOTE}/${FEATURE_BRANCH}")"
  echo "ci-base:  $(git -C "${ROOT_DIR}" rev-parse "${REMOTE}/${BASE_BRANCH}")"
}

new_detached_worktree() {
  local name="$1"
  local ref="$2"
  local wt="${WORKTREE_ROOT}/${name}"
  git -C "${ROOT_DIR}" worktree add --detach "${wt}" "${ref}" >/dev/null
  ACTIVE_WORKTREES+=("${wt}")
  NEW_WORKTREE="${wt}"
}

install_frontend_deps() {
  local wt="$1"
  pushd "${wt}/frontend" >/dev/null
  if [[ "${SKIP_INSTALL}" != "1" ]]; then
    npm ci
  fi
  if [[ "${SKIP_PLAYWRIGHT_INSTALL}" != "1" ]]; then
    npx playwright install --with-deps chromium
  fi
  popd >/dev/null
}

run_target_playwright_in_worktree() {
  local wt="$1"
  section "R4-4 最后一项 Playwright：dashboard-roles.spec.ts"
  require_cmd node
  require_cmd npm
  require_cmd npx
  install_frontend_deps "${wt}"
  pushd "${wt}/frontend" >/dev/null
  npx playwright test e2e/dashboard-roles.spec.ts
  popd >/dev/null
}

run_public_repository_guard() {
  local wt="$1"
  section "Public Repository Guard：禁止私有文档、运行数据、模型权重和常见密钥"
  local bad=0
  pushd "${wt}" >/dev/null
  while IFS= read -r file; do
    case "${file}" in
      README.md|*/README.md|AGENTS.md|*/AGENTS.md|records/*)
        echo "forbidden tracked project document: ${file}"; bad=1 ;;
      *.md|*.mdx|*.doc|*.docx|*.pdf|*.ppt|*.pptx)
        echo "forbidden tracked document: ${file}"; bad=1 ;;
      .env|.env.local|.env.*.local|.env.backup-*|*/.env|*/.env.local|*/.env.*.local)
        echo "forbidden runtime environment file: ${file}"; bad=1 ;;
      downloads/*|data/*)
        echo "forbidden root runtime/data path: ${file}"; bad=1 ;;
      *.pt|*.pth|*.onnx|*.ckpt|*.safetensors|*.pkl|*.joblib)
        echo "forbidden model artifact: ${file}"; bad=1 ;;
      *.pem|*.key|*.p12|*.pfx|*.jks|*.keystore)
        echo "forbidden credential/certificate file: ${file}"; bad=1 ;;
    esac
  done < <(git ls-files)
  if [[ "${bad}" -ne 0 ]]; then
    popd >/dev/null
    return 1
  fi

  local patterns='-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|gh[pousr]_[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9_-]{20,}'
  if git grep -I -n -E "${patterns}" -- . ':!*.lock' ':!package-lock.json'; then
    echo "Potential secret material detected in tracked files."
    popd >/dev/null
    return 1
  fi
  echo "No blocked secret pattern detected."
  popd >/dev/null
}

run_frontend_full_gate() {
  local wt="$1"
  section "Project Validation / Frontend 全量门禁"
  require_cmd node
  require_cmd npm
  require_cmd npx
  pushd "${wt}/frontend" >/dev/null
  if [[ "${SKIP_INSTALL}" != "1" ]]; then npm ci; fi
  npm run api:generate
  npm run api:check
  npm run type-check
  npm run test
  npm run build
  npm run lint
  if [[ "${SKIP_PLAYWRIGHT_INSTALL}" != "1" ]]; then
    npx playwright install --with-deps chromium
  fi
  npm run test:e2e
  popd >/dev/null
}

run_backend_full_gate() {
  local wt="$1"
  section "Project Validation / Java 后端全量测试"
  require_cmd java
  require_cmd mvn
  pushd "${wt}/backend-java" >/dev/null
  java -version
  mvn -version
  mvn -B -ntp test
  popd >/dev/null
}

run_ai_full_gate() {
  local wt="$1"
  section "Project Validation / Python 人工智能与无 CUDA 合同门禁"
  require_cmd "${PYTHON_BIN}"
  pushd "${wt}" >/dev/null
  if [[ "${SKIP_INSTALL}" != "1" ]]; then
    "${PYTHON_BIN}" -m pip install --upgrade pip
    "${PYTHON_BIN}" -m pip install -r ai-service-python/requirements.txt
  fi

  pushd ai-service-python >/dev/null
  "${PYTHON_BIN}" -m compileall -q app modeling training tools
  "${PYTHON_BIN}" -m tools.model_pipeline --help >/dev/null
  "${PYTHON_BIN}" -m pytest -q
  popd >/dev/null

  "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_evaluate_phase7_validation.py
  "${PYTHON_BIN}" -m unittest scripts/ai/tests/test_phase7_workflow_dsl.py

  if [[ -f docker/docker-compose.no-cuda.yml ]]; then
    "${PYTHON_BIN}" -m unittest scripts/dev/tests/test_no_cuda_compose_contract.py
    bash -n scripts/dev/start-docker-no-cuda-compose.sh
    require_cmd docker
    docker compose --env-file .env.example -f docker/docker-compose.no-cuda.yml config >/dev/null
  fi
  popd >/dev/null
}

run_full_gate() {
  local wt="$1"
  run_public_repository_guard "${wt}"
  run_frontend_full_gate "${wt}"
  run_backend_full_gate "${wt}"
  run_ai_full_gate "${wt}"
}

verify_patch_already_in_base() {
  local wt="$1"
  section "确认 R4-4 补丁已实际包含于 ci-base"
  pushd "${wt}" >/dev/null
  git merge --no-commit --no-ff "${REMOTE}/${FEATURE_BRANCH}" >/dev/null 2>&1 || {
    echo "错误：将 R4-4 feature 再合并到 ci-base 会产生冲突，无法证明补丁已完整落入 ci-base。" >&2
    git merge --abort >/dev/null 2>&1 || true
    popd >/dev/null
    return 1
  }

  if ! git diff --quiet HEAD -- || ! git diff --cached --quiet HEAD --; then
    echo "错误：R4-4 feature 再合并到 ci-base 仍会产生文件变化。" >&2
    echo "说明 ci-base 尚未包含与 feature 等价的完整补丁，请勿进行 postmerge 验收。" >&2
    git status --short
    git merge --abort >/dev/null 2>&1 || git reset --hard HEAD >/dev/null 2>&1 || true
    popd >/dev/null
    return 1
  fi

  git merge --abort >/dev/null 2>&1 || git reset --hard HEAD >/dev/null 2>&1 || true
  popd >/dev/null
  echo "确认：再次合并 feature 不产生内容变化，ci-base 已包含 R4-4 补丁。"
}

run_playwright_mode() {
  fetch_refs
  local wt
  new_detached_worktree feature-playwright "${REMOTE}/${FEATURE_BRANCH}"
  wt="${NEW_WORKTREE}"
  run_target_playwright_in_worktree "${wt}"
}

run_premerge_mode() {
  fetch_refs

  local feature_wt
  new_detached_worktree feature-playwright "${REMOTE}/${FEATURE_BRANCH}"
  feature_wt="${NEW_WORKTREE}"
  run_target_playwright_in_worktree "${feature_wt}"

  local merge_wt
  new_detached_worktree premerge "${REMOTE}/${BASE_BRANCH}"
  merge_wt="${NEW_WORKTREE}"
  section "模拟合并：${FEATURE_BRANCH} -> ${BASE_BRANCH}（仅临时工作树，不推送）"
  pushd "${merge_wt}" >/dev/null
  git merge --no-commit --no-ff "${REMOTE}/${FEATURE_BRANCH}"
  popd >/dev/null
  run_full_gate "${merge_wt}"
}

run_postmerge_mode() {
  fetch_refs
  local base_wt
  new_detached_worktree postmerge "${REMOTE}/${BASE_BRANCH}"
  base_wt="${NEW_WORKTREE}"
  verify_patch_already_in_base "${base_wt}"
  run_full_gate "${base_wt}"
}

case "${MODE}" in
  playwright) run_playwright_mode ;;
  premerge) run_premerge_mode ;;
  postmerge) run_postmerge_mode ;;
  help|-h|--help) usage ;;
  *)
    echo "错误：未知模式 ${MODE}" >&2
    usage >&2
    exit 2
    ;;
esac

echo
echo "[SUCCESS] R4-4 ${MODE} 验收通过。"
echo "日志：${LOG_FILE}"
echo "摘要：${SUMMARY_FILE}"
