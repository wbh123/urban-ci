"""CUDA-only 多模型运行时配置和进程级缓存测试。"""

import pytest

from app.config import PROJECT_ROOT, Settings
from app.main import _orchestrator


def test_relative_model_root_is_resolved_from_project_root(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_ROOT", "data/ai-service/models")
    monkeypatch.delenv("AI_MODEL_ROOT", raising=False)

    settings = Settings()

    assert settings.model_root == (PROJECT_ROOT / "data/ai-service/models").resolve()
    assert settings.model_root.is_absolute()


def test_catalog_and_cuda_device_are_read_from_project_env(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    monkeypatch.setenv("URBAN_SAFE_AI_CUDA_DEVICE_ID", "2")
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)
    monkeypatch.delenv("AI_CUDA_DEVICE_ID", raising=False)

    settings = Settings()

    assert str(settings.model_catalog_path) == "runtime-catalog.json"
    assert settings.cuda_device_id == 2
    assert not hasattr(settings, "onnx_execution_providers")
    assert not hasattr(settings, "onnx_require_gpu")


def test_negative_cuda_device_is_rejected(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_CUDA_DEVICE_ID", "-1")
    monkeypatch.delenv("AI_CUDA_DEVICE_ID", raising=False)

    with pytest.raises(ValueError, match="不能小于 0"):
        Settings()


def test_orchestrator_is_cached_per_process(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "UNAVAILABLE")
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "missing-catalog.json")
    _orchestrator.cache_clear()
    first = _orchestrator()
    second = _orchestrator()

    assert first is second
    _orchestrator.cache_clear()
