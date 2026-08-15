#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET="${SCRIPT_DIR}/start-manual-validation.sh"

python3 - "${TARGET}" <<'PY'
from pathlib import Path
import sys

text = Path(sys.argv[1]).read_text(encoding="utf-8")
backend_stop = "stop_group_process backend"
backend_start = "start_group_process backend"
frontend_stop = "stop_group_process frontend"
frontend_start = "start_group_process frontend"
stale_marker = "BACKEND_SOURCES_STALE"

if backend_stop not in text:
    raise SystemExit("RED: 已运行的 Spring Boot 不会在启动验收时重启，新的 .env/JAR 无法保证生效")
if backend_start not in text:
    raise SystemExit("缺少 Spring Boot 启动逻辑")
if text.index(backend_stop) > text.index(backend_start):
    raise SystemExit("Spring Boot 必须先停止旧进程，再使用当前 .env/JAR 启动")
if stale_marker not in text:
    raise SystemExit("RED: 后端源码比 Service.jar 新时不会自动重建，可能继续运行旧代码")
if frontend_stop not in text:
    raise SystemExit("RED: 已运行的 Vue 不会重启，新的 frontend/.env.local（尤其 VITE_API_MODE）不会生效")
if frontend_start not in text:
    raise SystemExit("缺少 Vue 启动逻辑")
if text.index(frontend_stop) > text.index(frontend_start):
    raise SystemExit("Vue 必须先停止旧进程，再使用当前 frontend/.env.local 启动")

print("PASS: 手动验证启动会刷新 Spring Boot、Vue 运行态，并识别过期后端 JAR")
PY
