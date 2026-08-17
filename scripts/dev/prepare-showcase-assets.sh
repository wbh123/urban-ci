#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
env_file="${1:-${URBAN_SAFE_ENV_FILE:-${repository_root}/.env}}"
compose_file="${repository_root}/docker/docker-compose.yml"
asset_dir="${SHOWCASE_ASSET_DIR:-${repository_root}/data/showcase-assets/inspection}"
variants="${SHOWCASE_ASSET_VARIANTS_PER_ISSUE:-4}"

if [[ ! -f "${env_file}" ]]; then
  echo "缺少环境文件：${env_file}" >&2
  exit 1
fi
if [[ ! -f "${compose_file}" ]]; then
  echo "缺少 Docker Compose 文件：${compose_file}" >&2
  exit 1
fi

compose=(docker compose --env-file "${env_file}" -f "${compose_file}")
if ! "${compose[@]}" ps --status running --services | grep -qx 'minio'; then
  echo "MinIO 服务未运行，无法准备比赛巡检图片。" >&2
  exit 1
fi

mkdir -p "${asset_dir}"
python3 "${repository_root}/scripts/dev/prepare-showcase-assets.py" "${asset_dir}" "${variants}"

# 使用项目已有的 minio-init 镜像中的 mc 客户端。图片目录只读挂载，避免容器修改宿主机文件。
# mc cp 对同名目标执行复制/更新，不使用仅属于 mc mirror 的 --overwrite 参数。
"${compose[@]}" run --rm --no-deps -T \
  -v "${asset_dir}:/showcase-assets:ro" \
  --entrypoint /bin/sh minio-init -c '
set -eu
alias_name="${MINIO_ALIAS:-local}"
mc alias set "$alias_name" "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing "$alias_name/$MINIO_ASSETS_BUCKET" >/dev/null
for file in /showcase-assets/*.png; do
  [ -f "$file" ] || continue
  mc cp "$file" "$alias_name/$MINIO_ASSETS_BUCKET/showcase/inspection/$(basename "$file")" >/dev/null
done
'

echo "演示巡检图片已准备：${asset_dir}"
echo "Manifest：${asset_dir}/manifest.json"
