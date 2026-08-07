"""真实模型清单准入测试。"""

from __future__ import annotations

import hashlib
import json

import pytest

from app.model_manifest import ModelManifestError, load_model_manifest


def _write_manifest(
    tmp_path,
    *,
    status: str = "APPROVED",
    weight_file: str = "model.onnx",
    sha256: str | None = None,
    output_activation: str | None = None,
    foreground_polarity: str | None = None,
    interpolation: str | None = None,
):
    weights = tmp_path / "model.onnx"
    weights.write_bytes(b"urban-safe-test-model")
    digest = hashlib.sha256(weights.read_bytes()).hexdigest()
    model_input = {
        "width": 640,
        "height": 640,
        "mean": [0.485, 0.456, 0.406],
        "std": [0.229, 0.224, 0.225],
    }
    if interpolation is not None:
        model_input["interpolation"] = interpolation
    manifest = {
        "schemaVersion": 1,
        "modelId": "AI-CRACK-SEG-001",
        "modelName": "UrbanSafe Crack Segmentation",
        "version": "1.0.0",
        "status": status,
        "task": "CRACK_SEGMENTATION",
        "adapter": "onnx-crack-segmentation-v1",
        "weightFile": weight_file,
        "weightSha256": sha256 or digest,
        "source": {
            "type": "TRAINED",
            "repository": "mendeley:jwsn7tfbrp/1",
            "revision": "dataset-v1",
            "license": "CC-BY-4.0",
        },
        "classes": [{"code": "CRACK", "name": "裂缝"}],
        "input": model_input,
        "thresholds": {"mask": 0.5, "minComponentPixels": 4},
        "metrics": {
            "dataset": "local-holdout-v1",
            "pixelF1": 0.81,
            "iou": 0.68,
            "imageRecall": 0.93,
        },
        "license": "PROJECT-TRAINED-CC-BY-4.0-DATA",
        "approvedBy": "test-reviewer",
        "approvedAt": "2026-07-26T00:00:00Z",
    }
    if output_activation is not None:
        manifest["outputActivation"] = output_activation
    if foreground_polarity is not None:
        manifest["foregroundPolarity"] = foreground_polarity
    path = tmp_path / "manifest.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False), encoding="utf-8")
    return path


def test_load_approved_manifest_and_verify_weight_digest(tmp_path):
    path = _write_manifest(tmp_path)

    manifest = load_model_manifest(path, tmp_path)

    assert manifest.model_id == "AI-CRACK-SEG-001"
    assert manifest.status == "APPROVED"
    assert manifest.weight_path == (tmp_path / "model.onnx").resolve()
    assert manifest.classes[0].code == "CRACK"
    assert manifest.output_activation == "LOGITS"
    assert manifest.foreground_polarity == "HIGH_PROBABILITY"
    assert manifest.input.interpolation == "BILINEAR"


def test_official_model_contract_supports_low_probability_and_lanczos(tmp_path):
    manifest = load_model_manifest(
        _write_manifest(
            tmp_path,
            output_activation="LOGITS",
            foreground_polarity="LOW_PROBABILITY",
            interpolation="LANCZOS",
        ),
        tmp_path,
    )

    assert manifest.foreground_polarity == "LOW_PROBABILITY"
    assert manifest.input.interpolation == "LANCZOS"


def test_probability_output_activation_is_supported(tmp_path):
    manifest = load_model_manifest(
        _write_manifest(tmp_path, output_activation="PROBABILITY"),
        tmp_path,
    )

    assert manifest.output_activation == "PROBABILITY"


@pytest.mark.parametrize(
    ("field", "value", "message"),
    [
        ("output_activation", "SOFTMAX", "outputActivation"),
        ("foreground_polarity", "DARK", "foregroundPolarity"),
        ("interpolation", "NEAREST", "input.interpolation"),
    ],
)
def test_unknown_inference_contract_value_is_rejected(
    tmp_path, field, value, message
):
    path = _write_manifest(tmp_path, **{field: value})

    with pytest.raises(ModelManifestError, match=message):
        load_model_manifest(path, tmp_path)


def test_candidate_manifest_cannot_enter_real_route(tmp_path):
    path = _write_manifest(tmp_path, status="CANDIDATE")

    with pytest.raises(ModelManifestError, match="APPROVED"):
        load_model_manifest(path, tmp_path)


def test_weight_digest_mismatch_is_rejected(tmp_path):
    path = _write_manifest(tmp_path, sha256="0" * 64)

    with pytest.raises(ModelManifestError, match="SHA-256"):
        load_model_manifest(path, tmp_path)


@pytest.mark.parametrize("weight_file", ["../outside.onnx", "/tmp/outside.onnx"])
def test_weight_path_must_stay_inside_model_root(tmp_path, weight_file):
    path = _write_manifest(tmp_path, weight_file=weight_file)

    with pytest.raises(ModelManifestError, match="模型根目录"):
        load_model_manifest(path, tmp_path)
