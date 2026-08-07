"""内部推理接口测试。"""

from __future__ import annotations

import json

from app.config import get_settings
from tests.conftest import metadata_json, post_inference


def test_inference_accepts_jpeg(client, jpeg_bytes):
    response = post_inference(client, jpeg_bytes)

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "req-001"
    assert body["status"] == "SUCCEEDED"
    assert body["mode"] == "MOCK"
    assert body["model"]["modelId"] == "AI-DEFECT-MOCK-001"
    assert body["model"]["version"] == "0.1.0"
    assert body["image"]["width"] > 0
    assert body["image"]["height"] > 0
    assert "durationMs" in body
    assert "模拟结果仅用于业务链路验证" in body["warnings"]


def test_inference_accepts_png_and_webp(client, png_bytes, webp_bytes):
    """PNG 和 WebP 格式能被成功解码，允许任何确定性种子分支。"""
    for image_bytes in (png_bytes, webp_bytes):
        response = post_inference(client, image_bytes, request_id="req-png-webp")
        assert response.status_code == 200
        body = response.json()
        # 只要格式被正确解码，任何状态（含 REJECTED/NOT_APPLICABLE）均合法。
        assert "image" in body
        assert body["image"]["width"] > 0


def test_inference_returns_normalized_bounding_boxes(client, applicable_one_image):
    response = post_inference(client, applicable_one_image)

    assert response.status_code == 200
    body = response.json()
    assert body["image"]["applicability"] == "APPLICABLE"
    assert len(body["detections"]) == 1
    box = body["detections"][0]["boundingBox"]
    assert box["coordinateType"] == "NORMALIZED_XYWH"
    assert 0.0 <= box["x"] <= 1.0
    assert 0.0 <= box["y"] <= 1.0
    assert 0.0 < box["width"] <= 1.0
    assert 0.0 < box["height"] <= 1.0
    assert box["x"] + box["width"] <= 1.0
    assert box["y"] + box["height"] <= 1.0
    assert 0.0 <= body["detections"][0]["confidence"] <= 1.0
    assert body["summary"]["detectionCount"] == 1


def test_inference_two_detections(client, applicable_two_image):
    response = post_inference(client, applicable_two_image)

    assert response.status_code == 200
    body = response.json()
    assert body["image"]["applicability"] == "APPLICABLE"
    assert len(body["detections"]) == 2
    assert body["summary"]["detectionCount"] == 2


def test_inference_no_defect_found_is_not_safe(client, no_defect_image):
    response = post_inference(client, no_defect_image)

    assert response.status_code == 200
    body = response.json()
    assert body["image"]["applicability"] == "NO_DEFECT_FOUND"
    assert body["detections"] == []
    # NO_DEFECT_FOUND 不能在响应中表述为“安全”。
    serialized = json.dumps(body, ensure_ascii=False)
    assert "安全" not in serialized


def test_inference_not_applicable_is_rejected(client, not_applicable_image):
    response = post_inference(client, not_applicable_image)

    assert response.status_code == 200
    body = response.json()
    assert body["image"]["applicability"] == "NOT_APPLICABLE"
    assert body["status"] == "REJECTED"
    assert body["detections"] == []


def test_inference_low_quality_image(client, tiny_png):
    response = post_inference(client, tiny_png)

    assert response.status_code == 200
    body = response.json()
    assert body["image"]["applicability"] == "LOW_QUALITY"
    assert body["image"]["qualityStatus"] == "LOW_QUALITY"
    assert body["detections"] == []


def test_inference_empty_file(client):
    response = post_inference(client, b"")

    assert response.status_code == 400
    body = response.json()
    assert body["errorCode"] == "AI_IMAGE_EMPTY"
    assert body["status"] == "REJECTED"


def test_inference_corrupted_image(client, corrupted_bytes):
    response = post_inference(client, corrupted_bytes)

    assert response.status_code == 422
    body = response.json()
    assert body["errorCode"] == "AI_IMAGE_DECODE_FAILED"
    assert body["status"] == "REJECTED"


def test_inference_unsupported_format(client, gif_bytes):
    response = post_inference(client, gif_bytes)

    assert response.status_code == 415
    body = response.json()
    assert body["errorCode"] == "AI_IMAGE_UNSUPPORTED_FORMAT"


def test_inference_too_large(client):
    settings = get_settings()
    too_large = b"\x00" * (settings.max_image_size_bytes + 1)
    response = post_inference(client, too_large)

    assert response.status_code == 413
    assert response.json()["errorCode"] == "AI_IMAGE_TOO_LARGE"


def test_inference_real_mode_unavailable(client, jpeg_bytes):
    response = client.post(
        "/internal/api/v1/ai/inferences",
        files={"file": ("image.jpg", jpeg_bytes, "image/jpeg")},
        data={"metadata": metadata_json(mode="REAL")},
    )

    assert response.status_code == 503
    body = response.json()
    assert body["errorCode"] == "AI_MODEL_UNAVAILABLE"
    assert body["status"] == "FAILED"


def test_inference_invalid_metadata(client, jpeg_bytes):
    response = client.post(
        "/internal/api/v1/ai/inferences",
        files={"file": ("image.jpg", jpeg_bytes, "image/jpeg")},
        data={"metadata": "not-json"},
    )

    assert response.status_code == 400
    assert response.json()["errorCode"] == "AI_SERVICE_INVALID_RESPONSE"
