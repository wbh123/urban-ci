# MinIO 对象存储服务与初始化器共用一个多阶段 Dockerfile。
# 基础镜像可以在 Docker Compose 中覆盖，便于使用内网镜像仓库或离线镜像。
ARG MINIO_SERVER_IMAGE=minio/minio:latest
ARG MINIO_CLIENT_IMAGE=minio/mc:latest

FROM ${MINIO_SERVER_IMAGE} AS server

LABEL org.opencontainers.image.title="UrbanSafe MinIO Object Storage" \
      org.opencontainers.image.description="UrbanSafe Priority object storage service based on MinIO"

EXPOSE 9000 9001
VOLUME ["/data"]

FROM ${MINIO_CLIENT_IMAGE} AS initializer

COPY --chmod=0755 minio/init-buckets.sh /usr/local/bin/init-buckets.sh

ENTRYPOINT ["/bin/sh", "/usr/local/bin/init-buckets.sh"]
