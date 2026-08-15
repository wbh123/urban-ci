"""YOLOX 建筑病害检测模型的离线清单与正式运行时准入校验。"""

from __future__ import annotations

import hashlib
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .model_manifest import ModelManifestError, ModelSource


YOLOX_BUILDING_DEFECT_ADAPTER = "yolox-building-defect-v1"
YOLOX_BUILDING_DEFECT_TASK = "BUILDING_DEFECT_DETECTION"
YOLOX_BUILDING_DEFECT_CODES = (
    "CRACK",
    "SPALLING",
    "EXPOSED_REBAR",
    "CORROSION",
    "WATER_SEEPAGE",
    "EFFLORESCENCE",
    "WALL_DAMAGE",
)
_FIXED_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")
_PENDING_APPROVAL = {"", "PENDING", "PENDING-HUMAN-REVIEW"}


@dataclass(frozen=True)
class YoloXClass:
    code: str
    name: str


@dataclass(frozen=True)
class YoloXInput:
    width: int
    height: int
    pad_value: int


@dataclass(frozen=True)
class YoloXThresholds:
    score: float
    nms_iou: float
    maximum_detections: int


@dataclass(frozen=True)
class YoloXMetrics:
    dataset: str
    map50: float
    map5095: float
    precision: float
    recall: float


@dataclass(frozen=True)
class YoloXModelManifest:
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
    classes: tuple[YoloXClass, ...]
    input: YoloXInput
    thresholds: YoloXThresholds
    metrics: YoloXMetrics
    license: str
    approved_by: str
    approved_at: str


def load_yolox_manifest(path: str | Path, model_root: str | Path) -> YoloXModelManifest:
    """加载已 APPROVED 的 YOLOX ONNX 模型；未审批模型禁止进入 REAL 运行时。"""

    root = Path(model_root).expanduser().resolve()
    manifest_path = Path(path).expanduser().resolve()
    if not _is_within(manifest_path, root):
        raise ModelManifestError("YOLOX 模型清单必须位于模型根目录内")
    if not manifest_path.is_file():
        raise ModelManifestError("YOLOX 模型清单不存在")

    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ModelManifestError("YOLOX 模型清单不是合法 UTF-8 JSON") from ex
    if not isinstance(payload, dict):
        raise ModelManifestError("YOLOX 模型清单根节点必须是对象")

    schema_version = _integer(payload, "schemaVersion")
    if schema_version != 1:
        raise ModelManifestError("YOLOX 仅支持 schemaVersion=1")

    status = _text(payload, "status").upper()
    if status != "APPROVED":
        raise ModelManifestError("YOLOX 真实模型清单状态必须为 APPROVED")
    approved_by = _text(payload, "approvedBy")
    approved_at = _text(payload, "approvedAt")
    if approved_by.upper() in _PENDING_APPROVAL or approved_at.upper() in _PENDING_APPROVAL:
        raise ModelManifestError("YOLOX APPROVED 必须填写真实审批人与审批时间")

    adapter = _text(payload, "adapter")
    if adapter != YOLOX_BUILDING_DEFECT_ADAPTER:
        raise ModelManifestError("不支持的 YOLOX 建筑病害适配器")
    task = _text(payload, "task")
    if task != YOLOX_BUILDING_DEFECT_TASK:
        raise ModelManifestError("YOLOX 模型任务与适配器不匹配")

    relative_weight = Path(_text(payload, "weightFile"))
    if relative_weight.is_absolute() or ".." in relative_weight.parts:
        raise ModelManifestError("YOLOX 权重文件必须位于模型根目录内")
    weight_path = (manifest_path.parent / relative_weight).resolve()
    if not _is_within(weight_path, root):
        raise ModelManifestError("YOLOX 权重文件必须位于模型根目录内")
    if not weight_path.is_file():
        raise ModelManifestError("YOLOX 权重文件不存在")

    weight_sha256 = _sha256_hex(payload.get("weightSha256"), "weightSha256")
    if _sha256(weight_path) != weight_sha256:
        raise ModelManifestError("YOLOX 权重 SHA-256 不匹配")

    source_payload = _mapping(payload, "source")
    revision = _text(source_payload, "revision").lower()
    if not _FIXED_SHA_PATTERN.fullmatch(revision):
        raise ModelManifestError("YOLOX source.revision 必须为固定 40 位 commit SHA")
    source = ModelSource(
        type=_text(source_payload, "type"),
        repository=_text(source_payload, "repository"),
        revision=revision,
        license=_verified_license(_text(source_payload, "license")),
    )

    classes_payload = payload.get("classes")
    if not isinstance(classes_payload, list) or not classes_payload:
        raise ModelManifestError("YOLOX 模型类别不能为空")
    classes = tuple(
        YoloXClass(code=_text(item, "code"), name=_text(item, "name"))
        for item in classes_payload
        if isinstance(item, dict)
    )
    class_codes = tuple(item.code for item in classes)
    if len(classes) != len(classes_payload) or class_codes != YOLOX_BUILDING_DEFECT_CODES:
        raise ModelManifestError(
            "YOLOX 模型类别必须严格按建筑病害类别合同排列，禁止缺失、重复或乱序"
        )

    input_payload = _mapping(payload, "input")
    width = _positive_integer(input_payload, "width")
    height = _positive_integer(input_payload, "height")
    if width % 32 != 0 or height % 32 != 0:
        raise ModelManifestError("YOLOX input.width/height 必须为 32 的倍数")
    pad_value = _integer(input_payload, "padValue")
    if not 0 <= pad_value <= 255:
        raise ModelManifestError("YOLOX input.padValue 必须位于 0~255")
    model_input = YoloXInput(width=width, height=height, pad_value=pad_value)

    threshold_payload = _mapping(payload, "thresholds")
    thresholds = YoloXThresholds(
        score=_open_unit_ratio(threshold_payload, "score"),
        nms_iou=_open_unit_ratio(threshold_payload, "nmsIou"),
        maximum_detections=_positive_integer(threshold_payload, "maximumDetections"),
    )

    metrics_payload = _mapping(payload, "metrics")
    metrics = YoloXMetrics(
        dataset=_text(metrics_payload, "dataset"),
        map50=_unit_ratio(metrics_payload, "map50"),
        map5095=_unit_ratio(metrics_payload, "map5095"),
        precision=_unit_ratio(metrics_payload, "precision"),
        recall=_unit_ratio(metrics_payload, "recall"),
    )

    return YoloXModelManifest(
        schema_version=schema_version,
        model_id=_text(payload, "modelId"),
        model_name=_text(payload, "modelName"),
        version=_text(payload, "version"),
        status=status,
        task=task,
        adapter=adapter,
        weight_path=weight_path,
        weight_sha256=weight_sha256,
        source=source,
        classes=classes,
        input=model_input,
        thresholds=thresholds,
        metrics=metrics,
        license=_verified_license(_text(payload, "license")),
        approved_by=approved_by,
        approved_at=approved_at,
    )


def _is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


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


def _unit_ratio(payload: dict[str, Any], key: str) -> float:
    value = payload.get(key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise ModelManifestError(f"字段 {key} 必须是数字")
    numeric = float(value)
    if not 0.0 <= numeric <= 1.0:
        raise ModelManifestError(f"字段 {key} 必须位于 0 和 1 之间")
    return numeric


def _open_unit_ratio(payload: dict[str, Any], key: str) -> float:
    value = _unit_ratio(payload, key)
    if value <= 0.0:
        raise ModelManifestError(f"字段 {key} 必须大于 0")
    return value


def _sha256_hex(value: Any, field: str) -> str:
    if not isinstance(value, str):
        raise ModelManifestError(f"字段 {field} 必须是字符串")
    normalized = value.strip().lower()
    if len(normalized) != 64 or any(ch not in "0123456789abcdef" for ch in normalized):
        raise ModelManifestError(f"字段 {field} SHA-256 格式不合法")
    return normalized


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verified_license(value: str) -> str:
    if value.strip().upper() in {"UNKNOWN", "UNLICENSED", "NONE"}:
        raise ModelManifestError("YOLOX 模型来源许可证未核验")
    return value.strip()
