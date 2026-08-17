from __future__ import annotations

import io
import sys
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from app.adapters.yolox_building_defect import (
    CUDA_EXECUTION_PROVIDER,
    DISABLE_CPU_FALLBACK_KEY,
    YoloXBuildingDefectAdapter,
    _create_yolox_session,
)
from app.config import Settings
from app.errors import ModelUnavailableError
from app.image import DecodedImage, decode_image
from app.schemas import Applicability, QualityStatus
from app.yolox_manifest import (
    YoloXClass,
    YoloXInput,
    YoloXMetrics,
    YoloXModelManifest,
    YoloXThresholds,
)
from app.model_manifest import ModelSource


CLASS_PAIRS = (
    ("CRACK", "裂缝"),
    ("SPALLING", "剥落"),
    ("EXPOSED_REBAR", "钢筋外露"),
    ("CORROSION", "锈蚀"),
    ("WATER_SEEPAGE", "渗水"),
    ("EFFLORESCENCE", "泛碱"),
    ("WALL_DAMAGE", "墙体破损"),
)


class FakeNode:
    def __init__(self, name, shape, type_="tensor(float)"):
        self.name = name
        self.shape = shape
        self.type = type_


class FakeAssignedSubgraph:
    def __init__(self, ep_name):
        self.ep_name = ep_name


class FakeSessionOptions:
    def __init__(self):
        self.entries = {}

    def add_session_config_entry(self, key, value):
        self.entries[key] = value


class FakeYoloXSession:
    def __init__(self, output=None, active_providers=None, graph_providers=None):
        self.output = output if output is not None else np.zeros((1, 8400, 12), dtype=np.float32)
        self.active_providers = active_providers or [CUDA_EXECUTION_PROVIDER]
        self.graph_providers = graph_providers or [CUDA_EXECUTION_PROVIDER]
        self.fallback_disabled = False
        self.calls = []

    def get_providers(self):
        return list(self.active_providers)

    def get_inputs(self):
        return [FakeNode("images", [1, 3, 640, 640])]

    def get_outputs(self):
        return [FakeNode("output", [1, 8400, 12])]

    def get_provider_graph_assignment_info(self):
        return [FakeAssignedSubgraph(provider) for provider in self.graph_providers]

    def disable_fallback(self):
        self.fallback_disabled = True

    def run(self, output_names, inputs):
        self.calls.append((output_names, inputs))
        return [self.output.copy()]


class FakeOrtModule:
    def __init__(self, session: FakeYoloXSession, available=None):
        self.session = session
        self.available = available or [CUDA_EXECUTION_PROVIDER, "CPUExecutionProvider"]
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


def _manifest(tmp_path: Path) -> YoloXModelManifest:
    weight = tmp_path / "model.onnx"
    weight.write_bytes(b"synthetic-yolox")
    return YoloXModelManifest(
        schema_version=1,
        model_id="AI-BUILDING-YOLOX-001",
        model_name="YOLOX-S 建筑病害检测",
        version="1.0.0",
        status="APPROVED",
        task="BUILDING_DEFECT_DETECTION",
        adapter="yolox-building-defect-v1",
        weight_path=weight,
        weight_sha256="0" * 64,
        source=ModelSource(
            type="TRAINED_FROM_YOLOX",
            repository="https://github.com/Megvii-BaseDetection/YOLOX",
            revision="a" * 40,
            license="Apache-2.0",
        ),
        classes=tuple(YoloXClass(code=code, name=name) for code, name in CLASS_PAIRS),
        input=YoloXInput(width=640, height=640, pad_value=114),
        thresholds=YoloXThresholds(score=0.35, nms_iou=0.45, maximum_detections=30),
        metrics=YoloXMetrics(
            dataset="independent-test",
            map50=0.7,
            map5095=0.45,
            precision=0.75,
            recall=0.70,
        ),
        license="Apache-2.0",
        approved_by="test",
        approved_at="2026-08-15T00:00:00Z",
    )


def _decoded_image() -> DecodedImage:
    image = Image.new("RGB", (320, 160), (100, 120, 140))
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return decode_image(buffer.getvalue(), Settings())


def test_create_session_disables_cpu_fallback_validates_cuda_and_warms_up(monkeypatch, tmp_path):
    session = FakeYoloXSession(active_providers=[CUDA_EXECUTION_PROVIDER, "CPUExecutionProvider"])
    fake_ort = FakeOrtModule(session)
    monkeypatch.setitem(sys.modules, "onnxruntime", fake_ort)

    created = _create_yolox_session(_manifest(tmp_path), cuda_device_id=2)

    assert created is session
    assert fake_ort.providers == [
        (CUDA_EXECUTION_PROVIDER, {"device_id": "2", "do_copy_in_default_stream": "1"})
    ]
    assert fake_ort.session_options.entries[DISABLE_CPU_FALLBACK_KEY] == "1"
    assert fake_ort.session_options.entries["session.record_ep_graph_assignment_info"] == "1"
    assert session.fallback_disabled is True
    assert len(session.calls) == 1
    assert session.calls[0][1]["images"].shape == (1, 3, 640, 640)


def test_create_session_rejects_cpu_graph_assignment(monkeypatch, tmp_path):
    session = FakeYoloXSession(graph_providers=[CUDA_EXECUTION_PROVIDER, "CPUExecutionProvider"])
    monkeypatch.setitem(sys.modules, "onnxruntime", FakeOrtModule(session))

    with pytest.raises(ModelUnavailableError, match="CPUExecutionProvider"):
        _create_yolox_session(_manifest(tmp_path))


def test_create_session_rejects_wrong_output_contract(monkeypatch, tmp_path):
    session = FakeYoloXSession()
    session.get_outputs = lambda: [FakeNode("output", [1, 8400, 11])]
    monkeypatch.setitem(sys.modules, "onnxruntime", FakeOrtModule(session))

    with pytest.raises(ModelUnavailableError, match="输出形状"):
        _create_yolox_session(_manifest(tmp_path))


def test_predict_decodes_detection_and_maps_back_to_original_image(tmp_path):
    output = np.zeros((1, 8400, 12), dtype=np.float32)
    # stride=8, grid (10, 10): cx/cy=(84,84), w/h=16; source image ratio=2.
    index = 10 * 80 + 10
    output[0, index, :4] = [0.5, 0.5, np.log(2.0), np.log(2.0)]
    output[0, index, 4] = 0.95
    output[0, index, 5 + 1] = 0.90  # SPALLING
    session = FakeYoloXSession(output=output)
    adapter = YoloXBuildingDefectAdapter(_manifest(tmp_path), session=session)

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.APPLICABLE
    assert len(detections) == 1
    detection = detections[0]
    assert detection.classCode == "SPALLING"
    assert detection.className == "剥落"
    assert detection.confidence == pytest.approx(0.855)
    assert detection.boundingBox.x == pytest.approx(38 / 320)
    assert detection.boundingBox.y == pytest.approx(38 / 160)
    assert detection.boundingBox.width == pytest.approx(8 / 320)
    assert detection.boundingBox.height == pytest.approx(8 / 160)
    assert detection.segmentation is None


def test_predict_returns_no_defect_when_every_score_is_below_threshold(tmp_path):
    adapter = YoloXBuildingDefectAdapter(
        _manifest(tmp_path),
        session=FakeYoloXSession(output=np.zeros((1, 8400, 12), dtype=np.float32)),
    )

    applicability, detections = adapter.predict(_decoded_image())

    assert applicability == Applicability.NO_DEFECT_FOUND
    assert detections == []


def test_low_quality_image_does_not_run_yolox(tmp_path):
    session = FakeYoloXSession()
    adapter = YoloXBuildingDefectAdapter(_manifest(tmp_path), session=session)
    image = _decoded_image()
    low_quality = DecodedImage(
        bytes_=image.bytes_,
        source_bytes=image.source_bytes,
        width=image.width,
        height=image.height,
        content_type=image.content_type,
        quality_status=QualityStatus.LOW_QUALITY,
        applicability=Applicability.LOW_QUALITY,
    )

    applicability, detections = adapter.predict(low_quality)

    assert applicability == Applicability.LOW_QUALITY
    assert detections == []
    assert session.calls == []
