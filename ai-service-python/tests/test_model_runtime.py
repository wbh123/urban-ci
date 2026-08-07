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
