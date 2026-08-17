from __future__ import annotations

import hashlib
import json
from pathlib import Path

from app.config import Settings
from app.model_runtime import ModelRegistry
from app.schemas import Applicability, InferenceMode, ModelBrief


class FakeAdapter:
    def __init__(self, manifest):
        self.manifest = manifest

    def model_info(self):
        return ModelBrief(
            modelId=self.manifest.model_id,
            modelName=self.manifest.model_name,
            version=self.manifest.version,
        )

    def execution_provider(self):
        return "CUDAExecutionProvider"

    def predict(self, image):
        return Applicability.NO_DEFECT_FOUND, []


def _settings(monkeypatch, root: Path) -> Settings:
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_ROOT", str(root))
    monkeypatch.setenv("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    monkeypatch.setenv("URBAN_SAFE_AI_CUDA_DEVICE_ID", "0")
    monkeypatch.setenv("URBAN_SAFE_AI_REAL_MODEL_STATUS", "UNAVAILABLE")
    monkeypatch.delenv("AI_MODEL_ROOT", raising=False)
    monkeypatch.delenv("AI_MODEL_CATALOG_PATH", raising=False)
    return Settings()


def _write_crack_manifest(root: Path) -> None:
    package = root / "AI-CRACK-001" / "1.0.0"
    package.mkdir(parents=True)
    weight = package / "model.onnx"
    weight.write_bytes(b"crack")
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
        "source": {"type": "TRAINED", "repository": "local", "revision": "v1", "license": "MIT"},
        "classes": [{"code": "CRACK", "name": "裂缝"}],
        "input": {"width": 32, "height": 32, "mean": [0, 0, 0], "std": [1, 1, 1]},
        "thresholds": {"mask": 0.5, "minComponentPixels": 2},
        "metrics": {"dataset": "test", "pixelF1": 0.8, "iou": 0.7, "imageRecall": 0.9},
        "license": "MIT",
        "approvedBy": "test",
        "approvedAt": "2026-08-15T00:00:00Z",
    }
    (package / "manifest.json").write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def _write_yolox_manifest(root: Path) -> None:
    package = root / "AI-BUILDING-YOLOX-001" / "1.0.0"
    package.mkdir(parents=True)
    weight = package / "model.onnx"
    weight.write_bytes(b"yolox")
    classes = [
        ("CRACK", "裂缝"),
        ("SPALLING", "剥落"),
        ("EXPOSED_REBAR", "钢筋外露"),
        ("CORROSION", "锈蚀"),
        ("WATER_SEEPAGE", "渗水"),
        ("EFFLORESCENCE", "泛碱"),
        ("WALL_DAMAGE", "墙体破损"),
    ]
    payload = {
        "schemaVersion": 1,
        "modelId": "AI-BUILDING-YOLOX-001",
        "modelName": "YOLOX-S 建筑病害检测",
        "version": "1.0.0",
        "status": "APPROVED",
        "task": "BUILDING_DEFECT_DETECTION",
        "adapter": "yolox-building-defect-v1",
        "weightFile": "model.onnx",
        "weightSha256": hashlib.sha256(weight.read_bytes()).hexdigest(),
        "source": {
            "type": "TRAINED_FROM_YOLOX",
            "repository": "https://github.com/Megvii-BaseDetection/YOLOX",
            "revision": "a" * 40,
            "license": "Apache-2.0",
        },
        "classes": [{"code": code, "name": name} for code, name in classes],
        "input": {"width": 640, "height": 640, "padValue": 114},
        "thresholds": {"score": 0.35, "nmsIou": 0.45, "maximumDetections": 30},
        "metrics": {"dataset": "test", "map50": 0.7, "map5095": 0.45, "precision": 0.75, "recall": 0.7},
        "license": "Apache-2.0",
        "approvedBy": "test",
        "approvedAt": "2026-08-15T00:00:00Z",
    }
    (package / "manifest.json").write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


def _write_catalog(root: Path) -> None:
    payload = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": "AI-CRACK-001",
        "models": [
            {
                "modelId": "AI-CRACK-001",
                "version": "1.0.0",
                "manifestPath": "AI-CRACK-001/1.0.0/manifest.json",
                "enabled": True,
            },
            {
                "modelId": "AI-BUILDING-YOLOX-001",
                "version": "1.0.0",
                "manifestPath": "AI-BUILDING-YOLOX-001/1.0.0/manifest.json",
                "enabled": True,
            },
        ],
    }
    (root / "runtime-catalog.json").write_text(json.dumps(payload), encoding="utf-8")


def test_default_registry_factory_contains_yolox_without_enabling_candidate(monkeypatch, tmp_path: Path):
    settings = _settings(monkeypatch, tmp_path)
    registry = ModelRegistry(settings)

    assert "yolox-building-defect-v1" in registry._factories
    assert registry.readiness().realModelCount == 0


def test_registry_routes_yolox_exactly_without_changing_existing_default(monkeypatch, tmp_path: Path):
    _write_crack_manifest(tmp_path)
    _write_yolox_manifest(tmp_path)
    _write_catalog(tmp_path)
    registry = ModelRegistry(
        _settings(monkeypatch, tmp_path),
        adapter_factories={
            "onnx-crack-segmentation-v1": FakeAdapter,
            "yolox-building-defect-v1": FakeAdapter,
        },
    )

    assert registry.current_model_info().modelId == "AI-CRACK-001"
    selected = registry.resolve(InferenceMode.REAL, "AI-BUILDING-YOLOX-001")
    assert selected.model_info().modelId == "AI-BUILDING-YOLOX-001"

    info = registry.model_info("AI-BUILDING-YOLOX-001")
    assert info.task == "BUILDING_DEFECT_DETECTION"
    assert info.adapter == "yolox-building-defect-v1"
    assert info.license == "Apache-2.0"
    assert info.executionProvider == "CUDAExecutionProvider"
    assert info.supportedDefects == [
        "crack",
        "spalling",
        "exposed_rebar",
        "corrosion",
        "water_seepage",
        "efflorescence",
        "wall_damage",
    ]
    assert registry.readiness().realModelCount == 2
    assert registry.readiness().defaultRealModelId == "AI-CRACK-001"
