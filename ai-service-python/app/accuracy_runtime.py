"""正式 ACCURACY 单图运行器：按阶段切换显存并在结束后恢复基础视觉模型。"""
from __future__ import annotations

import gc
import hashlib
import io
import os
from pathlib import Path
from typing import Callable

from PIL import Image

from .accuracy_inference import AccuracyBatchRunner, _release_grounded
from .accuracy_profile import DEFAULT_PROFILE_RELATIVE_PATH, AccuracyProfile, load_accuracy_profile
from .adapters.florence2_locator import Florence2Locator
from .adapters.qwen3_vl_classifier import Qwen3VlClassifier
from .config import PROJECT_ROOT
from .errors import ModelUnavailableError
from .model_digest import dir_digest
from .schemas import Applicability, DetectionItem


def _file_digest(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _verify_profile(profile: AccuracyProfile) -> None:
    if not profile.qwen_path.is_dir() or not profile.florence_path.is_dir():
        raise ModelUnavailableError("ACCURACY 辅助模型目录不存在")
    qwen_sha, _ = dir_digest(profile.qwen_path)
    florence_sha, _ = dir_digest(profile.florence_path)
    if qwen_sha != profile.qwen_sha256 or florence_sha != profile.florence_sha256:
        raise ModelUnavailableError("ACCURACY 辅助模型摘要与批准清单不一致")
    if not profile.benchmark_details_path.is_file():
        raise ModelUnavailableError("ACCURACY 冻结 benchmark 不存在")
    if _file_digest(profile.benchmark_details_path) != profile.benchmark_sha256:
        raise ModelUnavailableError("ACCURACY 冻结 benchmark 摘要不一致")


def _base_loaded(adapter) -> bool:
    return all(getattr(adapter, name, None) is not None for name in (
        "_dino", "_dino_processor", "_sam", "_sam_processor"
    ))


def _restore_base(adapter):
    if _base_loaded(adapter):
        return adapter
    loader = getattr(adapter, "_load_models", None)
    if loader is None:
        raise ModelUnavailableError("当前基础视觉模型不支持 ACCURACY 显存切换")
    loader()
    return adapter


def _release_base(adapter) -> None:
    _release_grounded(adapter)
    gc.collect()
    torch = getattr(adapter, "_torch", None)
    if torch is not None and torch.cuda.is_available():
        torch.cuda.empty_cache()


def _accuracy_model_root(settings) -> Path:
    """解析 ACCURACY 独立正式模型根目录。

    基础 AI-VISION-LOCAL-001 在比赛机 Profile B 中仍可由 data/model-cache 的
    runtime-catalog 激活；Qwen/Florence 与批准清单则固定安装在正式模型目录，
    两者不能再错误共用同一个 model_root。
    """

    configured = getattr(settings, "accuracy_model_root", None)
    if configured is not None:
        return Path(configured).expanduser().resolve()
    raw = os.getenv(
        "AI_ACCURACY_MODEL_ROOT",
        os.getenv("URBAN_SAFE_AI_ACCURACY_MODEL_ROOT", "data/ai-service/models"),
    ).strip() or "data/ai-service/models"
    path = Path(raw).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


class AccuracyRuntimeRunner:
    approved = True

    def __init__(
        self,
        profile: AccuracyProfile,
        *,
        batch_runner_factory: Callable[..., object] = AccuracyBatchRunner,
    ) -> None:
        if not profile.approved:
            raise ModelUnavailableError("ACCURACY Profile 尚未 APPROVED")
        self.profile = profile
        self.batch_runner_factory = batch_runner_factory

    def __call__(self, base_adapter, decoded):
        info = base_adapter.model_info()
        if info.modelId != self.profile.base_model_id or info.version != self.profile.base_model_version:
            raise ModelUnavailableError(
                "ACCURACY Profile 与当前基础视觉模型身份不一致："
                f"期望 {self.profile.base_model_id} v{self.profile.base_model_version}，"
                f"实际 {info.modelId} v{info.version}"
            )
        if decoded.applicability == Applicability.LOW_QUALITY:
            return Applicability.LOW_QUALITY, []
        try:
            image = Image.open(io.BytesIO(decoded.bytes_)).convert("RGB")
        except Exception as ex:
            raise ModelUnavailableError("ACCURACY 图片解码失败") from ex

        _release_base(base_adapter)
        try:
            runner = self.batch_runner_factory(
                qwen_factory=lambda: Qwen3VlClassifier(
                    self.profile.qwen_path,
                    device="cuda",
                    max_side=self.profile.qwen_max_side,
                    max_new_tokens=self.profile.qwen_max_new_tokens,
                ),
                grounded_factory=lambda: _restore_base(base_adapter),
                florence_factory=lambda: Florence2Locator(self.profile.florence_path, device="cuda"),
            )
            rows = runner.run_batch([image])
            payloads = rows[0] if rows else []
            detections: list[DetectionItem] = []
            for payload in payloads:
                item = dict(payload)
                diagnostics = dict(item.get("diagnostics") or {})
                diagnostics["accuracyExperimental"] = False
                diagnostics["accuracyProfileId"] = self.profile.profile_id
                diagnostics["accuracyProfileVersion"] = self.profile.version
                diagnostics["pipelineVersion"] = self.profile.pipeline_version
                item["diagnostics"] = diagnostics
                detections.append(DetectionItem.model_validate(item))
            if not detections:
                return Applicability.NO_DEFECT_FOUND, []
            return Applicability.APPLICABLE, detections
        finally:
            try:
                _restore_base(base_adapter)
            except Exception as ex:
                raise ModelUnavailableError("ACCURACY 结束后基础视觉模型恢复失败，请重启服务") from ex


def build_accuracy_runtime_runner(settings):
    accuracy_root = _accuracy_model_root(settings)
    profile_path = accuracy_root / DEFAULT_PROFILE_RELATIVE_PATH
    if not profile_path.is_file():
        return None
    profile = load_accuracy_profile(profile_path, accuracy_root, require_approved=False)
    if not profile.approved:
        return None
    approved = load_accuracy_profile(profile_path, accuracy_root, require_approved=True)
    if settings.vision_sha_mode == "STRICT":
        _verify_profile(approved)
    return AccuracyRuntimeRunner(approved)