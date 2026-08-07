"""UrbanSafe AI Service 运行时配置。

配置优先通过环境变量注入；本地直接运行时读取项目根目录 ``.env``。
真实模型只能来自本地运行时目录与显式模型目录清单，运行时不得访问公网。
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


class Settings:
    """进程级运行参数。真实模型统一采用 CUDA，不提供 CPU 推理配置。"""

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
        self.supported_content_types = ("image/jpeg", "image/png", "image/webp")


@lru_cache
def get_settings() -> Settings:
    """缓存配置，模型目录或环境变量变化后必须重启进程。"""

    return Settings()
