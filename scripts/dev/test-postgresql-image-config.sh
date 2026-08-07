#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
compose_file="${repository_root}/docker/docker-compose.yml"
env_example="${repository_root}/.env.example"
test_base="${repository_root}/backend-java/server/src/test/java/org/urbansafe/priority/support/PostgreSqlIntegrationTestBase.java"
legacy_dockerfile="${repository_root}/docker/postgresql/Dockerfile"
expected_image="imresamu/postgis:17-3.6.1-bundle0-bookworm"

fail() {
  echo "PostgreSQL 镜像配置检查失败：$1" >&2
  exit 1
}

grep -Fq "URBAN_SAFE_POSTGRES_IMAGE=${expected_image}" "${env_example}" \
  || fail ".env.example 未固定经过验证的组合镜像"

grep -Fq 'image: ${URBAN_SAFE_POSTGRES_IMAGE:?' "${compose_file}" \
  || fail "Docker Compose 未从唯一 .env 读取 PostgreSQL 镜像"

if grep -Eq 'dockerfile:[[:space:]]*postgresql/Dockerfile|PGVECTOR_VERSION|DEBIAN_MIRROR|POSTGRESQL_APT_MIRROR' "${compose_file}"; then
  fail "Docker Compose 仍包含 PostgreSQL 现场编译配置"
fi

if [[ -e "${legacy_dockerfile}" ]]; then
  fail "仍存在会通过 APT 和 GitHub 现场编译 pgvector 的旧 Dockerfile"
fi

grep -Fq "\"${expected_image}\"" "${test_base}" \
  || fail "Testcontainers 与本地运行环境没有使用同一镜像"

echo "PostgreSQL 镜像配置检查通过：${expected_image}"
