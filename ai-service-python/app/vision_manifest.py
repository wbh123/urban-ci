"""零样本视觉模型清单加载与准入校验。

Grounding DINO + SAM 2 这类 Transformers 零样本模型没有单一 ONNX 权重文件，权重以
本地目录形式存在（由 download-models-demo.sh 从可信来源下载）。

准入门禁：

- status 必须为 ``CANDIDATE`` 或 ``APPROVED``；``APPROVED`` 必须带真实审批人与时间，
  禁止 ``PENDING-HUMAN-REVIEW`` / ``PENDING`` 组合；
- 模型来源 revision 必须为固定 40 位 commit SHA，禁止 ``main`` / 分支 / 短哈希；
- 许可证必须已核验；source 记录来源平台与固定 revision；
- checkpoint 记录 detector/segmenter 仓库、固定 revision、本地目录与摘要。

解析阶段不要求权重目录存在（由下载脚本写入，adapter 加载时以 local_files_only 校验）。
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from .model_manifest import ModelManifestError, ModelSource

# 中性协议 grounded-sam2-v1；旧 Tiny 适配器 ID grounded-sam2-tiny-v1 向后兼容。
SUPPORTED_ZERO_SHOT_ADAPTERS = {"grounded-sam2-v1", "grounded-sam2-tiny-v1"}
SUPPORTED_ZERO_SHOT_TASK = "ZERO_SHOT_VISUAL_DEFECT"
SUPPORTED_STATUSES = {"CANDIDATE", "APPROVED"}
APPROVED_BY_PENDING = {"PENDING", "PENDING-HUMAN-REVIEW", ""}
APPROVED_AT_PENDING = {"PENDING", ""}

_FULL_SHA_PATTERN = re.compile(r"^[0-9a-f]{40}$")


@dataclass(frozen=True)
class ZeroShotClass:
    """带多个零样本提示词的病害类别；多个提示词映射回同一业务类别。"""

    code: str
    name: str
    prompts: tuple[str, ...]


@dataclass(frozen=True)
class ZeroShotCheckpoint:
    """本地视觉模型来源标识与权重目录（目录解析为模型根目录内绝对路径）。"""

    detector_repository: str
    segmenter_repository: str
    detector_revision: str
    segmenter_revision: str
    detector_dir: Path
    segmenter_dir: Path
    sha256: str
    size_bytes: int


@dataclass(frozen=True)
class ZeroShotInput:
    """零样本视觉推理输入约束。"""

    max_long_side: int
    box_threshold: float
    text_threshold: float
    max_detections: int


@dataclass(frozen=True)
class ZeroShotModelManifest:
    schema_version: int
    model_id: str
    model_name: str
    version: str
    status: str
    identity_verified: bool
    task: str
    adapter: str
    weight_sha256: str
    source: ModelSource
    classes: tuple[ZeroShotClass, ...]
    checkpoint: ZeroShotCheckpoint
    input: ZeroShotInput
    license: str
    approved_by: str
    approved_at: str


def load_zero_shot_manifest(path: str | Path, model_root: str | Path) -> ZeroShotModelManifest:
    """加载零样本视觉模型清单。

    与 ONNX 清单一致：清单必须位于模型根目录内；权重位于本地目录，
    解析阶段不校验权重存在性，实际可用性由 adapter 在 CUDA 加载时校验。
    """

    root = Path(model_root).expanduser().resolve()
    manifest_path = Path(path).expanduser().resolve()
    if not _is_within(manifest_path, root):
        raise ModelManifestError("模型清单必须位于模型根目录内")
    if not manifest_path.is_file():
        raise ModelManifestError("模型清单不存在")

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
    if status not in SUPPORTED_STATUSES:
        raise ModelManifestError("模型清单状态必须为 CANDIDATE 或 APPROVED")
    # CANDIDATE 允许空审批人/时间；APPROVED 必须真实填写。
    approved_by = _optional_text(payload, "approvedBy")
    approved_at = _optional_text(payload, "approvedAt")
    identity_verified = _optional_bool(payload, "identityVerified", False)
    if status == "APPROVED":
        if approved_by.upper() in APPROVED_BY_PENDING:
            raise ModelManifestError("APPROVED 必须填写真实审批人，禁止 PENDING")
        if approved_at.upper() in APPROVED_AT_PENDING:
            raise ModelManifestError("APPROVED 必须填写审批时间，禁止 PENDING")
        if not identity_verified:
            raise ModelManifestError(
                "APPROVED 必须 identityVerified=true（固定 revision 权重身份校验通过）"
            )

    adapter = _text(payload, "adapter")
    if adapter not in SUPPORTED_ZERO_SHOT_ADAPTERS:
        raise ModelManifestError("不支持的零样本视觉模型适配器")
    task = _text(payload, "task")
    if task != SUPPORTED_ZERO_SHOT_TASK:
        raise ModelManifestError("模型任务与零样本视觉适配器不匹配")

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
        ZeroShotClass(
            code=_text(item, "code"),
            name=_text(item, "name"),
            prompts=_string_list(item, "prompts"),
        )
        for item in classes_payload
        if isinstance(item, dict)
    )
    if len(classes) != len(classes_payload):
        raise ModelManifestError("模型类别字段不完整")

    checkpoint_payload = _mapping(payload, "checkpoint")
    checkpoint = ZeroShotCheckpoint(
        detector_repository=_text(checkpoint_payload, "detectorRepository"),
        segmenter_repository=_text(checkpoint_payload, "segmenterRepository"),
        detector_revision=_fixed_sha(checkpoint_payload.get("detectorRevision"), "detectorRevision"),
        segmenter_revision=_fixed_sha(checkpoint_payload.get("segmenterRevision"), "segmenterRevision"),
        detector_dir=_relative_dir(checkpoint_payload, "detectorDir", root),
        segmenter_dir=_relative_dir(checkpoint_payload, "segmenterDir", root),
        sha256=_sha256_hex(checkpoint_payload.get("sha256")),
        size_bytes=_positive_integer(checkpoint_payload, "sizeBytes"),
    )

    input_payload = _mapping(payload, "input")
    model_input = ZeroShotInput(
        max_long_side=_positive_integer(input_payload, "maxLongSide"),
        box_threshold=_unit_ratio(input_payload, "boxThreshold"),
        text_threshold=_unit_ratio(input_payload, "textThreshold"),
        max_detections=_positive_integer(input_payload, "maxDetections"),
    )

    return ZeroShotModelManifest(
        schema_version=schema_version,
        model_id=_text(payload, "modelId"),
        model_name=_text(payload, "modelName"),
        version=_text(payload, "version"),
        status=status,
        identity_verified=identity_verified,
        task=task,
        adapter=adapter,
        weight_sha256=checkpoint.sha256,
        source=source,
        classes=classes,
        checkpoint=checkpoint,
        input=model_input,
        license=_text(payload, "license"),
        approved_by=approved_by,
        approved_at=approved_at,
    )


def _is_within(path: Path, root: Path) -> bool:
    try:
        path.relative_to(root)
        return True
    except ValueError:
        return False


def _relative_dir(payload: dict[str, Any], key: str, root: Path) -> Path:
    """解析模型根目录内的权重目录；不允许绝对路径或 .. 越界。"""

    value = Path(_text(payload, key))
    if value.is_absolute() or ".." in value.parts:
        raise ModelManifestError(f"字段 {key} 必须位于模型根目录内")
    resolved = (root / value).resolve()
    if not _is_within(resolved, root):
        raise ModelManifestError(f"字段 {key} 必须位于模型根目录内")
    return resolved


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


def _optional_text(payload: dict[str, Any], key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str):
        raise ModelManifestError(f"字段 {key} 必须是字符串")
    return value.strip()


def _optional_bool(payload: dict[str, Any], key: str, default: bool) -> bool:
    value = payload.get(key)
    if value is None:
        return default
    if not isinstance(value, bool):
        raise ModelManifestError(f"字段 {key} 必须是布尔值")
    return value


def _string_list(payload: dict[str, Any], key: str) -> tuple[str, ...]:
    value = payload.get(key)
    if not isinstance(value, list) or not value:
        raise ModelManifestError(f"字段 {key} 必须是非空字符串数组")
    result = []
    for item in value:
        if not isinstance(item, str) or not item.strip():
            raise ModelManifestError(f"字段 {key} 必须是非空字符串数组")
        result.append(item.strip())
    return tuple(result)


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


def _sha256_hex(value: Any) -> str:
    if not isinstance(value, str):
        raise ModelManifestError("checkpoint.sha256 必须是字符串")
    normalized = value.strip().lower()
    if len(normalized) != 64 or any(ch not in "0123456789abcdef" for ch in normalized):
        raise ModelManifestError("checkpoint.sha256 格式不合法")
    return normalized


def _fixed_sha(value: Any, field: str) -> str:
    """固定模型 revision：必须是 40 位 commit SHA，禁止 main/分支/短哈希。"""

    if not isinstance(value, str):
        raise ModelManifestError(f"checkpoint.{field} 必须是字符串")
    normalized = value.strip().lower()
    if not _FULL_SHA_PATTERN.match(normalized):
        raise ModelManifestError(
            f"checkpoint.{field} 必须为固定 40 位 commit SHA（禁止 main/分支/短哈希）"
        )
    return normalized
