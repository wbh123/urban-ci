from __future__ import annotations

import hashlib
import json
import pytest

from app.config import Settings
from app.errors import ModelUnavailableError
from app.model_runtime import ModelRegistry, RuntimeCatalogError, load_runtime_catalog
from app.schemas import Applicability, InferenceMode, ModelBrief


class FakeAdapter:
    def __init__(self, manifest, provider="CUDAExecutionProvider"):
        self.manifest = manifest
        self.provider = provider

    def model_info(self):
        return ModelBrief(
            modelId=self.manifest.model_id,
            modelName=self.manifest.model_name,
            version=self.manifest.version,
        )

    def execution_provider(self):
        return self.provider

    def predict(self, image):
        return Applicability.NO_DEFECT_FOUND, []


def _write_manifest(root, model_id: str, version: str = "1.0.0"):
    package = root / model_id / version
    package.mkdir(parents=True)
    weight = package / "model.onnx"
    weight.write_bytes(f"{model_id}-{version}".encode())
    payload = {
        "schemaVersion": 1,
        "modelId": model_id,
        "modelName": f"Model {model_id}",
        "version": version,
        "status": "APPROVED",
        "task": "CRACK_SEGMENTATION",
        "adapter": "onnx-crack-segmentation-v1",
        "weightFile": "model.onnx",
        "weightSha256": hashlib.sha256(weight.read_bytes()).hexdigest(),
        "source": {
            "type": "TRAINED",
            "repository": "local:test",
            "revision": "v1",
            "license": "MIT",
        },
        "classes": [{"code": "CRACK", "name": "裂缝"}],
        "input": {
            "width": 8,
            "height": 8,
            "mean": [0.485, 0.456, 0.406],
            "std": [0.229, 0.224, 0.225],
        },
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
    manifest = package / "manifest.json"
    manifest.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return manifest


def _write_catalog(root, entries, default_model_id=None):
    payload = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": default_model_id,
        "models": entries,
    }
    path = root / "runtime-catalog.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


def _settings(monkeypatch, root):
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_ROOT", str(root))
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    monkeypatch.setenv("URBAN_SAFE_AI_CUDA_DEVICE_ID", "2")
    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "UNAVAILABLE")
    monkeypatch.delenv("AI_MODEL_ROOT", raising=False)
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)
    return Settings()


def test_catalog_loads_explicit_enabled_entries(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_manifest(tmp_path, "AI-CRACK-002")
    _write_catalog(
        tmp_path,
        [
            {
                "modelId": "AI-CRACK-001",
                "version": "1.0.0",
                "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
                "enabled": True,
            },
            {
                "modelId": "AI-CRACK-002",
                "version": "1.0.0",
                "manifestPath": "AI-CRACK-002/1.0.0/manifest.json",
                "enabled": False,
            },
        ],
        default_model_id="AI-CRACK-001",
    )

    catalog = load_runtime_catalog(_settings(monkeypatch, tmp_path))

    assert catalog.runtime == "CUDA_ONLY"
    assert catalog.default_model_id == "AI-CRACK-001"
    assert [entry.model_id for entry in catalog.entries] == ["AI-CRACK-001"]


def test_catalog_rejects_duplicate_model_ids(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    entry = {
        "modelId": "AI-CRACK-001",
        "version": "1.0.0",
        "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
        "enabled": True,
    }
    _write_catalog(tmp_path, [entry, entry], default_model_id="AI-CRACK-001")

    with pytest.raises(RuntimeCatalogError, match="重复"):
        load_runtime_catalog(_settings(monkeypatch, tmp_path))


def test_catalog_rejects_manifest_path_escape(monkeypatch, tmp_path):
    _write_catalog(
        tmp_path,
        [
            {
                "modelId": "AI-CRACK-001",
                "version": "1.0.0",
                "manifestPath": "../outside/manifest.json",
                "enabled": True,
            }
        ],
        default_model_id="AI-CRACK-001",
    )

    with pytest.raises(RuntimeCatalogError, match="模型根目录"):
        load_runtime_catalog(_settings(monkeypatch, tmp_path))


def test_catalog_rejects_absolute_manifest_path(monkeypatch, tmp_path):
    manifest = _write_manifest(tmp_path, "AI-CRACK-001")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-CRACK-001",
            "version": "1.0.0",
            "manifestPath": str(manifest.resolve()),
            "enabled": True,
        }],
        default_model_id="AI-CRACK-001",
    )

    with pytest.raises(RuntimeCatalogError, match="相对路径"):
        load_runtime_catalog(_settings(monkeypatch, tmp_path))


def test_registry_requires_explicit_real_model_id(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-CRACK-001",
            "version": "1.0.0",
            "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-CRACK-001",
    )
    registry = ModelRegistry(
        _settings(monkeypatch, tmp_path),
        adapter_factories={"onnx-crack-segmentation-v1": FakeAdapter},
    )

    with pytest.raises(ModelUnavailableError, match="必须指定模型编号"):
        registry.resolve(InferenceMode.REAL, None)


def test_registry_eagerly_loads_and_selects_exact_real_model(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_manifest(tmp_path, "AI-CRACK-002")
    _write_catalog(
        tmp_path,
        [
            {
                "modelId": "AI-CRACK-001",
                "version": "1.0.0",
                "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
                "enabled": True,
            },
            {
                "modelId": "AI-CRACK-002",
                "version": "1.0.0",
                "manifestPath": "AI-CRACK-002/1.0.0/manifest.json",
                "enabled": True,
            },
        ],
        default_model_id="AI-CRACK-002",
    )
    calls = []

    def factory(manifest):
        calls.append(manifest.model_id)
        return FakeAdapter(manifest)

    registry = ModelRegistry(
        _settings(monkeypatch, tmp_path),
        adapter_factories={"onnx-crack-segmentation-v1": factory},
    )

    assert calls == ["AI-CRACK-001", "AI-CRACK-002"]
    selected = registry.resolve(InferenceMode.REAL, "AI-CRACK-001")
    assert selected.model_info().modelId == "AI-CRACK-001"
    assert registry.current_model_info().modelId == "AI-CRACK-002"
    readiness = registry.readiness()
    assert readiness.status == "READY"
    assert readiness.realModelCount == 2
    assert readiness.cudaDeviceId == 2


def test_registry_rejects_unknown_real_model(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-CRACK-001",
            "version": "1.0.0",
            "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-CRACK-001",
    )
    registry = ModelRegistry(
        _settings(monkeypatch, tmp_path),
        adapter_factories={"onnx-crack-segmentation-v1": FakeAdapter},
    )

    with pytest.raises(ModelUnavailableError, match="未加载"):
        registry.resolve(InferenceMode.REAL, "AI-MISSING")


def test_registry_rejects_catalog_manifest_identity_mismatch(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-WRONG",
            "version": "1.0.0",
            "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-WRONG",
    )

    with pytest.raises(ModelUnavailableError, match="目录加载失败"):
        ModelRegistry(
            _settings(monkeypatch, tmp_path),
            adapter_factories={"onnx-crack-segmentation-v1": FakeAdapter},
        )


def test_registry_rejects_non_cuda_adapter(monkeypatch, tmp_path):
    _write_manifest(tmp_path, "AI-CRACK-001")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-CRACK-001",
            "version": "1.0.0",
            "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-CRACK-001",
    )

    with pytest.raises(ModelUnavailableError, match="目录加载失败"):
        ModelRegistry(
            _settings(monkeypatch, tmp_path),
            adapter_factories={
                "onnx-crack-segmentation-v1": lambda manifest: FakeAdapter(
                    manifest, provider="CPUExecutionProvider"
                )
            },
        )


def test_legacy_single_manifest_is_migrated_when_catalog_missing(monkeypatch, tmp_path):
    manifest_path = _write_manifest(tmp_path, "AI-CRACK-LEGACY")
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_ROOT", str(tmp_path))
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "APPROVED")
    monkeypatch.setenv(
        "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH",
        str(manifest_path.relative_to(tmp_path)),
    )
    monkeypatch.delenv("AI_MODEL_ROOT", raising=False)
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)

    catalog = load_runtime_catalog(Settings())

    assert len(catalog.entries) == 1
    assert catalog.entries[0].model_id is None
    assert catalog.entries[0].manifest_path == manifest_path.resolve()


def _write_vision_manifest(root: Path, model_id: str = "AI-VISION-LOCAL-001", status: str = "APPROVED") -> Path:
    package = root / model_id / "1.0.0"
    package.mkdir(parents=True)
    approved_by = "test" if status == "APPROVED" else ""
    approved_at = "2026-08-11T00:00:00Z" if status == "APPROVED" else ""
    payload = {
        "schemaVersion": 1,
        "modelId": model_id,
        "modelName": "Test Grounded SAM2",
        "version": "1.0.0",
        "status": status,
        "identityVerified": status == "APPROVED",
        "task": "ZERO_SHOT_VISUAL_DEFECT",
        "adapter": "grounded-sam2-tiny-v1",
        "source": {
            "type": "ZERO_SHOT_OPEN_WEIGHTS",
            "repository": "modelscope",
            "revision": "a2bb814dd30d776dcf7e30523b00659f4f141c71/de431c4043854a71d8101e17995dfe596bf101a5",
            "license": "Apache-2.0",
        },
        "classes": [
            {"code": "CRACK", "name": "疑似裂缝", "prompts": ["wall crack"]},
        ],
        "checkpoint": {
            "detectorRepository": "IDEA-Research/grounding-dino-tiny",
            "segmenterRepository": "facebook/sam2.1-hiera-tiny",
            "detectorRevision": "a2bb814dd30d776dcf7e30523b00659f4f141c71",
            "segmenterRevision": "de431c4043854a71d8101e17995dfe596bf101a5",
            "detectorDir": "AI-VISION-LOCAL-001/1.0.0/detector",
            "segmenterDir": "AI-VISION-LOCAL-001/1.0.0/segmenter",
            "sha256": "a" * 64,
            "sizeBytes": 1024,
        },
        "input": {
            "maxLongSide": 1280,
            "boxThreshold": 0.25,
            "textThreshold": 0.25,
            "maxDetections": 10,
        },
        "license": "Apache-2.0",
        "approvedBy": approved_by,
        "approvedAt": approved_at,
    }
    path = package / "manifest.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


def test_registry_loads_zero_shot_vision_adapter(monkeypatch, tmp_path):
    _write_vision_manifest(tmp_path)
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-VISION-LOCAL-001",
            "version": "1.0.0",
            "manifestPath": "AI-VISION-LOCAL-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-VISION-LOCAL-001",
    )

    registry = ModelRegistry(
        _settings(monkeypatch, tmp_path),
        adapter_factories={
            "grounded-sam2-tiny-v1": lambda manifest: FakeAdapter(
                manifest, provider="PyTorch-CUDA"
            )
        },
    )

    info = registry.model_info("AI-VISION-LOCAL-001")
    assert info.mode == InferenceMode.REAL
    assert info.status == "APPROVED"
    assert info.adapter == "grounded-sam2-tiny-v1"
    assert info.executionProvider == "PyTorch-CUDA"
    assert info.supportedDefects == ["crack"]

    resolved = registry.resolve(InferenceMode.REAL, "AI-VISION-LOCAL-001")
    assert resolved is registry._real_adapters["AI-VISION-LOCAL-001"]


def test_registry_rejects_vision_adapter_on_cpu_provider(monkeypatch, tmp_path):
    _write_vision_manifest(tmp_path)
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-VISION-LOCAL-001",
            "version": "1.0.0",
            "manifestPath": "AI-VISION-LOCAL-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-VISION-LOCAL-001",
    )

    with pytest.raises(ModelUnavailableError, match="目录加载失败"):
        ModelRegistry(
            _settings(monkeypatch, tmp_path),
            adapter_factories={
                "grounded-sam2-tiny-v1": lambda manifest: FakeAdapter(
                    manifest, provider="CPUExecutionProvider"
                )
            },
        )


def test_registry_rejects_candidate_vision_model(monkeypatch, tmp_path):
    _write_vision_manifest(tmp_path, status="CANDIDATE")
    _write_catalog(
        tmp_path,
        [{
            "modelId": "AI-VISION-LOCAL-001",
            "version": "1.0.0",
            "manifestPath": "AI-VISION-LOCAL-001/1.0.0/manifest.json",
            "enabled": True,
        }],
        default_model_id="AI-VISION-LOCAL-001",
    )

    with pytest.raises(ModelUnavailableError, match="目录加载失败"):
        ModelRegistry(
            _settings(monkeypatch, tmp_path),
            adapter_factories={
                "grounded-sam2-tiny-v1": lambda manifest: FakeAdapter(
                    manifest, provider="PyTorch-CUDA"
                )
            },
        )
