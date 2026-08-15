"""UrbanSafe AI Service 运行时配置。

配置优先通过环境变量注入；本地直接运行时读取项目根目录 ``.env``。
真实病害模型只能来自本地运行时目录与显式模型目录清单，运行时不得访问公网。
本地图片语义适用性门禁属于辅助预检能力，允许使用 ONNX Runtime CPU，不改变真实病害模型 CUDA-only 边界。
"""

from __future__ import annotations

import os
from functools import lru_cache
from pathlib import Path

from dotenv import load_dotenv


PROJECT_ROOT = Path(__file__).resolve().parents[2]
load_dotenv(PROJECT_ROOT / ".env", override=False)


def _env(primary: str, project_name: str, default: str = "") -> str:
    """优先读取服务短变量，并兼容项目统一前缀变量。"""

    return os.getenv(primary, os.getenv(project_name, default))


def _project_path(value: str) -> Path:
    """把相对路径稳定解析为项目根目录路径。"""

    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _non_negative_int(primary: str, project_name: str, default: int) -> int:
    raw_value = _env(primary, project_name, str(default)).strip()
    try:
        value = int(raw_value)
    except ValueError as ex:
        raise ValueError(f"环境变量 {project_name} 必须是整数") from ex
    if value < 0:
        raise ValueError(f"环境变量 {project_name} 不能小于 0")
    return value


def _boolean(primary: str, project_name: str, default: bool) -> bool:
    raw_value = _env(primary, project_name, "true" if default else "false").strip().lower()
    if raw_value in {"1", "true", "yes", "on"}:
        return True
    if raw_value in {"0", "false", "no", "off"}:
        return False
    raise ValueError(f"环境变量 {project_name} 必须是布尔值")


def _unit_float(primary: str, project_name: str, default: float) -> float:
    raw_value = _env(primary, project_name, str(default)).strip()
    try:
        value = float(raw_value)
    except ValueError as ex:
        raise ValueError(f"环境变量 {project_name} 必须是 0 到 1 之间的小数") from ex
    if value < 0.0 or value > 1.0:
        raise ValueError(f"环境变量 {project_name} 必须位于 0 到 1 之间")
    return value


class Settings:
    """进程级运行参数；病害真实模型 CUDA-only，辅助语义门禁允许 CPU ONNX。"""

    def __init__(self) -> None:
        self.service_name: str = _env(
            "AI_SERVICE_NAME", "URBAN_SAFE_AI_SERVICE_NAME", "urban-safe-ai-service"
        )
        self.mock_model_id: str = _env(
            "AI_MOCK_MODEL_ID",
            "URBAN_SAFE_AI_DEFAULT_MOCK_MODEL_ID",
            "AI-DEFECT-MOCK-001",
        )
        self.mock_model_name: str = _env(
            "AI_MOCK_MODEL_NAME",
            "URBAN_SAFE_AI_MOCK_MODEL_NAME",
            "UrbanSafe Deterministic Mock Detector",
        )
        self.mock_model_version: str = _env(
            "AI_MOCK_MODEL_VERSION",
            "URBAN_SAFE_AI_MOCK_MODEL_VERSION",
            "0.1.0",
        )
        self.model_root: Path = _project_path(
            _env(
                "AI_MODEL_ROOT",
                "URBAN_SAFE_AI_MODEL_ROOT",
                "data/ai-service/models",
            )
        )
        self.model_catalog_path: Path = Path(
            _env(
                "AI_MODEL_CATALOG_PATH",
                "URBAN_SAFE_AI_MODEL_CATALOG_PATH",
                "runtime-catalog.json",
            ).strip()
            or "runtime-catalog.json"
        ).expanduser()
        self.cuda_device_id: int = _non_negative_int(
            "AI_CUDA_DEVICE_ID", "URBAN_SAFE_AI_CUDA_DEVICE_ID", 0
        )

        # 只用于把旧版单模型安装平滑迁移到运行时目录；新安装不再写这两个变量。
        self.legacy_real_model_status: str = _env(
            "AI_REAL_MODEL_STATUS",
            "URBAN_SAFE_AI_REAL_MODEL_STATUS",
            "UNAVAILABLE",
        ).strip().upper()
        legacy_manifest_value = _env(
            "AI_REAL_MODEL_MANIFEST_PATH",
            "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH",
            "",
        ).strip()
        self.legacy_real_model_manifest_path: Path | None = (
            Path(legacy_manifest_value).expanduser() if legacy_manifest_value else None
        )

        self.max_image_size_bytes: int = int(
            _env(
                "AI_MAX_IMAGE_SIZE_BYTES",
                "URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES",
                str(10 * 1024 * 1024),
            )
        )
        self.low_quality_min_side: int = int(
            _env(
                "AI_LOW_QUALITY_MIN_SIDE",
                "URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE",
                "32",
            )
        )

        # 本地语义适用性门禁只负责“是否适合进入建筑病害分析”，不判断病害类别。
        self.applicability_enabled: bool = _boolean(
            "AI_APPLICABILITY_ENABLED",
            "URBAN_SAFE_AI_APPLICABILITY_ENABLED",
            True,
        )
        self.applicability_model_id: str = _env(
            "AI_APPLICABILITY_MODEL_ID",
            "URBAN_SAFE_AI_APPLICABILITY_MODEL_ID",
            "LOCAL-IMAGE-APPLICABILITY-001",
        ).strip() or "LOCAL-IMAGE-APPLICABILITY-001"
        self.applicability_model_version: str = _env(
            "AI_APPLICABILITY_MODEL_VERSION",
            "URBAN_SAFE_AI_APPLICABILITY_MODEL_VERSION",
            "1.0.0",
        ).strip() or "1.0.0"
        self.applicability_model_path: Path = _project_path(
            _env(
                "AI_APPLICABILITY_MODEL_PATH",
                "URBAN_SAFE_AI_APPLICABILITY_MODEL_PATH",
                "data/ai-service/models/image-applicability/model.onnx",
            )
        )
        self.applicability_metadata_path: Path = _project_path(
            _env(
                "AI_APPLICABILITY_METADATA_PATH",
                "URBAN_SAFE_AI_APPLICABILITY_METADATA_PATH",
                "data/ai-service/models/image-applicability/model.json",
            )
        )
        self.applicability_reject_threshold: float = _unit_float(
            "AI_APPLICABILITY_REJECT_THRESHOLD",
            "URBAN_SAFE_AI_APPLICABILITY_REJECT_THRESHOLD",
            0.90,
        )
        self.applicability_applicable_threshold: float = _unit_float(
            "AI_APPLICABILITY_APPLICABLE_THRESHOLD",
            "URBAN_SAFE_AI_APPLICABILITY_APPLICABLE_THRESHOLD",
            0.60,
        )
        if self.applicability_reject_threshold < self.applicability_applicable_threshold:
            raise ValueError(
                "环境变量 URBAN_SAFE_AI_APPLICABILITY_REJECT_THRESHOLD "
                "不能小于 URBAN_SAFE_AI_APPLICABILITY_APPLICABLE_THRESHOLD"
            )

        # 零样本本地视觉模型（AI-VISION-LOCAL-001，Grounding DINO + SAM 2.1 Tiny）。
        # 真实视觉模型只允许 CUDA；CPU 开发机通过不同 catalog 使用 MOCK，不加载本模型。
        self.vision_device: str = _env(
            "AI_VISION_DEVICE", "URBAN_SAFE_AI_VISUAL_DEVICE", "cuda"
        ).strip().lower() or "cuda"
        self.vision_dtype: str = _env(
            "AI_VISION_DTYPE", "URBAN_SAFE_AI_VISION_DTYPE", "float16"
        ).strip().lower() or "float16"
        self.vision_max_long_side: int = int(
            _env(
                "AI_VISION_MAX_LONG_SIDE",
                "URBAN_SAFE_AI_VISION_MAX_LONG_SIDE",
                "1280",
            )
        )
        self.vision_box_threshold: float = _unit_float(
            "AI_VISION_BOX_THRESHOLD",
            "URBAN_SAFE_AI_VISION_BOX_THRESHOLD",
            0.25,
        )
        self.vision_text_threshold: float = _unit_float(
            "AI_VISION_TEXT_THRESHOLD",
            "URBAN_SAFE_AI_VISION_TEXT_THRESHOLD",
            0.25,
        )
        self.vision_max_detections: int = int(
            _env(
                "AI_VISION_MAX_DETECTIONS",
                "URBAN_SAFE_AI_VISION_MAX_DETECTIONS",
                "10",
            )
        )
        self.vision_offline: bool = _boolean(
            "AI_VISION_OFFLINE",
            "URBAN_SAFE_AI_VISION_OFFLINE",
            True,
        )
        self.vision_hf_home: str = _env(
            "AI_VISION_HF_HOME",
            "URBAN_SAFE_AI_VISION_HF_HOME",
            "data/model-cache/huggingface",
        ).strip() or "data/model-cache/huggingface"

        # ACCURACY 多模型档位当前仍受模型治理门禁约束。只有完成独立评估、资源安装与
        # APPROVED 审批后才允许 FastAPI 正式执行；默认 CANDIDATE 不得绕过此门禁。
        accuracy_profile_status = _env(
            "AI_ACCURACY_PROFILE_STATUS",
            "URBAN_SAFE_AI_ACCURACY_PROFILE_STATUS",
            "CANDIDATE",
        ).strip().upper()
        if accuracy_profile_status not in {"CANDIDATE", "APPROVED"}:
            raise ValueError(
                "环境变量 URBAN_SAFE_AI_ACCURACY_PROFILE_STATUS 必须为 CANDIDATE 或 APPROVED"
            )
        self.accuracy_profile_status: str = accuracy_profile_status

        # GPU 并发保护：RTX 3060 6GB 同一进程同一 GPU 最多一个视觉推理。
        self.vision_max_concurrency: int = int(
            _env(
                "AI_VISION_MAX_CONCURRENCY",
                "URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY",
                "1",
            )
        )
        if self.vision_max_concurrency < 1:
            raise ValueError(
                "环境变量 URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY 必须 >= 1"
            )
        # SHA 门禁模式：STRICT（默认，启动时重算权重摘要与 manifest 对比）或
        # FAST（只校验 manifest 格式，不重算摘要）。比赛默认必须 STRICT。
        sha_mode = _env(
            "AI_VISION_SHA_MODE",
            "URBAN_SAFE_AI_VISION_SHA_MODE",
            "STRICT",
        ).strip().upper()
        if sha_mode not in {"STRICT", "FAST"}:
            raise ValueError(
                "环境变量 URBAN_SAFE_AI_VISION_SHA_MODE 必须为 STRICT 或 FAST"
            )
        self.vision_sha_mode: str = sha_mode
        # 运行时视觉模型不得联网下载权重：强制 huggingface_hub 离线读取本地缓存。
        if self.vision_offline:
            os.environ.setdefault("HF_HUB_OFFLINE", "1")

        self.supported_content_types = ("image/jpeg", "image/png", "image/webp")


@lru_cache
def get_settings() -> Settings:
    """缓存配置，模型目录或环境变量变化后必须重启进程。"""

    return Settings()
