"""GPU 并发保护测试：max_concurrency=1 信号量 + to_thread 串行化视觉推理。"""

from __future__ import annotations

import asyncio
import io
import json

import httpx
import pytest
from PIL import Image

from app.config import get_settings
from app.main import _gpu_semaphore, _orchestrator, app


def _image_bytes() -> bytes:
    buffer = io.BytesIO()
    Image.new("RGB", (32, 32), (128, 128, 128)).save(buffer, "PNG")
    return buffer.getvalue()


def _metadata(request_id: str) -> str:
    return json.dumps(
        {
            "requestId": request_id,
            "mode": "MOCK",
            "requestedModelId": "AI-DEFECT-MOCK-001",
        }
    )


def _reset_mock_env(monkeypatch) -> None:
    monkeypatch.setenv("URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY", "1")
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "missing-catalog.json")
    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "UNAVAILABLE")
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_ENABLED", "false")
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "4096")
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)
    monkeypatch.delenv("AI_REAL_MODEL_STATUS", raising=False)
    monkeypatch.delenv("AI_APPLICABILITY_ENABLED", raising=False)
    monkeypatch.delenv("AI_MAX_IMAGE_SIZE_BYTES", raising=False)
    get_settings.cache_clear()
    _orchestrator.cache_clear()
    monkeypatch.setattr("app.main._GPU_SEMAPHORE", None)


def test_gpu_semaphore_value_is_one(monkeypatch):
    _reset_mock_env(monkeypatch)

    async def check():
        return _gpu_semaphore()._value

    assert asyncio.run(check()) == 1


def test_inference_endpoint_serializes_under_semaphore(monkeypatch):
    _reset_mock_env(monkeypatch)

    state = {"current": 0, "max": 0}
    real_run = _orchestrator().run

    def slow_run(*args, **kwargs):
        state["current"] += 1
        state["max"] = max(state["max"], state["current"])
        import time

        time.sleep(0.05)
        try:
            return real_run(*args, **kwargs)
        finally:
            state["current"] -= 1

    monkeypatch.setattr(_orchestrator(), "run", slow_run)

    async def fire(index: int, transport: httpx.ASGITransport):
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            response = await client.post(
                "/internal/api/v1/ai/inferences",
                files={"file": ("image.png", _image_bytes(), "image/png")},
                data={"metadata": _metadata(f"conc-{index}")},
            )
            return response.status_code

    async def run_all():
        transport = httpx.ASGITransport(app=app)
        # 在同一事件循环创建信号量，保证与请求共享同一绑定循环。
        _gpu_semaphore()
        codes = await asyncio.gather(*(fire(i, transport) for i in range(4)))
        return codes

    codes = asyncio.run(run_all())
    assert all(code == 200 for code in codes)
    # 并发到达但 GPU 推理（模拟的 slow_run）最大并发必须是 1。
    assert state["max"] == 1


def test_concurrency_config_rejects_zero(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_VISUAL_MAX_CONCURRENCY", "0")
    get_settings.cache_clear()
    with pytest.raises(ValueError, match="MAX_CONCURRENCY"):
        get_settings()
