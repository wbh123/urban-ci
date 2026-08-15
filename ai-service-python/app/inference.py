"""图片解码、统一模型路由和标准推理结果编排。"""

from __future__ import annotations

import io
import time
from typing import Callable

from PIL import Image

from .adapters.mock import MOCK_WARNINGS
from .config import Settings
from .errors import ModelUnavailableError
from .image import decode_image
from .model_runtime import ModelRegistry
from .precision_inference import PrecisionInferenceEngine
from .schemas import (
    Applicability,
    DetectionItem,
    DetectionSummary,
    ImageInfo,
    InferenceMode,
    InferenceProfile,
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

PRECISION_WARNING = (
    "PRECISION 精度优先模式启用了多尺度候选与局部复核；可信度不代表房屋风险等级"
)

ACCURACY_WARNING = (
    "ACCURACY 多模型精度档位启用了语义门控、双定位器与 SAM 复核；"
    "可信度不代表房屋风险等级"
)

AccuracyRunner = Callable[[object, object], tuple[Applicability, list[DetectionItem]]]


class InferenceOrchestrator:
    """统一管理模型目录、图片解码、精确模型选择和响应标准化。"""

    def __init__(
        self,
        settings: Settings,
        registry: ModelRegistry | None = None,
        accuracy_runner: AccuracyRunner | None = None,
    ) -> None:
        self._settings = settings
        self._registry = registry or ModelRegistry(settings)
        # 正式 ACCURACY runner 只允许由经过 APPROVED Profile 校验的构建器注入。
        self._accuracy_runner = accuracy_runner

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
        inference_profile: InferenceProfile = InferenceProfile.FAST,
    ) -> InferenceResponse:
        """按请求中的精确模型编号和可选推理档位执行一次推理。"""

        adapter = self._registry.resolve(mode, requested_model_id)
        warnings = list(MOCK_WARNINGS if mode == InferenceMode.MOCK else REAL_WARNINGS)

        started = time.monotonic()
        decoded = decode_image(image_bytes, self._settings)
        if mode == InferenceMode.REAL:
            if inference_profile == InferenceProfile.ACCURACY:
                runner = self._accuracy_runner
                if runner is None or not bool(getattr(runner, "approved", False)):
                    raise ModelUnavailableError("ACCURACY 尚未批准或安装到正式运行时")
                applicability, detections = runner(adapter, decoded)
                warnings.append(ACCURACY_WARNING)
            elif inference_profile == InferenceProfile.PRECISION:
                applicability, detections = _run_precision(adapter, decoded)
                warnings.append(PRECISION_WARNING)
            else:
                applicability, detections = adapter.predict(decoded)
        else:
            # MOCK 即使收到 PRECISION/ACCURACY 也保持现有确定性行为；不得伪装真实能力。
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


def _formal_precision_results(results):
    """PRECISION 的 LOW 仅作为内部诊断候选，不进入正式 Detection。"""

    return [
        item
        for item in results
        if str(getattr(item.trust, "level", "")).upper() in {"HIGH", "MEDIUM"}
    ]


def _run_precision(adapter, decoded) -> tuple[Applicability, list[DetectionItem]]:
    """复用已加载 REAL adapter 执行精度优先多尺度推理，不重新加载权重。"""

    if decoded.applicability == Applicability.LOW_QUALITY:
        return Applicability.LOW_QUALITY, []
    required = ("_build_detections", "_sam2_masks", "_dino_processor", "_dino")
    if not all(hasattr(adapter, name) for name in required):
        raise ModelUnavailableError("当前真实视觉模型不支持 PRECISION 推理档位")
    try:
        pil = Image.open(io.BytesIO(decoded.bytes_)).convert("RGB")
    except Exception as ex:
        raise ModelUnavailableError("PRECISION 视觉模型图片解码失败") from ex

    results = _formal_precision_results(PrecisionInferenceEngine(adapter).run_pil(pil))
    if not results:
        return Applicability.NO_DEFECT_FOUND, []

    boxes = [item.candidate.box_xyxy for item in results]
    scores = [item.candidate.max_confidence for item in results]
    codes = [item.candidate.class_code for item in results]
    names = [item.candidate.class_name for item in results]
    masks = [item.mask for item in results]
    base_items = adapter._build_detections(
        boxes,
        scores,
        codes,
        names,
        decoded.width,
        decoded.height,
        masks,
    )

    enriched: list[DetectionItem] = []
    for base, result in zip(base_items, results, strict=False):
        payload = base.model_dump(mode="python")
        payload["trustLevel"] = result.trust.level
        payload["trustReasons"] = list(result.trust.reasons)
        payload["diagnostics"] = dict(result.diagnostics)
        enriched.append(DetectionItem.model_validate(payload))
    if not enriched:
        return Applicability.NO_DEFECT_FOUND, []
    return Applicability.APPLICABLE, enriched


def _build_summary(detections: list[DetectionItem]) -> DetectionSummary:
    class_counts: dict[str, int] = {}
    for item in detections:
        class_counts[item.classCode] = class_counts.get(item.classCode, 0) + 1
    return DetectionSummary(
        detectionCount=len(detections),
        classCounts=class_counts,
    )
