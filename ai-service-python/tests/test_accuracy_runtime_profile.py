import hashlib
import io
import json

import pytest
from PIL import Image

from app.accuracy_profile import load_accuracy_profile
from app.accuracy_runtime import AccuracyRuntimeRunner, build_accuracy_runtime_runner
from app.config import Settings
from app.errors import ModelUnavailableError
from app.model_digest import dir_digest
from app.schemas import Applicability, ModelBrief


def _profile(tmp_path, status):
    base = tmp_path / "AI-VISION-ACCURACY-001/1.0.0"
    for name in ("qwen", "florence"):
        path = base / name
        path.mkdir(parents=True, exist_ok=True)
        (path / "config.json").write_text("{}", encoding="utf-8")
        (path / "model.safetensors").write_bytes(name.encode())
    benchmark = tmp_path / "benchmark.json"
    benchmark.write_text("{}", encoding="utf-8")
    payload = {
        "schemaVersion": 1,
        "profileId": "AI-VISION-ACCURACY-001",
        "version": "1.0.0",
        "status": status,
        "pipelineVersion": "ACCURACY-CANDIDATE-002",
        "baseModel": {"modelId": "AI-VISION-LOCAL-001", "version": "1.1.0"},
        "qwen": {"repo": "Qwen/Qwen3-VL-2B-Instruct", "license": "Apache-2.0", "path": "AI-VISION-ACCURACY-001/1.0.0/qwen", "sha256": dir_digest(base / "qwen")[0]},
        "florence": {"repo": "florence-community/Florence-2-large-ft", "license": "MIT", "path": "AI-VISION-ACCURACY-001/1.0.0/florence", "sha256": dir_digest(base / "florence")[0]},
        "inference": {"qwenMaxSide": 1024, "qwenMaxNewTokens": 128},
        "benchmark": {"detailsPath": "benchmark.json", "sha256": hashlib.sha256(benchmark.read_bytes()).hexdigest()},
    }
    if status == "APPROVED":
        payload.update(approvedBy="tester", approvedAt="2026-08-13T00:00:00+00:00")
    path = base / "profile.json"
    path.write_text(json.dumps(payload), encoding="utf-8")
    return path


def test_candidate_profile_does_not_install(tmp_path):
    _profile(tmp_path, "CANDIDATE")
    settings = Settings()
    settings.model_root = tmp_path
    settings.accuracy_model_root = tmp_path
    assert build_accuracy_runtime_runner(settings) is None


def test_approved_profile_can_live_outside_base_model_root(tmp_path):
    base_model_root = tmp_path / "base-runtime"
    accuracy_model_root = tmp_path / "accuracy-runtime"
    base_model_root.mkdir()
    _profile(accuracy_model_root, "APPROVED")

    settings = Settings()
    settings.model_root = base_model_root
    settings.accuracy_model_root = accuracy_model_root
    settings.vision_sha_mode = "STRICT"

    runner = build_accuracy_runtime_runner(settings)
    assert isinstance(runner, AccuracyRuntimeRunner)
    assert runner.profile.qwen_path.is_relative_to(accuracy_model_root.resolve())


class Adapter:
    def __init__(self):
        self.restore_calls = 0
        self._dino = self._dino_processor = self._sam = self._sam_processor = object()

    def model_info(self):
        return ModelBrief(modelId="AI-VISION-LOCAL-001", modelName="fake", version="1.1.0")

    def _load_models(self):
        self.restore_calls += 1
        self._dino = self._dino_processor = self._sam = self._sam_processor = object()


class LegacyAdapter(Adapter):
    def model_info(self):
        return ModelBrief(modelId="AI-VISION-LOCAL-001", modelName="fake", version="1.0.0")


class Batch:
    def __init__(self, **kwargs):
        self.grounded_factory = kwargs["grounded_factory"]

    def run_batch(self, images):
        assert self.grounded_factory()._dino is not None
        return [[]]


class ReleasingBatch:
    def __init__(self, **kwargs):
        self.grounded_factory = kwargs["grounded_factory"]

    def run_batch(self, images):
        grounded = self.grounded_factory()
        assert grounded._dino is not None
        grounded._dino = None
        grounded._dino_processor = None
        grounded._sam = None
        grounded._sam_processor = None
        return [[]]


def _decoded_image():
    out = io.BytesIO()
    Image.new("RGB", (32, 32)).save(out, format="JPEG")
    return type(
        "Decoded",
        (),
        {"bytes_": out.getvalue(), "applicability": Applicability.APPLICABLE},
    )()


def test_base_model_mismatch_reports_expected_and_actual_identity(tmp_path):
    profile = load_accuracy_profile(_profile(tmp_path, "APPROVED"), tmp_path, require_approved=True)
    with pytest.raises(ModelUnavailableError) as exc:
        AccuracyRuntimeRunner(profile, batch_runner_factory=Batch)(LegacyAdapter(), _decoded_image())
    message = str(exc.value)
    assert "AI-VISION-LOCAL-001 v1.1.0" in message
    assert "AI-VISION-LOCAL-001 v1.0.0" in message


def test_approved_runner_restores_base_model(tmp_path):
    profile = load_accuracy_profile(_profile(tmp_path, "APPROVED"), tmp_path, require_approved=True)
    adapter = Adapter()
    applicability, detections = AccuracyRuntimeRunner(profile, batch_runner_factory=Batch)(
        adapter,
        _decoded_image(),
    )
    assert applicability == Applicability.NO_DEFECT_FOUND
    assert detections == []
    assert adapter.restore_calls == 1
    assert adapter._dino is not None


def test_approved_runner_restores_base_after_batch_release(tmp_path):
    profile = load_accuracy_profile(_profile(tmp_path, "APPROVED"), tmp_path, require_approved=True)
    adapter = Adapter()
    applicability, detections = AccuracyRuntimeRunner(
        profile,
        batch_runner_factory=ReleasingBatch,
    )(adapter, _decoded_image())
    assert applicability == Applicability.NO_DEFECT_FOUND
    assert detections == []
    assert adapter.restore_calls == 2
    assert adapter._dino is not None
    assert adapter._sam is not None