import hashlib
import json
from pathlib import Path

import pytest

from app.model_manifest import ModelManifestError, load_model_manifest
from app.yolox_manifest import YOLOX_BUILDING_DEFECT_CODES, load_yolox_manifest


def _write_manifest(root: Path, **overrides) -> Path:
    model_dir = root / "AI-BUILDING-YOLOX-001" / "1.0.0"
    model_dir.mkdir(parents=True)
    weight = model_dir / "model.onnx"
    weight.write_bytes(b"synthetic-yolox-onnx")
    sha = hashlib.sha256(weight.read_bytes()).hexdigest()
    payload = {
        "schemaVersion": 1,
        "modelId": "AI-BUILDING-YOLOX-001",
        "modelName": "YOLOX-S 建筑病害检测",
        "version": "1.0.0",
        "status": "APPROVED",
        "task": "BUILDING_DEFECT_DETECTION",
        "adapter": "yolox-building-defect-v1",
        "weightFile": "model.onnx",
        "weightSha256": sha,
        "source": {
            "type": "TRAINED_FROM_YOLOX",
            "repository": "https://github.com/Megvii-BaseDetection/YOLOX",
            "revision": "a" * 40,
            "license": "Apache-2.0",
        },
        "classes": [
            {"code": code, "name": name}
            for code, name in [
                ("CRACK", "裂缝"),
                ("SPALLING", "剥落"),
                ("EXPOSED_REBAR", "钢筋外露"),
                ("CORROSION", "锈蚀"),
                ("WATER_SEEPAGE", "渗水"),
                ("EFFLORESCENCE", "泛碱"),
                ("WALL_DAMAGE", "墙体破损"),
            ]
        ],
        "input": {"width": 640, "height": 640, "padValue": 114},
        "thresholds": {"score": 0.35, "nmsIou": 0.45, "maximumDetections": 30},
        "metrics": {
            "dataset": "urban-safe-independent-test-v1",
            "map50": 0.70,
            "map5095": 0.45,
            "precision": 0.75,
            "recall": 0.70,
        },
        "license": "Apache-2.0",
        "approvedBy": "UrbanSafe AI Review",
        "approvedAt": "2026-08-15T00:00:00Z",
    }
    for key, value in overrides.items():
        payload[key] = value
    path = model_dir / "manifest.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return path


def test_yolox_manifest_loads_approved_model_and_model_manifest_dispatches(tmp_path: Path):
    path = _write_manifest(tmp_path)

    manifest = load_yolox_manifest(path, tmp_path)
    dispatched = load_model_manifest(path, tmp_path)

    assert manifest.model_id == "AI-BUILDING-YOLOX-001"
    assert manifest.adapter == "yolox-building-defect-v1"
    assert tuple(item.code for item in manifest.classes) == YOLOX_BUILDING_DEFECT_CODES
    assert manifest.input.width == 640
    assert manifest.thresholds.score == 0.35
    assert dispatched.model_id == manifest.model_id


def test_yolox_manifest_rejects_candidate_from_real_runtime(tmp_path: Path):
    path = _write_manifest(tmp_path, status="CANDIDATE")
    with pytest.raises(ModelManifestError, match="APPROVED"):
        load_yolox_manifest(path, tmp_path)


def test_yolox_manifest_rejects_wrong_task(tmp_path: Path):
    path = _write_manifest(tmp_path, task="CRACK_SEGMENTATION")
    with pytest.raises(ModelManifestError, match="任务"):
        load_yolox_manifest(path, tmp_path)


def test_yolox_manifest_requires_exact_business_class_contract(tmp_path: Path):
    path = _write_manifest(
        tmp_path,
        classes=[{"code": "CRACK", "name": "裂缝"}, {"code": "CRACK", "name": "裂缝2"}],
    )
    with pytest.raises(ModelManifestError, match="类别"):
        load_yolox_manifest(path, tmp_path)


def test_yolox_manifest_rejects_invalid_thresholds(tmp_path: Path):
    path = _write_manifest(
        tmp_path,
        thresholds={"score": 1.5, "nmsIou": 0.45, "maximumDetections": 30},
    )
    with pytest.raises(ModelManifestError, match="score"):
        load_yolox_manifest(path, tmp_path)


def test_yolox_manifest_rejects_wrong_weight_digest(tmp_path: Path):
    path = _write_manifest(tmp_path, weightSha256="0" * 64)
    with pytest.raises(ModelManifestError, match="SHA-256"):
        load_yolox_manifest(path, tmp_path)
