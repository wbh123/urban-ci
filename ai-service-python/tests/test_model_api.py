"""统一模型目录、精确模型查询和请求路由接口测试。"""

from __future__ import annotations

from tests.conftest import metadata_json


def test_model_catalog_and_readiness_use_unified_runtime(client):
    catalog = client.get("/internal/api/v1/ai/models")
    readiness = client.get("/internal/api/v1/ai/ready")

    assert catalog.status_code == 200
    assert catalog.json()["runtime"] == "CUDA_ONLY"
    assert catalog.json()["models"][0]["modelId"] == "AI-DEFECT-MOCK-001"
    assert readiness.status_code == 200
    assert readiness.json()["status"] == "READY"
    assert readiness.json()["realModelCount"] == 0


def test_model_lookup_returns_exact_runtime_identity(client):
    response = client.get("/internal/api/v1/ai/models/AI-DEFECT-MOCK-001")

    assert response.status_code == 200
    body = response.json()
    assert body["modelId"] == "AI-DEFECT-MOCK-001"
    assert body["ready"] is True
    assert body["executionProvider"] == "DETERMINISTIC_MOCK"


def test_inference_rejects_unknown_requested_model(client, jpeg_bytes):
    response = client.post(
        "/internal/api/v1/ai/inferences",
        files={"file": ("image.jpg", jpeg_bytes, "image/jpeg")},
        data={"metadata": metadata_json(mode="MOCK").replace(
            '"AI-DEFECT-MOCK-001"', '"AI-MISSING"'
        )},
    )

    assert response.status_code == 503
    assert response.json()["errorCode"] == "AI_MODEL_UNAVAILABLE"


def test_unknown_model_lookup_is_not_silently_replaced_by_mock(client):
    response = client.get("/internal/api/v1/ai/models/AI-MISSING")

    assert response.status_code == 503
    assert response.json()["errorCode"] == "AI_MODEL_UNAVAILABLE"
