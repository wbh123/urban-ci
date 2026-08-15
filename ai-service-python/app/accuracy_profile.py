"""ACCURACY 多模型运行时 Profile 清单。"""
from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path

from .errors import ModelUnavailableError

ACCURACY_PROFILE_ID = "AI-VISION-ACCURACY-001"
ACCURACY_PROFILE_VERSION = "1.0.0"
ACCURACY_PIPELINE_VERSION = "ACCURACY-CANDIDATE-002"
DEFAULT_PROFILE_RELATIVE_PATH = Path(ACCURACY_PROFILE_ID) / ACCURACY_PROFILE_VERSION / "profile.json"


@dataclass(frozen=True)
class AccuracyProfile:
    profile_id: str
    version: str
    status: str
    pipeline_version: str
    base_model_id: str
    base_model_version: str
    qwen_path: Path
    qwen_sha256: str
    florence_path: Path
    florence_sha256: str
    qwen_max_side: int
    qwen_max_new_tokens: int
    benchmark_details_path: Path
    benchmark_sha256: str
    approved_by: str | None = None
    approved_at: str | None = None

    @property
    def approved(self) -> bool:
        return self.status == "APPROVED"


def _text(payload: dict, key: str) -> str:
    value = payload.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ModelUnavailableError("ACCURACY Profile 字段无效: " + key)
    return value.strip()


def _inside(root: Path, value: str) -> Path:
    relative = Path(value)
    if relative.is_absolute() or ".." in relative.parts:
        raise ModelUnavailableError("ACCURACY Profile 路径必须位于模型根目录内")
    result = (root / relative).resolve()
    try:
        result.relative_to(root)
    except ValueError as ex:
        raise ModelUnavailableError("ACCURACY Profile 路径越界") from ex
    return result


def load_accuracy_profile(path: Path, model_root: Path, *, require_approved: bool = False) -> AccuracyProfile:
    root = Path(model_root).resolve()
    profile_path = Path(path).resolve()
    try:
        profile_path.relative_to(root)
    except ValueError as ex:
        raise ModelUnavailableError("ACCURACY Profile 清单必须位于模型根目录内") from ex
    try:
        payload = json.loads(profile_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ModelUnavailableError("ACCURACY Profile 读取失败") from ex
    if payload.get("schemaVersion") != 1:
        raise ModelUnavailableError("ACCURACY Profile 仅支持 schemaVersion=1")
    profile_id = _text(payload, "profileId")
    version = _text(payload, "version")
    status = _text(payload, "status").upper()
    pipeline = _text(payload, "pipelineVersion")
    if (profile_id, version, pipeline) != (
        ACCURACY_PROFILE_ID, ACCURACY_PROFILE_VERSION, ACCURACY_PIPELINE_VERSION
    ):
        raise ModelUnavailableError("ACCURACY Profile 身份与冻结版本不一致")
    if status not in {"CANDIDATE", "APPROVED"}:
        raise ModelUnavailableError("ACCURACY Profile status 无效")
    if require_approved and status != "APPROVED":
        raise ModelUnavailableError("ACCURACY Profile 尚未 APPROVED")
    base = payload["baseModel"]
    qwen = payload["qwen"]
    florence = payload["florence"]
    inference = payload["inference"]
    benchmark = payload["benchmark"]
    if qwen.get("repo") != "Qwen/Qwen3-VL-2B-Instruct" or qwen.get("license") != "Apache-2.0":
        raise ModelUnavailableError("Qwen3-VL 来源或许可证不匹配")
    if florence.get("repo") != "florence-community/Florence-2-large-ft" or florence.get("license") != "MIT":
        raise ModelUnavailableError("Florence-2 来源或许可证不匹配")
    max_side = int(inference["qwenMaxSide"])
    max_tokens = int(inference["qwenMaxNewTokens"])
    if max_side != 1024 or max_tokens != 128:
        raise ModelUnavailableError("ACCURACY 正式推理参数必须固定为 1024/128")
    profile = AccuracyProfile(
        profile_id, version, status, pipeline,
        _text(base, "modelId"), _text(base, "version"),
        _inside(root, _text(qwen, "path")), _text(qwen, "sha256").lower(),
        _inside(root, _text(florence, "path")), _text(florence, "sha256").lower(),
        max_side, max_tokens,
        _inside(root, _text(benchmark, "detailsPath")), _text(benchmark, "sha256").lower(),
        str(payload.get("approvedBy") or "").strip() or None,
        str(payload.get("approvedAt") or "").strip() or None,
    )
    if require_approved and (not profile.approved_by or not profile.approved_at):
        raise ModelUnavailableError("APPROVED ACCURACY Profile 缺少人工审批记录")
    return profile
