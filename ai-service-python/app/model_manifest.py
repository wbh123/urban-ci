"""离线真实模型清单加载与准入校验。"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any


class ModelManifestError(ValueError):
    """模型清单、权重或准入信息不合法。"""


@dataclass(frozen=True)
class ModelClass:
    code: str
    name: str


@dataclass(frozen=True)
class ModelInput:
    width: int
    height: int
    mean: tuple[float, float, float]
    std: tuple[float, float, float]
    interpolation: str


@dataclass(frozen=True)
class ModelThresholds:
    mask: float
    min_component_pixels: int
    max_component_area_ratio: float = 0.60
    max_bounding_box_area_ratio: float = 0.90
    minimum_mean_confidence: float = 0.0
    maximum_detections: int = 30


@dataclass(frozen=True)
class ModelMetrics:
    dataset: str
    pixel_f1: float
    iou: float
    image_recall: float


@dataclass(frozen=True)
class ModelSource:
    type: str
    repository: str
    revision: str
    license: str


@dataclass(frozen=True)
class ModelManifest:
    schema_version: int
    model_id: str
    model_name: str
    version: str
    status: str
    task: str
    adapter: str
    weight_path: Path
    weight_sha256: str
    source: ModelSource
    classes: tuple[ModelClass, ...]
    input: ModelInput
    output_activation: str
    foreground_polarity: str
    thresholds: ModelThresholds
    metrics: ModelMetrics
    license: str
    approved_by: str
    approved_at: str


SUPPORTED_ADAPTER_TASKS = {
    "onnx-crack-segmentation-v1": "CRACK_SEGMENTATION",
}
SUPPORTED_OUTPUT_ACTIVATIONS = {"LOGITS", "PROBABILITY"}
SUPPORTED_FOREGROUND_POLARITIES = {"HIGH_PROBABILITY", "LOW_PROBABILITY"}
SUPPORTED_INPUT_INTERPOLATIONS = {"BILINEAR", "BICUBIC", "LANCZOS"}


def load_model_manifest(path: str | Path, model_root: str | Path) -> ModelManifest:
    """加载已批准模型清单，并验证权重路径、摘要和推理契约。

    按清单 adapter 字段分派：零样本视觉模型委托给 vision_manifest；YOLOX
    建筑病害检测委托给 yolox_manifest；其余继续走 CUDA-only ONNX 分割准入逻辑。
    """

    root = Path(model_root).expanduser().resolve()
    manifest_path = Path(path).expanduser().resolve()
    if not _is_within(manifest_path, root):
        raise ModelManifestError("模型清单必须位于模型根目录内")
    if not manifest_path.is_file():
        raise ModelManifestError("模型清单不存在")

    adapter = _peek_adapter(manifest_path)
    if adapter in ("grounded-sam2-tiny-v1", "grounded-sam2-v1"):
        from .vision_manifest import load_zero_shot_manifest

        return load_zero_shot_manifest(manifest_path, root)
    if adapter == "yolox-building-defect-v1":
        from .yolox_manifest import load_yolox_manifest

        return load_yolox_manifest(manifest_path, root)

    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ModelManifestError("模型清单不是合法 UTF-8 JSON") from ex
    if not isinstance(payload, dict):
        raise ModelManifestError("模型清单根节点必须是对象")

    schema_version = _integer(payload, "schemaVersion")
    if schema_version != 1:
        raise ModelManifestError("仅支持 schemaVersion=1")

    status = _text(payload, "status").upper()
    if status != "APPROVED":
        raise ModelManifestError("真实模型清单状态必须为 APPROVED")

    task = _text(payload, "task")
    adapter = _text(payload, "adapter")
    expected_task = SUPPORTED_ADAPTER_TASKS.get(adapter)
    if expected_task is None:
        raise ModelManifestError("不支持的真实模型适配器")
    if task != expected_task:
        raise ModelManifestError("模型任务与适配器不匹配")

    output_activation = _optional_text(payload, "outputActivation", "LOGITS").upper()
    if output_activation not in SUPPORTED_OUTPUT_ACTIVATIONS:
        raise ModelManifestError("字段 outputActivation 仅支持 LOGITS 或 PROBABILITY")
    foreground_polarity = _optional_text(
        payload, "foregroundPolarity", "HIGH_PROBABILITY"
    ).upper()
    if foreground_polarity not in SUPPORTED_FOREGROUND_POLARITIES:
        raise ModelManifestError(
            "字段 foregroundPolarity 仅支持 HIGH_PROBABILITY 或 LOW_PROBABILITY"
        )

    relative_weight = Path(_text(payload, "weightFile"))
    if relative_weight.is_absolute() or ".." in relative_weight.parts:
        raise ModelManifestError("权重文件必须位于模型根目录内")
    weight_path = (manifest_path.parent / relative_weight).resolve()
    if not _is_within(weight_path, root):
        raise ModelManifestError("权重文件必须位于模型根目录内")
    if not weight_path.is_file():
        raise ModelManifestError("权重文件不存在")

    expected_sha256 = _text(payload, "weightSha256").lower()
    if len(expected_sha256) != 64 or any(
        ch not in "0123456789abcdef" for ch in expected_sha256
    ):
        raise ModelManifestError("权重 SHA-256 格式不合法")
    if _sha256(weight_path) != expected_sha256:
        raise ModelManifestError("权重 SHA-256 不匹配")

    source_payload = _mapping(payload, "source")
    source = ModelSource(
        type=_text(source_payload, "type"),
        repository=_text(source_payload, "repository"),
        revision=_text(source_payload, "revision"),
        license=_text(source_payload, "license"),
    )
    if source.license.upper() in {"UNKNOWN", "UNLICENSED", "NONE"}:
        raise ModelManifestError("模型来源许可证未核验")

    classes_payload = payload.get("classes")
    if not isinstance(classes_payload, list) or not classes_payload:
        raise ModelManifestError("模型类别不能为空")
    classes = tuple(
        ModelClass(code=_text(item, "code"), name=_text(item, "name"))
        for item in classes_payload
        if isinstance(item, dict)
    )
    if len(classes) != len(classes_payload) or not any(
        item.code == "CRACK" for item in classes
    ):
        raise ModelManifestError("模型类别必须包含 CRACK")

    input_payload = _mapping(payload, "input")
    mean = _triple(input_payload, "mean")
    std = _triple(input_payload, "std")
    if any(value <= 0 for value in std):
        raise ModelManifestError("归一化标准差必须大于 0")
    interpolation = _optional_text(input_payload, "interpolation", "BILINEAR").upper()
    if interpolation not in SUPPORTED_INPUT_INTERPOLATIONS:
        raise ModelManifestError(
            "字段 input.interpolation 仅支持 BILINEAR、BICUBIC 或 LANCZOS"
        )
    model_input = ModelInput(
        width=_positive_integer(input_payload, "width"),
        height=_positive_integer(input_payload, "height"),
        mean=mean,
        std=std,
        interpolation=interpolation,
    )

    threshold_payload = _mapping(payload, "thresholds")
    mask_threshold = _number(threshold_payload, "mask")
    if not 0.0 < mask_threshold < 1.0:
        raise ModelManifestError("掩膜阈值必须位于 0 和 1 之间")
    thresholds = ModelThresholds(
        mask=mask_threshold,
        min_component_pixels=_positive_integer(
            threshold_payload, "minComponentPixels"
        ),
        max_component_area_ratio=_optional_unit_ratio(
            threshold_payload, "maxComponentAreaRatio", 0.60, allow_zero=False
        ),
        max_bounding_box_area_ratio=_optional_unit_ratio(
            threshold_payload, "maxBoundingBoxAreaRatio", 0.90, allow_zero=False
        ),
        minimum_mean_confidence=_optional_unit_ratio(
            threshold_payload, "minimumMeanConfidence", 0.0, allow_zero=True
        ),
        maximum_detections=_optional_positive_integer(
            threshold_payload, "maximumDetections", 30
        ),
    )

    metrics_payload = _mapping(payload, "metrics")
    metrics = ModelMetrics(
        dataset=_text(metrics_payload, "dataset"),
        pixel_f1=_unit_metric(metrics_payload, "pixelF1"),
        iou=_unit_metric(metrics_payload, "iou"),
        image_recall=_unit_metric(metrics_payload, "imageRecall"),
    )

    return ModelManifest(
        schema_version=schema_version,
        model_id=_text(payload, "modelId"),
        model_name=_text(payload, "modelName"),
        version=_text(payload, "version"),
        status=status,
        task=task,
        adapter=adapter,
        weight_path=weight_path,
        weight_sha256=expected_sha256,
        source=source,
        classes=classes,
        input=model_input,
        output_activation=output_activation,
        foreground_polarity=foreground_polarity,
        thresholds=thresholds,
        metrics=metrics,
        license=_text(payload, "license"),
        approved_by=_text(payload, "approvedBy"),
        approved_at=_text(payload, "approvedAt"),
    )


def _peek_adapter(manifest_path: Path) -> str:
    """只读取清单中的 adapter 字段用于分派，不执行完整准入校验。"""

    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ModelManifestError("模型清单不是合法 UTF-8 JSON") from ex
    if not isinstance(payload, dict) or not isinstance(payload.get("adapter"), str):
        raise ModelManifestError("模型清单缺少 adapter 字段")
    adapter = payload["adapter"].strip()
    if not adapter:
        raise ModelManifestError("模型清单 adapter 字段不能为空")
    return adapter


def _is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _mapping(payload: dict[str, Any], key: str) -> dict[str, Any]:
    value = payload.get(key)
    if not isinstance(value, dict):
        raise ModelManifestError(f"字段 {key} 必须是对象")
    return value


def _text(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ModelManifestError(f"字段 {key} 不能为空")
    return value.strip()


def _optional_text(payload: dict[str, Any], key: str, default: str) -> str:
    if key not in payload:
        return default
    return _text(payload, key)


def _integer(payload: dict[str, Any], key: str) -> int:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise ModelManifestError(f"字段 {key} 必须是整数")
    return value


def _positive_integer(payload: dict[str, Any], key: str) -> int:
    value = _integer(payload, key)
    if value <= 0:
        raise ModelManifestError(f"字段 {key} 必须大于 0")
    return value


def _optional_positive_integer(
    payload: dict[str, Any], key: str, default: int
) -> int:
    if key not in payload:
        return default
    return _positive_integer(payload, key)


def _number(payload: dict[str, Any], key: str) -> float:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ModelManifestError(f"字段 {key} 必须是数字")
    return float(value)


def _optional_unit_ratio(
    payload: dict[str, Any], key: str, default: float, *, allow_zero: bool
) -> float:
    if key not in payload:
        return default
    value = _number(payload, key)
    lower_valid = value >= 0.0 if allow_zero else value > 0.0
    if not lower_valid or value > 1.0:
        boundary = "0 到 1" if allow_zero else "大于 0 且不超过 1"
        raise ModelManifestError(f"字段 {key} 必须位于{boundary}范围内")
    return value


def _unit_metric(payload: dict[str, Any], key: str) -> float:
    value = _number(payload, key)
    if not 0.0 <= value <= 1.0:
        raise ModelManifestError(f"指标 {key} 必须位于 0 和 1 之间")
    return value


def _triple(payload: dict[str, Any], key: str) -> tuple[float, float, float]:
    value = payload.get(key)
    if not isinstance(value, list) or len(value) != 3:
        raise ModelManifestError(f"字段 {key} 必须包含三个数字")
    result: list[float] = []
    for item in value:
        if isinstance(item, bool) or not isinstance(item, (int, float)):
            raise ModelManifestError(f"字段 {key} 必须包含三个数字")
        result.append(float(item))
    return result[0], result[1], result[2]
