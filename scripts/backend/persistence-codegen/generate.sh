#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
BACKEND_DIR="${REPOSITORY_ROOT}/backend-java"
APPLICATION_YAML="${BACKEND_DIR}/starter/src/main/resources/application.yaml"
CODEGEN_POM="${SCRIPT_DIR}/pom.xml"
MIGRATION_DIR="${BACKEND_DIR}/server/src/main/resources/db/migration"
GENERATED_DIR="${BACKEND_DIR}/server/src/generated/java"
GENERATED_PARENT="$(dirname "${GENERATED_DIR}")"

mkdir -p "${GENERATED_PARENT}"
TEMP_ROOT="$(mktemp -d "${GENERATED_PARENT}/.urban-safe-persistence-codegen.XXXXXX")"
TEMP_GENERATED="${TEMP_ROOT}/generated"
BACKUP_DIR="${TEMP_ROOT}/previous-generated"
ORIGINAL_SNAPSHOT_MOVED=false
REPLACEMENT_COMPLETED=false

cleanup() {
    local cleanup_status=0
    if [[ "${ORIGINAL_SNAPSHOT_MOVED}" == "true" \
            && "${REPLACEMENT_COMPLETED}" != "true" \
            && -d "${BACKUP_DIR}" ]]; then
        rm -rf "${GENERATED_DIR}"
        if ! mv "${BACKUP_DIR}" "${GENERATED_DIR}"; then
            echo "严重错误：无法恢复持久层生成快照 ${GENERATED_DIR}" >&2
            cleanup_status=1
        fi
    fi
    rm -rf "${TEMP_ROOT}"
    return "${cleanup_status}"
}
trap cleanup EXIT

command -v python3 >/dev/null 2>&1 || {
    echo "错误：缺少 Python 3，无法读取 application.yaml" >&2
    exit 2
}

mapfile -t DATABASE_CONFIG < <(python3 - "${APPLICATION_YAML}" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
values = {}
stack = []
for raw in path.read_text(encoding="utf-8").splitlines():
    stripped = raw.strip()
    if not stripped or stripped.startswith("#") or stripped.startswith("- ") or ":" not in stripped:
        continue
    indent = len(raw) - len(raw.lstrip(" "))
    key, value = stripped.split(":", 1)
    while stack and indent <= stack[-1][0]:
        stack.pop()
    if not value.strip():
        stack.append((indent, key.strip()))
        continue
    full_key = ".".join([entry[1] for entry in stack] + [key.strip()])
    scalar = value.strip().strip('"').strip("'")
    values[full_key] = scalar

for key in ("spring.datasource.url", "spring.datasource.username", "spring.datasource.password"):
    value = values.get(key, "")
    if not value:
        raise SystemExit(f"application.yaml 缺少必要配置：{key}")
    print(value)
PY
)

if [[ "${#DATABASE_CONFIG[@]}" -ne 3 ]]; then
    echo "错误：无法从 application.yaml 读取完整数据库配置" >&2
    exit 2
fi

export URBAN_SAFE_CODEGEN_DB_URL="${DATABASE_CONFIG[0]}"
export URBAN_SAFE_CODEGEN_DB_USERNAME="${DATABASE_CONFIG[1]}"
export URBAN_SAFE_CODEGEN_DB_PASSWORD="${DATABASE_CONFIG[2]}"

table_count="$(
    mvn -q -f "${CODEGEN_POM}" -Pentity-codegen compile exec:java \
        -Dexec.args=--table-count
)"
if [[ ! "${table_count}" =~ ^[1-9][0-9]*$ ]]; then
    echo "错误：生成器 tableCount 输出不是正整数：${table_count}" >&2
    exit 3
fi

mvn -f "${CODEGEN_POM}" \
    -Pentity-codegen \
    compile exec:java \
    -Durban.safe.codegen.outputDir="${TEMP_GENERATED}" \
    -Durban.safe.codegen.migrationDir="${MIGRATION_DIR}"

entity_count="$(find "${TEMP_GENERATED}" -path '*/entity/*.java' -type f | wc -l | tr -d ' ')"
mapper_count="$(find "${TEMP_GENERATED}" -path '*/mapper/*.java' -type f | wc -l | tr -d ' ')"
if [[ "${entity_count}" -ne "${table_count}" || "${mapper_count}" -ne "${table_count}" ]]; then
    echo "错误：生成结果数量异常，期望=${table_count}，Entity=${entity_count}，Mapper=${mapper_count}" >&2
    exit 4
fi

generated_file_count="$(find "${TEMP_GENERATED}" -name '*.java' -type f | wc -l | tr -d ' ')"
expected_file_count="$((table_count * 2))"
if [[ "${generated_file_count}" -ne "${expected_file_count}" ]] \
        || find "${TEMP_GENERATED}" -name '*.java' -type f -exec grep -L '@Generated' {} + | grep -q .; then
    echo "错误：生成标志或生成文件数量校验失败，文件=${generated_file_count}，期望=${expected_file_count}" >&2
    exit 5
fi

echo "以下是本次生成相对于仓库快照的差异："
diff -ruN "${GENERATED_DIR}" "${TEMP_GENERATED}" || true

if [[ -d "${GENERATED_DIR}" ]]; then
    mv "${GENERATED_DIR}" "${BACKUP_DIR}"
    ORIGINAL_SNAPSHOT_MOVED=true
fi
mv "${TEMP_GENERATED}" "${GENERATED_DIR}"

mvn -f "${BACKEND_DIR}/pom.xml" -pl server -am -DskipTests compile

REPLACEMENT_COMPLETED=true
ORIGINAL_SNAPSHOT_MOVED=false
echo "持久层代码生成完成：${GENERATED_DIR}（表数量：${table_count}）"
