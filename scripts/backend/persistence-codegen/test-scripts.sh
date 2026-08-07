#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
GENERATE_SCRIPT="${SCRIPT_DIR}/generate.sh"
DRIFT_SCRIPT="${SCRIPT_DIR}/check-drift.sh"

assert_contains() {
    local file_path="$1"
    local expected_text="$2"
    local failure_message="$3"
    if ! grep -Fq -- "${expected_text}" "${file_path}"; then
        echo "失败：${failure_message}" >&2
        exit 1
    fi
}

assert_not_contains() {
    local file_path="$1"
    local forbidden_text="$2"
    local failure_message="$3"
    if grep -Fq -- "${forbidden_text}" "${file_path}"; then
        echo "失败：${failure_message}" >&2
        exit 1
    fi
}

test -x "${GENERATE_SCRIPT}" || {
    echo "失败：generate.sh 必须保留可执行位" >&2
    exit 1
}
test -x "${DRIFT_SCRIPT}" || {
    echo "失败：check-drift.sh 必须保留可执行位" >&2
    exit 1
}

bash -n "${GENERATE_SCRIPT}"
bash -n "${DRIFT_SCRIPT}"

assert_contains "${GENERATE_SCRIPT}" 'APPLICATION_YAML="${BACKEND_DIR}/starter/src/main/resources/application.yaml"' \
    "生成脚本必须从 application.yaml 读取数据库配置"
assert_contains "${GENERATE_SCRIPT}" 'GENERATED_DIR="${BACKEND_DIR}/server/src/generated/java"' \
    "生成快照必须位于 server 模块"
assert_not_contains "${GENERATE_SCRIPT}" 'persistence/src/generated/java' \
    "不得重新引用已删除的 persistence 模块"
assert_contains "${GENERATE_SCRIPT}" 'TEMP_ROOT="$(mktemp -d "${GENERATED_PARENT}/.urban-safe-persistence-codegen.XXXXXX")"' \
    "临时生成目录必须与快照处于同一文件系统"
assert_contains "${GENERATE_SCRIPT}" 'REPLACEMENT_COMPLETED=false' \
    "生成脚本必须记录替换状态"
assert_contains "${GENERATE_SCRIPT}" 'mvn -f "${BACKEND_DIR}/pom.xml" -pl server -am -DskipTests compile' \
    "替换完成前必须编译 server 及依赖模块"

assert_contains "${DRIFT_SCRIPT}" "--health-cmd 'pg_isready -U \"\$POSTGRES_USER\" -d \"\$POSTGRES_DB\"'" \
    "漂移检查必须配置 PostgreSQL 健康检查"
assert_contains "${DRIFT_SCRIPT}" 'RUN_DIRECTORY_NAME="$(basename "${TEMP_ROOT}")"' \
    "漂移检查必须使用 mktemp 唯一目录派生资源名"
assert_contains "${DRIFT_SCRIPT}" 'CONTAINER_CREATED=false' \
    "漂移检查必须记录容器所有权"
assert_not_contains "${DRIFT_SCRIPT}" 'backend-java/persistence/' \
    "漂移检查不得重新引用已删除模块"

echo "持久层代码生成脚本静态验证通过"
