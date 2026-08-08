"""本地图片语义适用性分类 Provider。

该模块只回答“图片是否适合进入建筑表观病害分析”，不判断病害类型。
正式运行时只消费本地 ONNX 文件，禁止运行时联网下载模型。
"""

from __future__ import annotations

import hashlib
import io
import json
import logging
from pathlib import Path
from typing import Any, Protocol

import numpy as np
from PIL import Image, UnidentifiedImageError

from .config import Settings
from .errors import (
    ImageDecodeFailedError,
    ImageEmptyError,
    ImageTooLargeError,
    ImageUnsupportedFormatError,
)
from .image import FORMAT_TO_CONTENT_TYPE
from .schemas import ImageApplicabilityDecision, ImageApplicabilityResponse


LOGGER = logging.getLogger(__name__)
LOCAL_IMAGE_APPLICABILITY_MODEL_ID = "LOCAL-IMAGE-APPLICABILITY-001"
LOCAL_IMAGE_APPLICABILITY_MODEL_VERSION = "1.0.0"
_REQUIRED_CLASSES = {
    ImageApplicabilityDecision.APPLICABLE.value,
    ImageApplicabilityDecision.NOT_APPLICABLE.value,
}


class ImageApplicabilityProvider(Protocol):
    """图片语义适用性分类器稳定接口。"""

    def classify(self, image_bytes: bytes, request_id: str) -> ImageApplicabilityResponse:
        ...


class UnavailableImageApplicabilityProvider:
    """模型未安装、禁用或加载失败时使用的 fail-open Provider。"""

    def __init__(self, settings: Settings, reason: str = "MODEL_UNAVAILABLE") -> None:
        self._settings = settings
        self._reason = reason

    def classify(self, image_bytes: bytes, request_id: str) -> ImageApplicabilityResponse:
        _decode_rgb(image_bytes, self._settings)
        return _uncertain_response(
            request_id=request_id,
            model_id=self._settings.applicability_model_id,
            model_version=self._settings.applicability_model_version,
            reason=self._reason,
        )


class OnnxImageApplicabilityProvider:
    """使用 ONNX Runtime CPU 执行整图二分类，并按阈值生成三态决策。"""

    def __init__(self, settings: Settings, session: Any | None = None) -> None:
        self._settings = settings
        self._metadata = _load_metadata(settings.applicability_metadata_path)
        self._model_id = str(
            self._metadata.get("modelId") or settings.applicability_model_id
        ).strip()
        self._model_version = str(
            self._metadata.get("modelVersion") or settings.applicability_model_version
        ).strip()
        self._classes = [str(value).strip().upper() for value in self._metadata["classes"]]
        if not _REQUIRED_CLASSES.issubset(set(self._classes)):
            raise ValueError("图片适用性模型 classes 必须包含 APPLICABLE 与 NOT_APPLICABLE")

        _verify_weight_sha256(settings.applicability_model_path, self._metadata)

        self._input_height, self._input_width = _parse_input_size(
            self._metadata.get("inputSize", [224, 224])
        )
        self._mean = _parse_vector(self._metadata.get("mean", [0.0, 0.0, 0.0]), "mean")
        self._std = _parse_vector(self._metadata.get("std", [1.0, 1.0, 1.0]), "std")
        if np.any(self._std <= 0.0):
            raise ValueError("图片适用性模型 std 必须全部大于 0")
        self._output_type = str(self._metadata.get("outputType", "LOGITS")).strip().upper()
        if self._output_type not in {"LOGITS", "PROBABILITIES"}:
            raise ValueError("图片适用性模型 outputType 只支持 LOGITS 或 PROBABILITIES")

        self._session = session or _create_cpu_session(settings.applicability_model_path)
        inputs = list(self._session.get_inputs())
        if not inputs:
            raise ValueError("图片适用性 ONNX 模型没有输入节点")
        self._input_name = str(self._metadata.get("inputName") or inputs[0].name)
        output_name = self._metadata.get("outputName")
        self._output_names = [str(output_name)] if output_name else None

    def classify(self, image_bytes: bytes, request_id: str) -> ImageApplicabilityResponse:
        image = _decode_rgb(image_bytes, self._settings)
        try:
            tensor = self._preprocess(image)
            outputs = self._session.run(self._output_names, {self._input_name: tensor})
            if not outputs:
                return self._uncertain(request_id, "INVALID_MODEL_OUTPUT")
            raw_scores = np.asarray(outputs[0], dtype=np.float64).reshape(-1)
            if raw_scores.size != len(self._classes) or not np.all(np.isfinite(raw_scores)):
                return self._uncertain(request_id, "INVALID_MODEL_OUTPUT")
            probabilities = self._to_probabilities(raw_scores)
            return self._decide(request_id, probabilities)
        except Exception as ex:  # fail-open：辅助门禁故障不能阻断真实病害主链。
            LOGGER.warning("图片语义适用性模型推理失败，按 UNCERTAIN 放行：%s", ex)
            return self._uncertain(request_id, "MODEL_INFERENCE_FAILED")

    def _preprocess(self, image: Image.Image) -> np.ndarray:
        resized = image.resize(
            (self._input_width, self._input_height),
            Image.Resampling.BILINEAR,
        )
        array = np.asarray(resized, dtype=np.float32) / 255.0
        array = (array - self._mean.reshape(1, 1, 3)) / self._std.reshape(1, 1, 3)
        return np.transpose(array, (2, 0, 1))[None, ...].astype(np.float32, copy=False)

    def _to_probabilities(self, raw_scores: np.ndarray) -> np.ndarray:
        if self._output_type == "PROBABILITIES":
            clipped = np.clip(raw_scores, 0.0, None)
            total = float(np.sum(clipped))
            if total <= 0.0:
                raise ValueError("概率输出总和必须大于 0")
            return clipped / total
        shifted = raw_scores - float(np.max(raw_scores))
        exp = np.exp(shifted)
        total = float(np.sum(exp))
        if total <= 0.0 or not np.isfinite(total):
            raise ValueError("logits 无法转换为合法概率")
        return exp / total

    def _decide(
        self,
        request_id: str,
        probabilities: np.ndarray,
    ) -> ImageApplicabilityResponse:
        scores = {
            class_name: round(float(probability), 6)
            for class_name, probability in zip(self._classes, probabilities, strict=True)
        }
        applicable = float(scores[ImageApplicabilityDecision.APPLICABLE.value])
        not_applicable = float(scores[ImageApplicabilityDecision.NOT_APPLICABLE.value])

        if not_applicable >= self._settings.applicability_reject_threshold:
            return ImageApplicabilityResponse(
                requestId=_request_id(request_id),
                modelId=self._model_id,
                modelVersion=self._model_version,
                decision=ImageApplicabilityDecision.NOT_APPLICABLE,
                confidence=round(not_applicable, 6),
                scores=scores,
                allowDify=False,
                reason="HIGH_CONFIDENCE_NOT_APPLICABLE",
            )
        if applicable >= self._settings.applicability_applicable_threshold:
            return ImageApplicabilityResponse(
                requestId=_request_id(request_id),
                modelId=self._model_id,
                modelVersion=self._model_version,
                decision=ImageApplicabilityDecision.APPLICABLE,
                confidence=round(applicable, 6),
                scores=scores,
                allowDify=True,
                reason="HIGH_CONFIDENCE_APPLICABLE",
            )
        return ImageApplicabilityResponse(
            requestId=_request_id(request_id),
            modelId=self._model_id,
            modelVersion=self._model_version,
            decision=ImageApplicabilityDecision.UNCERTAIN,
            confidence=round(max(applicable, not_applicable), 6),
            scores=scores,
            allowDify=True,
            reason="LOW_CONFIDENCE",
        )

    def _uncertain(self, request_id: str, reason: str) -> ImageApplicabilityResponse:
        return _uncertain_response(
            request_id=request_id,
            model_id=self._model_id,
            model_version=self._model_version,
            reason=reason,
        )


def build_image_applicability_provider(settings: Settings) -> ImageApplicabilityProvider:
    """按本地配置创建 Provider；模型缺失或加载失败时稳定 fail-open。"""

    if not settings.applicability_enabled:
        return UnavailableImageApplicabilityProvider(settings, "DISABLED")
    if not settings.applicability_model_path.is_file():
        return UnavailableImageApplicabilityProvider(settings, "MODEL_FILE_MISSING")
    if not settings.applicability_metadata_path.is_file():
        return UnavailableImageApplicabilityProvider(settings, "MODEL_METADATA_MISSING")
    try:
        return OnnxImageApplicabilityProvider(settings)
    except Exception as ex:
        LOGGER.warning("图片语义适用性模型加载失败，按 UNCERTAIN 放行：%s", ex)
        return UnavailableImageApplicabilityProvider(settings, "MODEL_LOAD_FAILED")


def _create_cpu_session(model_path: Path):
    """延迟导入 ONNX Runtime，强制只使用 CPUExecutionProvider。"""

    import onnxruntime as ort

    return ort.InferenceSession(
        str(model_path),
        providers=["CPUExecutionProvider"],
    )


def _load_metadata(path: Path) -> dict[str, Any]:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict):
        raise ValueError("图片适用性模型元数据必须是 JSON 对象")
    classes = payload.get("classes")
    if not isinstance(classes, list) or len(classes) < 2:
        raise ValueError("图片适用性模型元数据缺少 classes")
    digest = str(payload.get("weightSha256") or "").strip().lower()
    if len(digest) != 64 or any(char not in "0123456789abcdef" for char in digest):
        raise ValueError("图片适用性模型元数据必须包含合法 weightSha256")
    return payload


def _verify_weight_sha256(model_path: Path, metadata: dict[str, Any]) -> None:
    expected = str(metadata["weightSha256"]).strip().lower()
    digest = hashlib.sha256()
    with model_path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    actual = digest.hexdigest().lower()
    if actual != expected:
        raise ValueError("图片适用性模型 weightSha256 与本地 ONNX 文件不一致")


def _parse_input_size(value: Any) -> tuple[int, int]:
    if isinstance(value, int):
        height = width = value
    elif isinstance(value, list) and len(value) == 2:
        height, width = int(value[0]), int(value[1])
    else:
        raise ValueError("图片适用性模型 inputSize 必须是整数或 [height,width]")
    if height <= 0 or width <= 0:
        raise ValueError("图片适用性模型 inputSize 必须大于 0")
    return height, width


def _parse_vector(value: Any, name: str) -> np.ndarray:
    if not isinstance(value, list) or len(value) != 3:
        raise ValueError(f"图片适用性模型 {name} 必须包含 3 个数值")
    vector = np.asarray(value, dtype=np.float32)
    if not np.all(np.isfinite(vector)):
        raise ValueError(f"图片适用性模型 {name} 包含非法数值")
    return vector


def _decode_rgb(image_bytes: bytes, settings: Settings) -> Image.Image:
    if not image_bytes:
        raise ImageEmptyError()
    if len(image_bytes) > settings.max_image_size_bytes:
        raise ImageTooLargeError()
    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.load()
    except (UnidentifiedImageError, OSError, ValueError) as ex:
        raise ImageDecodeFailedError() from ex

    image_format = (image.format or "").upper()
    if FORMAT_TO_CONTENT_TYPE.get(image_format) is None:
        raise ImageUnsupportedFormatError()
    width, height = image.size
    if width <= 0 or height <= 0:
        raise ImageDecodeFailedError()
    return image.convert("RGB")


def _uncertain_response(
    request_id: str,
    model_id: str,
    model_version: str,
    reason: str,
) -> ImageApplicabilityResponse:
    return ImageApplicabilityResponse(
        requestId=_request_id(request_id),
        modelId=model_id or LOCAL_IMAGE_APPLICABILITY_MODEL_ID,
        modelVersion=model_version or LOCAL_IMAGE_APPLICABILITY_MODEL_VERSION,
        decision=ImageApplicabilityDecision.UNCERTAIN,
        confidence=0.0,
        scores={},
        allowDify=True,
        reason=reason,
    )


def _request_id(request_id: str) -> str:
    normalized = (request_id or "").strip()
    return normalized or "APPLICABILITY"
