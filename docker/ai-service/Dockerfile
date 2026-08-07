FROM python:3.11-slim

ARG PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple/

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_INDEX_URL=${PIP_INDEX_URL} \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    PIP_DEFAULT_TIMEOUT=120

WORKDIR /app

COPY ai-service-python/requirements.txt ./requirements.txt
COPY ai-service-python/requirements-real.txt ./requirements-real.txt
RUN python -m pip install --no-cache-dir --index-url "${PIP_INDEX_URL}" \
    -r requirements-real.txt

# onnxruntime-gpu 的 CUDA 13 和 cuDNN 9 用户态动态库由 Python wheel 安装到
# site-packages/nvidia 下；容器启动时必须提前加入动态链接路径，否则
# CUDAExecutionProvider 会因为找不到 libcublasLt.so.13 等库而装载失败。
ENV \
    LD_LIBRARY_PATH=/usr/local/lib/python3.11/site-packages/nvidia/cu13/lib:/usr/local/lib/python3.11/site-packages/nvidia/cudnn/lib

COPY ai-service-python/app ./app

RUN mkdir -p /app/output /app/models /root/.cache

EXPOSE 8001

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8001"]
