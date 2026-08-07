#!/bin/sh
set -eu

MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ALIAS="${MINIO_ALIAS:-local}"
MINIO_WAIT_INTERVAL="${MINIO_WAIT_INTERVAL:-2}"
MINIO_WAIT_RETRIES="${MINIO_WAIT_RETRIES:-60}"

: "${MINIO_ROOT_USER:?必须设置 MINIO_ROOT_USER}"
: "${MINIO_ROOT_PASSWORD:?必须设置 MINIO_ROOT_PASSWORD}"
: "${MINIO_APP_USER:?必须设置 MINIO_APP_USER}"
: "${MINIO_APP_PASSWORD:?必须设置 MINIO_APP_PASSWORD}"
: "${MINIO_POLICY_NAME:?必须设置 MINIO_POLICY_NAME}"
: "${MINIO_ASSETS_BUCKET:?必须设置 MINIO_ASSETS_BUCKET}"
: "${MINIO_REPORTS_BUCKET:?必须设置 MINIO_REPORTS_BUCKET}"
: "${MINIO_MODELS_BUCKET:?必须设置 MINIO_MODELS_BUCKET}"

if [ "${MINIO_ROOT_USER}" = "${MINIO_APP_USER}" ]; then
    echo "MinIO 应用账号不得与根账号相同" >&2
    exit 1
fi

attempt=1
until mc alias set \
    "${MINIO_ALIAS}" \
    "${MINIO_ENDPOINT}" \
    "${MINIO_ROOT_USER}" \
    "${MINIO_ROOT_PASSWORD}" >/dev/null 2>&1; do
    if [ "${attempt}" -ge "${MINIO_WAIT_RETRIES}" ]; then
        echo "等待 MinIO 超时：${MINIO_ENDPOINT}" >&2
        exit 1
    fi

    echo "等待 MinIO 启动（${attempt}/${MINIO_WAIT_RETRIES}）..."
    attempt=$((attempt + 1))
    sleep "${MINIO_WAIT_INTERVAL}"
done

for bucket in \
    "${MINIO_ASSETS_BUCKET}" \
    "${MINIO_REPORTS_BUCKET}" \
    "${MINIO_MODELS_BUCKET}"; do
    mc mb --ignore-existing "${MINIO_ALIAS}/${bucket}"
    mc anonymous set none "${MINIO_ALIAS}/${bucket}"
done

policy_file="/tmp/${MINIO_POLICY_NAME}.json"
cat >"${policy_file}" <<EOF
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": ["s3:GetBucketLocation", "s3:ListBucket", "s3:ListBucketMultipartUploads"],
      "Resource": [
        "arn:aws:s3:::${MINIO_ASSETS_BUCKET}",
        "arn:aws:s3:::${MINIO_REPORTS_BUCKET}",
        "arn:aws:s3:::${MINIO_MODELS_BUCKET}"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "s3:GetObject",
        "s3:PutObject",
        "s3:DeleteObject",
        "s3:AbortMultipartUpload",
        "s3:ListMultipartUploadParts"
      ],
      "Resource": [
        "arn:aws:s3:::${MINIO_ASSETS_BUCKET}/*",
        "arn:aws:s3:::${MINIO_REPORTS_BUCKET}/*",
        "arn:aws:s3:::${MINIO_MODELS_BUCKET}/*"
      ]
    }
  ]
}
EOF

# 同名策略会被覆盖，从而确保每次初始化都与 .env 中的存储桶配置一致。
mc admin policy create "${MINIO_ALIAS}" "${MINIO_POLICY_NAME}" "${policy_file}"

# 开发环境初始化时重建应用账号，确保修改 .env 密码后不会继续使用旧凭据。
if mc admin user info "${MINIO_ALIAS}" "${MINIO_APP_USER}" >/dev/null 2>&1; then
    mc admin policy detach "${MINIO_ALIAS}" "${MINIO_POLICY_NAME}" \
        --user "${MINIO_APP_USER}" >/dev/null 2>&1 || true
    mc admin user rm "${MINIO_ALIAS}" "${MINIO_APP_USER}"
fi

mc admin user add \
    "${MINIO_ALIAS}" \
    "${MINIO_APP_USER}" \
    "${MINIO_APP_PASSWORD}"
mc admin policy attach \
    "${MINIO_ALIAS}" \
    "${MINIO_POLICY_NAME}" \
    --user "${MINIO_APP_USER}"

mc admin user info "${MINIO_ALIAS}" "${MINIO_APP_USER}"

echo "MinIO 初始化完成："
echo "- 私有存储桶：${MINIO_ASSETS_BUCKET} ${MINIO_REPORTS_BUCKET} ${MINIO_MODELS_BUCKET}"
echo "- 应用账号：${MINIO_APP_USER}"
echo "- 受限策略：${MINIO_POLICY_NAME}"
