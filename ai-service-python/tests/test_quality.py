"""本地图片质量能力测试。"""

from __future__ import annotations

import io

from PIL import Image, ImageDraw

from app.config import get_settings
from app.quality import LOCAL_IMAGE_QUALITY_MODEL_ID, analyze_image_quality


def _png_bytes(image: Image.Image) -> bytes:
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def _checkerboard(size: int = 256, cell: int = 16) -> bytes:
    image = Image.new("RGB", (size, size), "white")
    draw = ImageDraw.Draw(image)
    for y in range(0, size, cell):
        for x in range(0, size, cell):
            if (x // cell + y // cell) % 2 == 0:
                draw.rectangle((x, y, x + cell - 1, y + cell - 1), fill="black")
    return _png_bytes(image)


def test_checkerboard_is_usable_and_deterministic(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "10485760")
    monkeypatch.setenv("URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE", "32")
    get_settings.cache_clear()
    data = _checkerboard()

    first = analyze_image_quality(data, get_settings())
    second = analyze_image_quality(data, get_settings())

    assert first == second
    assert first.modelId == LOCAL_IMAGE_QUALITY_MODEL_ID
    assert first.decodeStatus == "DECODED"
    assert first.width == 256
    assert first.height == 256
    assert first.blank is False
    assert first.blurDetected is False
    assert first.reshootRecommended is False
    assert first.reasons == []


def test_uniform_dark_image_requires_reshoot(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "10485760")
    monkeypatch.setenv("URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE", "32")
    get_settings.cache_clear()
    data = _png_bytes(Image.new("RGB", (256, 256), (2, 2, 2)))

    result = analyze_image_quality(data, get_settings())

    assert result.blank is True
    assert result.underexposed is True
    assert result.blurDetected is True
    assert result.lowQuality is True
    assert result.reshootRecommended is True
    assert "IMAGE_BLANK_OR_LOW_CONTRAST" in result.reasons
    assert "IMAGE_UNDEREXPOSED" in result.reasons


def test_small_image_is_low_quality(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "10485760")
    monkeypatch.setenv("URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE", "64")
    get_settings.cache_clear()
    data = _checkerboard(size=32, cell=4)

    result = analyze_image_quality(data, get_settings())

    assert result.lowResolution is True
    assert result.lowQuality is True
    assert result.reshootRecommended is True
    assert "IMAGE_RESOLUTION_TOO_LOW" in result.reasons


def test_quality_endpoint_returns_stable_contract(client):
    response = client.post(
        "/internal/api/v1/ai/image-quality",
        files={"file": ("inspection.png", _checkerboard(), "image/png")},
        data={"requestId": "quality-001"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "quality-001"
    assert body["modelId"] == LOCAL_IMAGE_QUALITY_MODEL_ID
    assert body["modelVersion"] == "0.1.0"
    assert body["decodeStatus"] == "DECODED"
    assert isinstance(body["reasons"], list)


def test_quality_endpoint_rejects_corrupted_image(client, corrupted_bytes):
    response = client.post(
        "/internal/api/v1/ai/image-quality",
        files={"file": ("broken.jpg", corrupted_bytes, "image/jpeg")},
        data={"requestId": "quality-broken"},
    )

    assert response.status_code == 422
    body = response.json()
    assert body["requestId"] == "quality-broken"
    assert body["errorCode"] == "AI_IMAGE_DECODE_FAILED"
