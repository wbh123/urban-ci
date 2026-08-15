from __future__ import annotations

import io

import pytest
from PIL import Image

from app.config import Settings
from app.errors import ModelUnavailableError
from app.inference import InferenceOrchestrator
from app.schemas import Applicability, InferenceMetadata, InferenceMode, InferenceProfile, ModelBrief


class FakeAdapter:
    def __init__(self):
        self.predict_calls = 0

    def predict(self, decoded):
        self.predict_calls += 1
        return Applicability.APPLICABLE, []

    def model_info(self):
        return ModelBrief(modelId="AI-VISION-LOCAL-001", modelName="Fake Vision", version="1.1.0")


class FakeRegistry:
    def __init__(self, adapter):
        self.adapter = adapter

    def resolve(self, mode, requested_model_id=None):
        return self.adapter


class ApprovedRunner:
    approved = True

    def __init__(self):
        self.calls = 0

    def __call__(self, adapter, decoded):
        self.calls += 1
        return Applicability.APPLICABLE, []


def image_bytes():
    out = io.BytesIO()
    Image.new("RGB", (64, 64)).save(out, format="JPEG")
    return out.getvalue()


def test_metadata_accepts_accuracy_profile():
    item = InferenceMetadata.model_validate({
        "requestId": "acc-1", "mode": "MOCK", "inferenceProfile": "ACCURACY"
    })
    assert item.inferenceProfile == InferenceProfile.ACCURACY


def test_real_accuracy_without_approved_runner_is_rejected():
    adapter = FakeAdapter()
    orchestrator = InferenceOrchestrator(Settings(), registry=FakeRegistry(adapter))
    with pytest.raises(ModelUnavailableError, match="ACCURACY"):
        orchestrator.run(
            "acc-2", InferenceMode.REAL, image_bytes(),
            "AI-VISION-LOCAL-001", InferenceProfile.ACCURACY,
        )
    assert adapter.predict_calls == 0


def test_real_accuracy_rejects_unapproved_callable():
    adapter = FakeAdapter()
    called = []
    orchestrator = InferenceOrchestrator(
        Settings(), registry=FakeRegistry(adapter),
        accuracy_runner=lambda *_: called.append(True),
    )
    with pytest.raises(ModelUnavailableError, match="ACCURACY"):
        orchestrator.run(
            "acc-3", InferenceMode.REAL, image_bytes(),
            "AI-VISION-LOCAL-001", InferenceProfile.ACCURACY,
        )
    assert called == []


def test_real_accuracy_uses_approved_runner_without_fast_predict():
    adapter = FakeAdapter()
    runner = ApprovedRunner()
    orchestrator = InferenceOrchestrator(
        Settings(), registry=FakeRegistry(adapter), accuracy_runner=runner,
    )
    response = orchestrator.run(
        "acc-4", InferenceMode.REAL, image_bytes(),
        "AI-VISION-LOCAL-001", InferenceProfile.ACCURACY,
    )
    assert response.summary.detectionCount == 0
    assert any("ACCURACY" in item for item in response.warnings)
    assert runner.calls == 1
    assert adapter.predict_calls == 0


def test_mock_accuracy_keeps_existing_predict_path():
    adapter = FakeAdapter()
    runner = ApprovedRunner()
    orchestrator = InferenceOrchestrator(
        Settings(), registry=FakeRegistry(adapter), accuracy_runner=runner,
    )
    orchestrator.run(
        "acc-5", InferenceMode.MOCK, image_bytes(),
        inference_profile=InferenceProfile.ACCURACY,
    )
    assert adapter.predict_calls == 1
    assert runner.calls == 0
