"""图片解码、统一模型路由和标准推理结果编排。"""

from __future__ import annotations

import time

from .adapters.mock import MOCK_WARNINGS
from .config import Settings
from .image import decode_image
from .model_runtime import ModelRegistry
from .schemas import (
    Applicability,
    DetectionItem,
    DetectionSummary,
    ImageInfo,
    InferenceMode,
    InferenceResponse,
    InferenceStatus,
    ModelCatalogResponse,
    ModelInfo,
    RuntimeReadiness,
)


REAL_WARNINGS = [
    "真实模型结果必须经专业人员复核",
    "模型置信度不代表房屋危险概率",
]


class InferenceOrchestrator:
    """统一管理模型目录、图片解码、精确模型选择和响应标准化。"""

    def __init__(self, settings: Settings, registry: ModelRegistry | None = None) -> None:
        self._settings = settings
        self._registry = registry or ModelRegistry(settings)

    def current_model_info(self) -> ModelInfo:
        return self._registry.current_model_info()

    def model_info(self, model_id: str) -> ModelInfo:
        return self._registry.model_info(model_id)

    def model_catalog(self) -> ModelCatalogResponse:
        return self._registry.catalog()

    def readiness(self) -> RuntimeReadiness:
        return self._registry.readiness()

    def model_brief(self, model_id: str | None):
        return self._registry.model_brief(model_id)

    def run(
        self,
        request_id: str,
        mode: InferenceMode,
        image_bytes: bytes,
        requested_model_id: str | None = None,
    ) -> InferenceResponse:
        """按请求中的精确模型编号执行一次推理。"""

        adapter = self._registry.resolve(mode, requested_model_id)
        warnings = list(MOCK_WARNINGS if mode == InferenceMode.MOCK else REAL_WARNINGS)

        started = time.monotonic()
        decoded = decode_image(image_bytes, self._settings)
        applicability, detections = adapter.predict(decoded)
        duration_ms = int((time.monotonic() - started) * 1000)

        status = InferenceStatus.SUCCEEDED
        if applicability == Applicability.NOT_APPLICABLE:
            status = InferenceStatus.REJECTED

        return InferenceResponse(
            requestId=request_id,
            status=status,
            mode=mode,
            model=adapter.model_info(),
            image=ImageInfo(
                width=decoded.width,
                height=decoded.height,
                qualityStatus=decoded.quality_status,
                applicability=applicability,
            ),
            detections=detections,
            summary=_build_summary(detections),
            durationMs=duration_ms,
            warnings=warnings,
        )


def _build_summary(detections: list[DetectionItem]) -> DetectionSummary:
    class_counts: dict[str, int] = {}
    for item in detections:
        class_counts[item.classCode] = class_counts.get(item.classCode, 0) + 1
    return DetectionSummary(
        detectionCount=len(detections),
        classCounts=class_counts,
    )
