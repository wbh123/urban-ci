"""本地图片语义适用性门禁测试。"""

from __future__ import annotations

import io
import json

import numpy as np
import pytest
from PIL import Image
from pydantic import ValidationError

from app.applicability import (
    LOCAL_IMAGE_APPLICABILITY_MODEL_ID,
    OnnxImageApplicabilityProvider,
    UnavailableImageApplicabilityProvider,
    build_image_applicability_provider,
)
from app.config import Settings, get_settings
from app.main import _applicability_provider
from app.schemas import ImageApplicabilityDecision, ImageApplicabilityResponse


_TEST_MODEL_BYTES = b"test-model-placeholder"
_TEST_MODEL_SHA256 = "1e5097c118ce1baab233547aa23e4c28117a300afb04eaaf994fe359a6ba5c31"


class _FakeInput:
    name = "images"


class _FakeSession:
    def __init__(self, output: list[float]) -> None:
        self._output = np.asarray([output], dtype=np.float32)

    def get_inputs(self):
        return [_FakeInput()]

    def run(self, output_names, inputs):
        assert output_names is None
        tensor = inputs["images"]
        assert tensor.shape == (1, 3, 32, 32)
        return [self._output]


def _png_bytes() -> bytes:
    image = Image.new("RGB", (64, 64), (120, 140, 160))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def _settings(
    monkeypatch,
    tmp_path,
    *,
    model_exists: bool = True,
    weight_sha256: str = _TEST_MODEL_SHA256,
) -> Settings:
    model_path = tmp_path / "model.onnx"
    metadata_path = tmp_path / "model.json"
    if model_exists:
        model_path.write_bytes(_TEST_MODEL_BYTES)
    metadata_path.write_text(
        json.dumps(
            {
                "modelId": "LOCAL-IMAGE-APPLICABILITY-TEST",
                "modelVersion": "test-1",
                "weightSha256": weight_sha256,
                "inputSize": [32, 32],
                "classes": ["APPLICABLE", "NOT_APPLICABLE"],
                "mean": [0.0, 0.0, 0.0],
                "std": [1.0, 1.0, 1.0],
                "outputType": "LOGITS",
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_ENABLED", "true")
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_MODEL_PATH", str(model_path))
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_METADATA_PATH", str(metadata_path))
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_REJECT_THRESHOLD", "0.90")
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_APPLICABLE_THRESHOLD", "0.60")
    monkeypatch.setenv("URBAN_SAFE_AI_MAX_IMAGE_SIZE_BYTES", "10485760")
    return Settings()


def test_unavailable_provider_fails_open_after_validating_image(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path, model_exists=False)
    provider = UnavailableImageApplicabilityProvider(settings, "MODEL_FILE_MISSING")

    result = provider.classify(_png_bytes(), "app-001")

    assert result.requestId == "app-001"
    assert result.modelId == LOCAL_IMAGE_APPLICABILITY_MODEL_ID
    assert result.decision == ImageApplicabilityDecision.UNCERTAIN
    assert result.allowDify is True
    assert result.reason == "MODEL_FILE_MISSING"


def test_high_confidence_applicable_is_allowed(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path)
    provider = OnnxImageApplicabilityProvider(settings, session=_FakeSession([4.0, 0.0]))

    result = provider.classify(_png_bytes(), "app-002")

    assert result.decision == ImageApplicabilityDecision.APPLICABLE
    assert result.allowDify is True
    assert result.confidence > 0.95
    assert result.reason == "HIGH_CONFIDENCE_APPLICABLE"


def test_high_confidence_not_applicable_is_rejected(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path)
    provider = OnnxImageApplicabilityProvider(settings, session=_FakeSession([0.0, 4.0]))

    result = provider.classify(_png_bytes(), "app-003")

    assert result.decision == ImageApplicabilityDecision.NOT_APPLICABLE
    assert result.allowDify is False
    assert result.confidence > 0.95
    assert result.reason == "HIGH_CONFIDENCE_NOT_APPLICABLE"


def test_low_confidence_result_is_uncertain_and_allowed(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path)
    provider = OnnxImageApplicabilityProvider(settings, session=_FakeSession([0.0, 0.0]))

    result = provider.classify(_png_bytes(), "app-004")

    assert result.decision == ImageApplicabilityDecision.UNCERTAIN
    assert result.allowDify is True
    assert result.confidence == 0.5
    assert result.reason == "LOW_CONFIDENCE"


def test_invalid_model_output_fails_open(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path)
    provider = OnnxImageApplicabilityProvider(settings, session=_FakeSession([1.0, 2.0, 3.0]))

    result = provider.classify(_png_bytes(), "app-005")

    assert result.decision == ImageApplicabilityDecision.UNCERTAIN
    assert result.allowDify is True
    assert result.reason == "INVALID_MODEL_OUTPUT"


def test_digest_mismatch_falls_back_to_unavailable_provider(monkeypatch, tmp_path):
    settings = _settings(monkeypatch, tmp_path, weight_sha256="0" * 64)

    provider = build_image_applicability_provider(settings)
    result = provider.classify(_png_bytes(), "app-sha")

    assert isinstance(provider, UnavailableImageApplicabilityProvider)
    assert result.decision == ImageApplicabilityDecision.UNCERTAIN
    assert result.allowDify is True
    assert result.reason == "MODEL_LOAD_FAILED"


def test_response_schema_rejects_contradictory_gate_decision():
    with pytest.raises(ValidationError):
        ImageApplicabilityResponse(
            requestId="schema-1",
            modelId="LOCAL-IMAGE-APPLICABILITY-001",
            modelVersion="1.0.0",
            decision=ImageApplicabilityDecision.NOT_APPLICABLE,
            confidence=0.99,
            scores={"NOT_APPLICABLE": 0.99, "APPLICABLE": 0.01},
            allowDify=True,
            reason="BROKEN",
        )


def test_settings_reject_inverted_thresholds(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_REJECT_THRESHOLD", "0.50")
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_APPLICABLE_THRESHOLD", "0.60")

    with pytest.raises(ValueError):
        Settings()


def test_applicability_endpoint_fails_open_when_model_disabled(client, png_bytes):
    response = client.post(
        "/internal/api/v1/ai/image-applicability",
        files={"file": ("inspection.png", png_bytes, "image/png")},
        data={"requestId": "app-endpoint-1"},
    )

    assert response.status_code == 200
    body = response.json()
    assert body["requestId"] == "app-endpoint-1"
    assert body["decision"] == "UNCERTAIN"
    assert body["allowDify"] is True
    assert body["reason"] == "DISABLED"


def test_applicability_endpoint_rejects_corrupted_image(client, corrupted_bytes):
    response = client.post(
        "/internal/api/v1/ai/image-applicability",
        files={"file": ("broken.jpg", corrupted_bytes, "image/jpeg")},
        data={"requestId": "app-broken"},
    )

    assert response.status_code == 422
    body = response.json()
    assert body["requestId"] == "app-broken"
    assert body["errorCode"] == "AI_IMAGE_DECODE_FAILED"


def test_applicability_provider_cache_can_be_reset(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_APPLICABILITY_ENABLED", "false")
    get_settings.cache_clear()
    _applicability_provider.cache_clear()

    first = _applicability_provider()
    second = _applicability_provider()

    assert first is second
    _applicability_provider.cache_clear()
    get_settings.cache_clear()
