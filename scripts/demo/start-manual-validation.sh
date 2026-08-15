#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

BUILD=false
SEED=false
for arg in "$@"; do
  case "${arg}" in
    --build) BUILD=true ;;
    --seed) SEED=true ;;
    *) fail "未知参数：${arg}（支持 --build --seed）" ;;
  esac
done

cd "${PROJECT_ROOT}"

bash "${SCRIPT_DIR}/preflight-manual-validation.sh"

if command -v fc-list >/dev/null 2>&1; then
  if ! fc-list :lang=zh family 2>/dev/null | grep -q '[^[:space:]]'; then
    echo "[WARN] 当前主机未检测到中文字体：风险 PDF 将拒绝生成乱码文件。"
    echo "[WARN] Ubuntu/WSL 可执行：sudo apt update && sudo apt install -y fonts-noto-cjk"
  else
    pass "检测到中文字体，风险 PDF 中文渲染可用"
  fi
else
  info "未安装 fontconfig，无法预检中文字体；风险报告生成时仍会由 Java 进行字体能力检测"
fi

AI_PORT="$(env_value URBAN_SAFE_AI_SERVICE_PORT || echo 8001)"
SERVER_PORT="$(env_value URBAN_SAFE_SERVER_PORT || echo 8888)"
FRONT_PORT=5173
AI_PYTHON="$(resolve_ai_python)"

echo
info "比赛手动验证拓扑：Docker(PostgreSQL+MinIO) + Host FastAPI REAL + Spring Boot + Vue"

# 当前 docker/ai-service 仍是 ONNX/MOCK-oriented 镜像，比赛验证明确不启动它，避免抢占 8001。
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" stop ai-service >/dev/null 2>&1 || true

info "启动 PostgreSQL 与 MinIO"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" up -d --build postgresql minio

info "等待 PostgreSQL 可用"
for _ in $(seq 1 60); do
  if docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" \
      exec -T postgresql sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null 2>&1; then
    break
  fi
  sleep 2
done
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" \
  exec -T postgresql sh -c 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' >/dev/null
pass "PostgreSQL READY"

info "幂等初始化 MinIO bucket / 应用账号 / 策略"
docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" run --rm minio-init
pass "MinIO 初始化完成"

if port_open 127.0.0.1 "${AI_PORT}" && ! process_alive fastapi; then
  fail "端口 ${AI_PORT} 已被非本脚本进程占用。请确认没有 docker ai-service 或其他 FastAPI。"
fi

if ! process_alive fastapi; then
  start_group_process fastapi "${PROJECT_ROOT}/ai-service-python" \
    env \
      URBAN_SAFE_AI_MODEL_ROOT="${PROJECT_ROOT}/data/model-cache" \
      URBAN_SAFE_AI_MODEL_CATALOG_PATH=runtime-catalog.json \
      URBAN_SAFE_AI_VISUAL_DEVICE=cuda \
      URBAN_SAFE_AI_VISION_DTYPE=float16 \
      URBAN_SAFE_AI_VISION_OFFLINE=true \
      URBAN_SAFE_AI_VISION_HF_HOME="${PROJECT_ROOT}/data/model-cache/huggingface" \
      URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY=1 \
      URBAN_SAFE_AI_VISION_SHA_MODE=STRICT \
      HF_HOME="${PROJECT_ROOT}/data/model-cache/huggingface" \
      HF_HUB_OFFLINE=1 \
      "${AI_PYTHON}" -m uvicorn app.main:app --host 0.0.0.0 --port "${AI_PORT}" --workers 1
fi

info "等待 FastAPI REAL READY"
wait_http "http://127.0.0.1:${AI_PORT}/internal/api/v1/ai/ready" 90 2 \
  || fail "FastAPI 未在预期时间内 READY，请查看 $(log_file fastapi)"

curl -fsS "http://127.0.0.1:${AI_PORT}/internal/api/v1/ai/models/AI-VISION-LOCAL-001" \
  | "${AI_PYTHON}" -c '
import json, sys
m=json.load(sys.stdin)
assert m.get("modelId")=="AI-VISION-LOCAL-001", m
assert m.get("mode")=="REAL", m
assert m.get("status")=="APPROVED", m
assert m.get("ready") is True, m
assert m.get("executionProvider") in {"PyTorch-CUDA","CUDAExecutionProvider"}, m
print("AI-VISION-LOCAL-001 REAL / APPROVED / READY /", m.get("executionProvider"))
'
pass "FastAPI REAL READY"

JAR="${PROJECT_ROOT}/backend-java/starter/target/Service.jar"
BACKEND_SOURCES_STALE=false
if [[ -f "${JAR}" ]]; then
  if find "${PROJECT_ROOT}/backend-java" \
      -path '*/target' -prune -o \
      -type f \( -name '*.java' -o -name '*.xml' -o -name '*.yaml' -o -name '*.yml' -o -name '*.properties' -o -name '*.sql' \) \
      -newer "${JAR}" -print -quit | grep -q .; then
    BACKEND_SOURCES_STALE=true
    info "检测到后端源码/配置比 Service.jar 新，将自动重建"
  fi
fi

if [[ "${BUILD}" == "true" || ! -f "${JAR}" || "${BACKEND_SOURCES_STALE}" == "true" ]]; then
  info "构建 Spring Boot JAR"
  MVN_ARGS=(-B -ntp -f "${PROJECT_ROOT}/backend-java/pom.xml" clean package -DskipTests)
  if [[ -f "${PROJECT_ROOT}/tools/maven/settings-cn.xml" ]]; then
    MVN_ARGS=(-B -ntp -s "${PROJECT_ROOT}/tools/maven/settings-cn.xml" -f "${PROJECT_ROOT}/backend-java/pom.xml" clean package -DskipTests)
  fi
  mvn "${MVN_ARGS[@]}"
fi
[[ -f "${JAR}" ]] || fail "Spring Boot JAR 不存在：${JAR}"

# Spring Boot 的 @ConfigurationProperties 在 JVM 启动时绑定。.env 或 JAR 更新后若复用旧进程，
# 页面会继续显示旧的 Provider enabled/configured 状态，因此比赛启动统一刷新后端进程。
if process_alive backend; then
  info "重启 Spring Boot，确保当前根 .env 与最新 Service.jar 已生效"
  stop_group_process backend
elif port_open 127.0.0.1 "${SERVER_PORT}"; then
  fail "端口 ${SERVER_PORT} 已被非本脚本进程占用"
fi
start_group_process backend "${PROJECT_ROOT}" java -jar "${JAR}"

info "等待 Spring Boot READY"
wait_http "http://127.0.0.1:${SERVER_PORT}/actuator/health" 90 2 \
  || fail "Spring Boot 未在预期时间内 READY，请查看 $(log_file backend)"
pass "Spring Boot READY"

if [[ "${SEED}" == "true" ]]; then
  info "写入/刷新幂等比赛演示数据"
  bash "${PROJECT_ROOT}/scripts/dev/seed-demo-data.sh"
fi

FRONT_ENV="${PROJECT_ROOT}/frontend/.env.local"
if [[ ! -f "${FRONT_ENV}" ]]; then
  cp "${PROJECT_ROOT}/frontend/.env.example" "${FRONT_ENV}"
  sed -i 's/^VITE_API_MODE=.*/VITE_API_MODE=real/' "${FRONT_ENV}"
  sed -i "s#^VITE_API_BASE_URL=.*#VITE_API_BASE_URL=http://localhost:${SERVER_PORT}#" "${FRONT_ENV}"
  info "已创建 frontend/.env.local（real 模式，仅包含前端公开配置）"
fi

if [[ ! -d "${PROJECT_ROOT}/frontend/node_modules" ]]; then
  info "安装前端依赖"
  (cd "${PROJECT_ROOT}/frontend" && npm ci)
fi

if [[ "${BUILD}" == "true" ]]; then
  info "刷新并校验前端 API 契约"
  (cd "${PROJECT_ROOT}/frontend" && npm run api:generate && npm run api:check)
fi

# Vite 的 VITE_* 变量在进程启动时读取。即使 .env.local 已切到 real，复用旧前端进程仍可能继续使用 mock。
if process_alive frontend; then
  info "重启 Vue，确保当前 frontend/.env.local 与最新前端代码已生效"
  stop_group_process frontend
elif port_open 127.0.0.1 "${FRONT_PORT}"; then
  fail "端口 ${FRONT_PORT} 已被非本脚本进程占用"
fi
start_group_process frontend "${PROJECT_ROOT}/frontend" npm run dev -- --host 0.0.0.0 --port "${FRONT_PORT}"

info "等待 Vue 前端"
wait_http "http://127.0.0.1:${FRONT_PORT}/" 60 1 \
  || fail "Vue 前端未在预期时间内可访问，请查看 $(log_file frontend)"
pass "Vue READY"

echo
bash "${SCRIPT_DIR}/status-manual-validation.sh"
echo
pass "手动验证环境已准备完成"
echo "浏览器入口：http://localhost:${FRONT_PORT}/console/login"
echo "建议按 docs/10_开发阶段/07_第七阶段/44_比赛机手动验证流程.md 逐项验收"
