"""UrbanSafe CUDA-only 人工智能推理服务入口。"""

from __future__ import annotations

import json
from contextlib import asynccontextmanager
from datetime import datetime, timezone
from functools import lru_cache

from fastapi import FastAPI, File, Form, UploadFile
from fastapi.responses import JSONResponse

from .adapters.mock import MOCK_WARNINGS
from .config import get_settings
from .errors import InferenceServiceError
from .inference import InferenceOrchestrator
from .quality import analyze_image_quality
from .schemas import (
    InferenceErrorDetail,
    InferenceMetadata,
    InferenceMode,
    InferenceResponse,
    InferenceStatus,
    ImageQualityErrorResponse,
    ImageQualityResponse,
    ModelCatalogResponse,
    ModelInfo,
    RuntimeReadiness,
)


@asynccontextmanager
async def _lifespan(_: FastAPI):
    # 显式目录中的全部真实模型在服务接收请求前完成 CUDA 装载与热身。
    _orchestrator()
    yield


app = FastAPI(
    title="UrbanSafe AI Service",
    description="城安智序 CUDA-only 多模型推理与结果标准化服务",
    version="0.5.0",
    lifespan=_lifespan,
)


@lru_cache(maxsize=1)
def _orchestrator() -> InferenceOrchestrator:
    """创建进程级模型注册表；目录或权重变化后必须重启服务。"""

    return InferenceOrchestrator(get_settings())


@app.get("/api/ai/health", tags=["Health"])
async def health_check():
    """进程存活检查，不把模型就绪状态伪装为健康。"""

    return {
        "status": "healthy",
        "service": get_settings().service_name,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }


@app.get("/internal/api/v1/ai/health", tags=["Internal Health"])
async def internal_health_check():
    return await health_check()


@app.get(
    "/internal/api/v1/ai/ready",
    tags=["Internal Runtime"],
    response_model=RuntimeReadiness,
)
async def runtime_readiness():
    """验证显式目录中的全部模型已经完成 CUDA 装载与热身。"""

    try:
        return _orchestrator().readiness()
    except InferenceServiceError as ex:
        return _runtime_error(ex)


@app.get(
    "/internal/api/v1/ai/models",
    tags=["Internal Model"],
    response_model=ModelCatalogResponse,
)
async def list_models():
    """列出当前进程实际装载的模拟模型和真实模型。"""

    try:
        return _orchestrator().model_catalog()
    except InferenceServiceError as ex:
        return _runtime_error(ex)


@app.get(
    "/internal/api/v1/ai/models/current",
    tags=["Internal Model"],
    response_model=ModelInfo,
)
async def get_current_model_info():
    try:
        return _orchestrator().current_model_info()
    except InferenceServiceError as ex:
        return _runtime_error(ex)


@app.get(
    "/internal/api/v1/ai/models/{model_id}",
    tags=["Internal Model"],
    response_model=ModelInfo,
)
async def get_model_info(model_id: str):
    """按模型编号返回精确运行时身份，禁止替换为其他模型。"""

    try:
        return _orchestrator().model_info(model_id)
    except InferenceServiceError as ex:
        return _runtime_error(ex)


@app.post(
    "/internal/api/v1/ai/image-quality",
    tags=["Internal Image Quality"],
    response_model=ImageQualityResponse,
    responses={
        400: {"model": ImageQualityErrorResponse, "description": "图片为空"},
        413: {"model": ImageQualityErrorResponse, "description": "图片超过大小限制"},
        415: {"model": ImageQualityErrorResponse, "description": "不支持的图片格式"},
        422: {"model": ImageQualityErrorResponse, "description": "图片解码失败"},
    },
)
async def analyze_uploaded_image_quality(
    file: UploadFile = File(..., description="待预检图片"),
    requestId: str = Form("QUALITY", description="调用方请求编号"),
):
    """在发送到在线模型前执行本地、确定性、无权重图片质量预检。"""

    try:
        image_bytes = await file.read()
        return analyze_image_quality(image_bytes, get_settings(), requestId)
    except InferenceServiceError as ex:
        detail = ImageQualityErrorResponse(
            requestId=requestId.strip() or "QUALITY",
            errorCode=ex.error_code,
            errorMessage=ex.message,
        )
        return JSONResponse(status_code=ex.status_code, content=detail.model_dump(mode="json"))


@app.post(
    "/internal/api/v1/ai/inferences",
    tags=["Internal Inference"],
    response_model=InferenceResponse,
    responses={
        400: {"description": "图片为空或元数据无效"},
        413: {"description": "图片超过大小限制"},
        415: {"description": "不支持的图片格式"},
        422: {"description": "图片解码失败或不适用"},
        503: {"description": "请求模型未在 CUDA 运行时就绪"},
    },
)
async def run_inference(
    file: UploadFile = File(..., description="图片字节"),
    metadata: str = Form(..., description="推理元数据 JSON"),
):
    try:
        parsed_metadata = InferenceMetadata.model_validate_json(metadata)
    except (ValueError, json.JSONDecodeError):
        return _error_response(
            status_code=400,
            error_code="AI_SERVICE_INVALID_RESPONSE",
            message="元数据无效或不是合法 JSON",
            request_id="UNKNOWN",
            mode=InferenceMode.MOCK,
            requested_model_id=None,
        )

    try:
        image_bytes = await file.read()
        return _orchestrator().run(
            request_id=parsed_metadata.requestId,
            mode=parsed_metadata.mode,
            image_bytes=image_bytes,
            requested_model_id=parsed_metadata.requestedModelId,
        )
    except InferenceServiceError as ex:
        return _error_response(
            status_code=ex.status_code,
            error_code=ex.error_code,
            message=ex.message,
            request_id=parsed_metadata.requestId,
            mode=parsed_metadata.mode,
            requested_model_id=parsed_metadata.requestedModelId,
        )


def _runtime_error(ex: InferenceServiceError) -> JSONResponse:
    return _error_response(
        status_code=ex.status_code,
        error_code=ex.error_code,
        message=ex.message,
        request_id="RUNTIME",
        mode=InferenceMode.REAL,
        requested_model_id=None,
    )


def _error_response(
    status_code: int,
    error_code: str,
    message: str,
    request_id: str,
    mode: InferenceMode,
    requested_model_id: str | None,
) -> JSONResponse:
    model = None
    if status_code != 503:
        try:
            model = _orchestrator().model_brief(requested_model_id)
        except InferenceServiceError:
            model = None
    detail = InferenceErrorDetail(
        requestId=request_id,
        status=InferenceStatus.FAILED if status_code == 503 else InferenceStatus.REJECTED,
        errorCode=error_code,
        errorMessage=message,
        mode=mode,
        model=model,
        warnings=list(MOCK_WARNINGS) if mode == InferenceMode.MOCK and status_code != 503 else [],
    )
    return JSONResponse(status_code=status_code, content=detail.model_dump(mode="json"))
