from types import SimpleNamespace

import pytest

from app.inference import InferenceOrchestrator, _formal_precision_results
from app.schemas import (
    Applicability,
    InferenceMode,
    InferenceProfile,
    ModelBrief,
    QualityStatus,
)


class FakeRegistry:
    def __init__(self, adapter):
        self.adapter = adapter

    def resolve(self, mode, requested_model_id):
        return self.adapter


class FakeAdapter:
    def __init__(self):
        self.fast_calls = 0

    def predict(self, decoded):
        self.fast_calls += 1
        return Applicability.NO_DEFECT_FOUND, []

    def model_info(self):
        return ModelBrief(modelId="m", modelName="m", version="1")


def decoded_image():
    return SimpleNamespace(
        width=100,
        height=100,
        bytes_=b"x",
        quality_status=QualityStatus.ACCEPTABLE,
        applicability=Applicability.APPLICABLE,
    )


def test_real_precision_uses_precision_engine_while_fast_uses_adapter(monkeypatch):
    from app import inference as module

    adapter = FakeAdapter()
    orchestrator = InferenceOrchestrator(
        SimpleNamespace(), registry=FakeRegistry(adapter)
    )
    monkeypatch.setattr(module, "decode_image", lambda data, settings: decoded_image())
    precision_calls = []
    monkeypatch.setattr(
        module,
        "_run_precision",
        lambda resolved_adapter, decoded: (
            precision_calls.append(resolved_adapter)
            or (Applicability.NO_DEFECT_FOUND, [])
        ),
    )

    orchestrator.run(
        "r-fast",
        InferenceMode.REAL,
        b"x",
        inference_profile=InferenceProfile.FAST,
    )
    orchestrator.run(
        "r-precision",
        InferenceMode.REAL,
        b"x",
        inference_profile=InferenceProfile.PRECISION,
    )

    assert adapter.fast_calls == 1
    assert precision_calls == [adapter]


def test_mock_precision_request_keeps_mock_adapter_path(monkeypatch):
    from app import inference as module

    adapter = FakeAdapter()
    orchestrator = InferenceOrchestrator(
        SimpleNamespace(), registry=FakeRegistry(adapter)
    )
    monkeypatch.setattr(module, "decode_image", lambda data, settings: decoded_image())
    monkeypatch.setattr(
        module,
        "_run_precision",
        lambda *args: pytest.fail("MOCK must not use precision engine"),
    )

    orchestrator.run(
        "r",
        InferenceMode.MOCK,
        b"x",
        inference_profile=InferenceProfile.PRECISION,
    )
    assert adapter.fast_calls == 1


def test_low_trust_candidates_are_diagnostic_only_not_formal_detections():
    results = [
        SimpleNamespace(trust=SimpleNamespace(level="HIGH")),
        SimpleNamespace(trust=SimpleNamespace(level="MEDIUM")),
        SimpleNamespace(trust=SimpleNamespace(level="LOW")),
    ]
    formal = _formal_precision_results(results)
    assert [item.trust.level for item in formal] == ["HIGH", "MEDIUM"]
