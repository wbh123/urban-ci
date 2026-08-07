from __future__ import annotations

import hashlib
import io
import json
import sys

import numpy as np
import pytest
from PIL import Image

from app.adapters.onnx_crack_segmentation import (
    CUDA_EXECUTION_PROVIDER,
    DISABLE_CPU_FALLBACK_KEY,
    OnnxCrackSegmentationAdapter,
    _create_session,
    _foreground_probabilities,
    _output_probabilities,
    summarize_output_tensor,
)
from app.config import Settings
from app.errors import ModelUnavailableError
from app.image import decode_image
from app.model_manifest import load_model_manifest
from app.schemas import Applicability


class FakeNode:
    def __init__(self, name, shape, type_="tensor(float)"):
        self.name = name
        self.shape = shape
        self.type = type_


class FakeSessionOptions:
    def __init__(self):
        self.entries = {}

    def add_session_config_entry(self, key, value):
        self.entries[key] = value


class FakeOrtSession:
    def __init__(
        self,
        active_providers,
        output_shape=(1, 1, 8, 8),
        graph_providers=None,
    ):
        self.active_providers = active_providers
        self.output_shape = output_shape
        self.graph_providers = graph_providers or ["CUDAExecutionProvider"]
        self.fallback_disabled = False
        self.calls = []

    def get_providers(self):
        return list(self.active_providers)

    def get_inputs(self):
        return [FakeNode("images", [1, 3, 8, 8])]

    def get_outputs(self):
        return [FakeNode("mask_logits", [1, 1, 8, 8])]

    def disable_fallback(self):
        self.fallback_disabled = True

    def get_provider_graph_assignment_info(self):
        return [FakeAssignedSubgraph(provider) for provider in self.graph_providers]

    def run(self, output_names, inputs):
        self.calls.append((output_names, inputs))
        return [np.zeros(self.output_shape, dtype=np.float32)]


class FakeAssignedSubgraph:
    def __init__(self, ep_name):
        self.ep_name = ep_name


class FakeOrtModule:
    def __init__(self, available, active, graph_providers=None):
        self.available = available
        self.session = FakeOrtSession(active, graph_providers=graph_providers)
        self.session_options = None
        self.providers = None
        self.SessionOptions = self._session_options

    def _session_options(self):
        self.session_options = FakeSessionOptions()
        return self.session_options

    def get_available_providers(self):
        return list(self.available)

    def InferenceSession(self, model_path, sess_options, providers):
        self.providers = providers
        assert sess_options is self.session_options
        return self.session


def _manifest(
    tmp_path,
    *,
    output_activation: str = "LOGITS",
    foreground_polarity: str = "HIGH_PROBABILITY",
    interpolation: str = "BILINEAR",
):
    weight = tmp_path / "model.onnx"
    weight.write_bytes(b"onnx")
    payload = {
        "schemaVersion": 1,
        "modelId": "AI-CRACK-001",
        "modelName": "Crack",
        "version": "1.0.0",
        "status": "APPROVED",
        "task": "CRACK_SEGMENTATION",
        "adapter": "onnx-crack-segmentation-v1",
        "weightFile": "model.onnx",
        "weightSha256": hashlib.sha256(weight.read_bytes()).hexdigest(),
        "source": {
            "type": "TRAINED",
            "repository": "local",
            "revision": "v1",
            "license": "MIT",
        },
        "classes": [{"code": "CRACK", "name": "裂缝"}],
        "input": {
            "width": 8,
            "height": 8,
            "mean": [0, 0, 0],
            "std": [1, 1, 1],
            "interpolation": interpolation,
        },
        "outputActivation": output_activation,
        "foregroundPolarity": foreground_polarity,
        "thresholds": {"mask": 0.5, "minComponentPixels": 2},
        "metrics": {
            "dataset": "test",
            "pixelF1": 0.8,
            "iou": 0.7,
            "imageRecall": 0.9,
        },
        "license": "MIT",
        "approvedBy": "test",
        "approvedAt": "2026-07-27T00:00:00Z",
    }
    path = tmp_path / "manifest.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return load_model_manifest(path, tmp_path)


def _decoded_image():
    image = Image.new("RGB", (64, 64), (128, 128, 128))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return decode_image(buffer.getvalue(), Settings())


def test_session_uses_only_cuda_and_disables_all_fallback(monkeypatch, tmp_path):
    fake_ort = FakeOrtModule(
        available=["CUDAExecutionProvider", "CPUExecutionProvider"],
        active=["CUDAExecutionProvider", "CPUExecutionProvider"],
        graph_providers=["CUDAExecutionProvider"],
    )
    monkeypatch.setitem(sys.modules, "onnxruntime", fake_ort)

    session = _create_session(_manifest(tmp_path), cuda_device_id=3)

    assert fake_ort.providers == [
        (
            CUDA_EXECUTION_PROVIDER,
            {"device_id": "3", "do_copy_in_default_stream": "1"},
        )
    ]
    assert fake_ort.session_options.entries[DISABLE_CPU_FALLBACK_KEY] == "1"
    assert fake_ort.session_options.entries["session.record_ep_graph_assignment_info"] == "1"
    assert session.fallback_disabled is True
    assert len(session.calls) == 1
    assert session.calls[0][1]["images"].shape == (1, 3, 8, 8)


def test_session_rejects_missing_cuda_provider(monkeypatch, tmp_path):
    monkeypatch.setitem(
        sys.modules,
        "onnxruntime",
        FakeOrtModule(available=["CPUExecutionProvider"], active=["CPUExecutionProvider"]),
    )

    with pytest.raises(ModelUnavailableError, match="CUDAExecutionProvider"):
        _create_session(_manifest(tmp_path))


def test_session_rejects_runtime_that_assigns_graph_to_cpu(monkeypatch, tmp_path):
    monkeypatch.setitem(
        sys.modules,
        "onnxruntime",
        FakeOrtModule(
            available=["CUDAExecutionProvider", "CPUExecutionProvider"],
            active=["CUDAExecutionProvider", "CPUExecutionProvider"],
            graph_providers=["CUDAExecutionProvider", "CPUExecutionProvider"],
        ),
    )

    with pytest.raises(ModelUnavailableError, match="CPUExecutionProvider"):
        _create_session(_manifest(tmp_path))


def test_probability_output_is_not_sigmoid_twice():
    output = np.asarray([[0.0, 0.25, 0.75, 1.0]], dtype=np.float32)

    probabilities = _output_probabilities(output, "PROBABILITY")

    np.testing.assert_allclose(probabilities, output)


def test_logits_output_is_converted_with_sigmoid():
    output = np.asarray([[-10.0, 0.0, 10.0]], dtype=np.float32)

    probabilities = _output_probabilities(output, "LOGITS")

    assert probabilities[0, 0] < 0.001
    assert probabilities[0, 1] == pytest.approx(0.5)
    assert probabilities[0, 2] > 0.999


def test_low_probability_foreground_inverts_model_probability():
    model_probabilities = np.asarray([[0.05, 0.25, 0.75, 0.95]], dtype=np.float32)

    foreground = _foreground_probabilities(
        model_probabilities, "LOW_PROBABILITY"
    )

    np.testing.assert_allclose(foreground, 1.0 - model_probabilities)


def test_output_summary_contains_percentiles_and_activation_ratios():
    output = np.arange(100, dtype=np.float32).reshape(10, 10) / 100.0

    summary = summarize_output_tensor(output, [0.25, 0.5, 0.75])

    assert summary["minimum"] == pytest.approx(0.0)
    assert summary["maximum"] == pytest.approx(0.99)
    assert set(summary["percentiles"]) == {
        "p01", "p05", "p25", "p50", "p75", "p95", "p99"
    }
    assert summary["activationRatios"]["0.5"] == pytest.approx(0.5)


def test_segmentation_component_is_normalized(tmp_path):
    logits = np.full((1, 1, 8, 8), -10.0, dtype=np.float32)
    logits[0, 0, 2:4, 3:6] = 10.0
    session = FakeOrtSession(["CUDAExecutionProvider"])
    session.run = lambda output_names, inputs: [logits]
    adapter = OnnxCrackSegmentationAdapter(_manifest(tmp_path), session=session)

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.APPLICABLE
    assert len(detections) == 1
    assert detections[0].boundingBox.x == 3 / 8
    assert detections[0].boundingBox.y == 2 / 8
    assert detections[0].boundingBox.width == 3 / 8
    assert detections[0].boundingBox.height == 2 / 8


def test_official_low_probability_model_detects_dark_crack_region(tmp_path):
    logits = np.full((1, 1, 8, 8), 10.0, dtype=np.float32)
    logits[0, 0, 2:4, 3:6] = -10.0
    session = FakeOrtSession(["CUDAExecutionProvider"])
    session.run = lambda output_names, inputs: [logits]
    adapter = OnnxCrackSegmentationAdapter(
        _manifest(
            tmp_path,
            output_activation="LOGITS",
            foreground_polarity="LOW_PROBABILITY",
            interpolation="LANCZOS",
        ),
        session=session,
    )

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.APPLICABLE
    assert len(detections) == 1
    assert detections[0].confidence > 0.999
    assert detections[0].boundingBox.x == 3 / 8
    assert detections[0].boundingBox.y == 2 / 8


def test_probability_model_can_return_local_detection(tmp_path):
    probabilities = np.zeros((1, 1, 8, 8), dtype=np.float32)
    probabilities[0, 0, 2:4, 3:6] = 0.9
    session = FakeOrtSession(["CUDAExecutionProvider"])
    session.run = lambda output_names, inputs: [probabilities]
    adapter = OnnxCrackSegmentationAdapter(
        _manifest(tmp_path, output_activation="PROBABILITY"),
        session=session,
    )

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.APPLICABLE
    assert len(detections) == 1


def test_predict_rejects_near_full_image_activation(tmp_path):
    logits = np.full((1, 1, 8, 8), 10.0, dtype=np.float32)
    session = FakeOrtSession(["CUDAExecutionProvider"])
    session.run = lambda output_names, inputs: [logits]
    adapter = OnnxCrackSegmentationAdapter(_manifest(tmp_path), session=session)

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.NOT_APPLICABLE
    assert detections == []
