#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPOSITORY_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
CODEGEN_POM="${SCRIPT_DIR}/pom.xml"
GENERATED_DIR="${REPOSITORY_ROOT}/backend-java/server/src/generated/java"
MIGRATION_DIR="${REPOSITORY_ROOT}/backend-java/server/src/main/resources/db/migration"
TEMP_ROOT="$(mktemp -d "${TMPDIR:-/tmp}/urban-safe-persistence-drift.XXXXXX")"
TEMP_GENERATED="${TEMP_ROOT}/generated"
RUN_DIRECTORY_NAME="$(basename "${TEMP_ROOT}")"
RUN_SUFFIX="${RUN_DIRECTORY_NAME//-/_}"
CONTAINER_NAME="urban-safe-codegen-drift-${RUN_SUFFIX}"
DATABASE_NAME="urban_safe_codegen_${RUN_SUFFIX//-/_}"
DATABASE_USERNAME="urban_safe_codegen"
DATABASE_PASSWORD="urban_safe_codegen_${RUN_SUFFIX}"
POSTGRES_IMAGE="urban-safe-postgresql:test"
DATABASE_PORT=""
CONTAINER_CREATED=false

cleanup() {
    if [[ "${CONTAINER_CREATED}" == "true" ]]; then
        docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    fi
    rm -rf "${TEMP_ROOT}"
}
trap cleanup EXIT

docker info >/dev/null
if ! docker image inspect "${POSTGRES_IMAGE}" >/dev/null 2>&1; then
    docker build \
        --tag "${POSTGRES_IMAGE}" \
        --file "${REPOSITORY_ROOT}/docker/postgresql/Dockerfile" \
        "${REPOSITORY_ROOT}/docker"
fi

docker run --detach \
    --name "${CONTAINER_NAME}" \
    --rm \
    --publish 127.0.0.1::5432 \
    --health-cmd 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
    --health-interval=5s \
    --health-timeout=5s \
    --health-retries=20 \
    --health-start-period=10s \
    --env "POSTGRES_DB=${DATABASE_NAME}" \
    --env "POSTGRES_USER=${DATABASE_USERNAME}" \
    --env "POSTGRES_PASSWORD=${DATABASE_PASSWORD}" \
    "${POSTGRES_IMAGE}" >/dev/null
CONTAINER_CREATED=true

DATABASE_PORT="$(docker port "${CONTAINER_NAME}" 5432/tcp | sed -E 's/.*:([0-9]+)$/\1/')"
if [[ ! "${DATABASE_PORT}" =~ ^[0-9]+$ ]]; then
    echo "错误：无法读取临时 PostgreSQL 的映射端口" >&2
    exit 2
fi

for attempt in $(seq 1 60); do
    health_status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}starting{{end}}' "${CONTAINER_NAME}")"
    if [[ "${health_status}" == "healthy" ]]; then
        break
    fi
    if [[ "${attempt}" == "60" ]]; then
        docker logs "${CONTAINER_NAME}" >&2 || true
        echo "错误：临时 PostgreSQL 健康检查超时" >&2
        exit 3
    fi
    sleep 1
done

export URBAN_SAFE_CODEGEN_DB_URL="jdbc:postgresql://127.0.0.1:${DATABASE_PORT}/${DATABASE_NAME}"
export URBAN_SAFE_CODEGEN_DB_USERNAME="${DATABASE_USERNAME}"
export URBAN_SAFE_CODEGEN_DB_PASSWORD="${DATABASE_PASSWORD}"

mvn -f "${CODEGEN_POM}" \
    -Pentity-codegen \
    compile exec:java \
    -Dexec.args=--migrate \
    -Durban.safe.codegen.migrationDir="${MIGRATION_DIR}"

mvn -f "${CODEGEN_POM}" \
    -Pentity-codegen \
    compile exec:java \
    -Durban.safe.codegen.outputDir="${TEMP_GENERATED}" \
    -Durban.safe.codegen.migrationDir="${MIGRATION_DIR}"

if ! diff -ruN "${GENERATED_DIR}" "${TEMP_GENERATED}"; then
    echo "错误：持久层生成代码已漂移，请执行 scripts/backend/persistence-codegen/generate.sh" >&2
    exit 5
fi

echo "持久层生成代码与独立临时数据库迁移结果一致"
