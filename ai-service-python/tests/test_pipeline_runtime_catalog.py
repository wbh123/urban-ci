from __future__ import annotations

import json

from tools.pipeline_runtime import _update_runtime_catalog


def test_runtime_catalog_is_created_and_model_is_default(tmp_path):
    relative = tmp_path / "AI-ONE" / "1.0.0" / "manifest.json"
    relative.parent.mkdir(parents=True)
    relative.write_text("{}", encoding="utf-8")

    path = _update_runtime_catalog(
        tmp_path,
        {"modelId": "AI-ONE", "version": "1.0.0"},
        relative.relative_to(tmp_path),
    )

    body = json.loads(path.read_text(encoding="utf-8"))
    assert body["schemaVersion"] == 1
    assert body["runtime"] == "CUDA_ONLY"
    assert body["defaultModelId"] == "AI-ONE"
    assert body["models"] == [{
        "modelId": "AI-ONE",
        "version": "1.0.0",
        "manifestPath": "AI-ONE/1.0.0/manifest.json",
        "enabled": True,
    }]


def test_runtime_catalog_preserves_other_models_and_replaces_same_id(tmp_path):
    catalog = tmp_path / "runtime-catalog.json"
    catalog.write_text(json.dumps({
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": "AI-OLD",
        "models": [
            {"modelId": "AI-OLD", "version": "1.0.0", "manifestPath": "old/manifest.json", "enabled": True},
            {"modelId": "AI-KEEP", "version": "1.0.0", "manifestPath": "keep/manifest.json", "enabled": True},
        ],
    }), encoding="utf-8")

    _update_runtime_catalog(
        tmp_path,
        {"modelId": "AI-OLD", "version": "2.0.0"},
        __import__("pathlib").Path("AI-OLD/2.0.0/manifest.json"),
    )

    body = json.loads(catalog.read_text(encoding="utf-8"))
    assert body["defaultModelId"] == "AI-OLD"
    assert [item["modelId"] for item in body["models"]] == ["AI-KEEP", "AI-OLD"]
    assert body["models"][1]["version"] == "2.0.0"
