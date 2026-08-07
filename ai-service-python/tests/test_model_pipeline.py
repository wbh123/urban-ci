"""本地模型流水线中不依赖深度学习框架的回归测试。"""
from __future__ import annotations

import json
import sys
from types import SimpleNamespace

import numpy as np
import pytest

from tools import model_pipeline, pipeline_evaluation, pipeline_model
from training.split import split_group_names


def test_calculate_metrics_counts_positive_and_negative_images():
    predictions = [
        (
            np.asarray([[0.9, 0.1], [0.8, 0.1]], dtype=np.float32),
            np.asarray([[True, False], [True, False]]),
        ),
        (
            np.asarray([[0.1, 0.2], [0.1, 0.2]], dtype=np.float32),
            np.asarray([[False, False], [False, False]]),
        ),
    ]
    metrics = model_pipeline._calculate_metrics(predictions, threshold=0.5)
    assert metrics == {
        "pixelF1": 1.0,
        "iou": 1.0,
        "imageRecall": 1.0,
        "falsePositiveImageRate": 0.0,
    }


def test_update_env_file_replaces_values_and_keeps_backup(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "URBAN_SAFE_AI_DEFAULT_MODE=MOCK\nURBAN_SAFE_AI_REAL_MODEL_STATUS=UNAVAILABLE\n",
        encoding="utf-8",
    )
    model_pipeline._update_env_file(
        env_file,
        {
            "URBAN_SAFE_AI_DEFAULT_MODE": "REAL",
            "URBAN_SAFE_AI_REAL_MODEL_STATUS": "APPROVED",
            "URBAN_SAFE_AI_MODEL_ROOT": "/tmp/models",
        },
    )
    content = env_file.read_text(encoding="utf-8")
    assert "URBAN_SAFE_AI_DEFAULT_MODE=REAL" in content
    assert "URBAN_SAFE_AI_REAL_MODEL_STATUS=APPROVED" in content
    assert "URBAN_SAFE_AI_MODEL_ROOT=/tmp/models" in content
    assert len(list(tmp_path.glob(".env.backup-*"))) == 1


def test_update_env_file_removes_deprecated_runtime_keys(tmp_path):
    env_file = tmp_path / ".env"
    env_file.write_text(
        "\n".join(
            [
                "URBAN_SAFE_AI_REAL_MODEL_STATUS=APPROVED",
                "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH=AI-OLD/1.0.0/manifest.json",
                "URBAN_SAFE_AI_ONNX_EXECUTION_PROVIDERS=CUDAExecutionProvider,CPUExecutionProvider",
                "URBAN_SAFE_AI_ONNX_REQUIRE_GPU=true",
                "URBAN_SAFE_AI_DEFAULT_MODE=MOCK",
            ]
        )
        + "\n",
        encoding="utf-8",
    )

    model_pipeline._update_env_file(
        env_file,
        {"URBAN_SAFE_AI_DEFAULT_MODE": "REAL"},
        remove_keys={
            "URBAN_SAFE_AI_REAL_MODEL_STATUS",
            "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH",
            "URBAN_SAFE_AI_ONNX_EXECUTION_PROVIDERS",
            "URBAN_SAFE_AI_ONNX_REQUIRE_GPU",
        },
    )

    content = env_file.read_text(encoding="utf-8")
    assert "URBAN_SAFE_AI_DEFAULT_MODE=REAL" in content
    assert "URBAN_SAFE_AI_REAL_MODEL_STATUS" not in content
    assert "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH" not in content
    assert "URBAN_SAFE_AI_ONNX_EXECUTION_PROVIDERS" not in content
    assert "URBAN_SAFE_AI_ONNX_REQUIRE_GPU" not in content


def test_candidate_manifest_records_weight_digest(tmp_path):
    (tmp_path / model_pipeline.MODEL_FILENAME).write_bytes(b"onnx-test")
    path = model_pipeline._write_candidate_manifest(
        output_dir=tmp_path,
        model_id="AI-CRACK-TEST-001",
        model_name="测试裂缝模型",
        version="1.0.0",
        source_type="TRAINED",
        source_repository="mendeley:jwsn7tfbrp/1",
        source_revision="dataset-v1",
        source_license="CC-BY-4.0",
        model_license="PROJECT-TRAINED",
        input_size=640,
        mask_threshold=0.5,
        min_component_pixels=16,
    )
    payload = json.loads(path.read_text(encoding="utf-8"))
    assert payload["status"] == "CANDIDATE"
    assert payload["adapter"] == "onnx-crack-segmentation-v1"
    assert payload["weightSha256"] == model_pipeline._sha256(
        tmp_path / model_pipeline.MODEL_FILENAME
    )
    assert payload["input"]["width"] == 640
    assert payload["foregroundPolarity"] == "HIGH_PROBABILITY"
    assert payload["input"]["interpolation"] == "BILINEAR"


def test_hf_registry_metadata_is_normalized_for_export(tmp_path):
    source_dir = tmp_path / "source"
    source_dir.mkdir()
    weights = source_dir / "unet_model_weights.pth"
    weights.write_bytes(b"weights")
    digest = model_pipeline._sha256(weights)
    (source_dir / "source.json").write_text(
        json.dumps(
            {
                "schemaVersion": 1,
                "provider": "HUGGING_FACE",
                "resourceType": "MODEL_SNAPSHOT",
                "resourceId": "samir-mohamed/concrete-crack-segmentation",
                "resolvedRevision": "55b4933b417822f8dda632cca19e391406d0bc7e",
                "license": "MIT",
                "files": [
                    {
                        "path": "unet_model_weights.pth",
                        "sizeBytes": len(b"weights"),
                        "sha256": digest,
                    }
                ],
            }
        ),
        encoding="utf-8",
    )

    metadata = model_pipeline._read_hf_source_metadata(source_dir)

    assert metadata["repository"] == "samir-mohamed/concrete-crack-segmentation"
    assert metadata["resolvedRevision"] == "55b4933b417822f8dda632cca19e391406d0bc7e"
    assert metadata["license"] == "MIT"
    assert metadata["weightSha256"] == digest


def test_float_range_is_inclusive_and_stable():
    assert model_pipeline._float_range(0.3, 0.5, 0.1) == [0.3, 0.4, 0.5]


def test_three_groups_produce_non_empty_disjoint_splits():
    train, val, test = split_group_names(["g1", "g2", "g3"], seed=7)
    assert len(train) == len(val) == len(test) == 1
    assert not train & val and not train & test and not val & test


def test_promote_restores_candidate_when_runtime_validation_fails(tmp_path, monkeypatch):
    package = tmp_path / "package"
    package.mkdir()
    weight = package / model_pipeline.MODEL_FILENAME
    weight.write_bytes(b"fake-onnx")
    manifest = model_pipeline._write_candidate_manifest(
        output_dir=package,
        model_id="AI-CRACK-TEST-001",
        model_name="测试裂缝模型",
        version="1.0.0",
        source_type="TRAINED",
        source_repository="local",
        source_revision="v1",
        source_license="MIT",
        model_license="MIT",
        input_size=640,
        mask_threshold=0.5,
        min_component_pixels=16,
    )
    evaluation = tmp_path / "test-evaluation.json"
    evaluation.write_text(
        json.dumps(
            {
                "modelId": "AI-CRACK-TEST-001",
                "weightSha256": model_pipeline._sha256(weight),
                "selectedThreshold": 0.5,
                "dataset": "test-v1",
                "inferenceContract": {
                    "outputActivation": "LOGITS",
                    "foregroundPolarity": "HIGH_PROBABILITY",
                    "interpolation": "BILINEAR",
                },
                "metrics": {
                    "pixelF1": 0.9,
                    "iou": 0.8,
                    "imageRecall": 0.95,
                    "falsePositiveImageRate": 0.1,
                },
            }
        ),
        encoding="utf-8",
    )
    monkeypatch.setattr(
        pipeline_evaluation,
        "_validate_package_with_runtime",
        lambda _: (_ for _ in ()).throw(RuntimeError("invalid onnx")),
    )
    args = SimpleNamespace(
        package=package,
        evaluation=evaluation,
        approved_by="reviewer",
        approved_at=None,
        minimum_pixel_f1=0.75,
        minimum_iou=0.60,
        minimum_image_recall=0.90,
        maximum_false_positive_image_rate=0.30,
    )
    with pytest.raises(RuntimeError, match="invalid onnx"):
        pipeline_evaluation._command_promote(args)
    assert json.loads(manifest.read_text(encoding="utf-8"))["status"] == "CANDIDATE"
    assert not (package / "evaluation.json").exists()


def test_promote_rejects_evaluation_from_different_inference_contract(tmp_path):
    package = tmp_path / "package"
    package.mkdir()
    weight = package / model_pipeline.MODEL_FILENAME
    weight.write_bytes(b"fake-onnx")
    model_pipeline._write_candidate_manifest(
        output_dir=package,
        model_id="AI-CRACK-TEST-001",
        model_name="测试裂缝模型",
        version="1.0.0",
        source_type="TRAINED",
        source_repository="local",
        source_revision="v1",
        source_license="MIT",
        model_license="MIT",
        input_size=640,
        mask_threshold=0.5,
        min_component_pixels=16,
    )
    evaluation = tmp_path / "evaluation.json"
    evaluation.write_text(
        json.dumps(
            {
                "modelId": "AI-CRACK-TEST-001",
                "weightSha256": model_pipeline._sha256(weight),
                "inferenceContract": {
                    "outputActivation": "LOGITS",
                    "foregroundPolarity": "LOW_PROBABILITY",
                    "interpolation": "BILINEAR",
                },
                "metrics": {},
            }
        ),
        encoding="utf-8",
    )
    args = SimpleNamespace(package=package, evaluation=evaluation)

    with pytest.raises(RuntimeError, match="推理契约"):
        pipeline_evaluation._command_promote(args)


class FakePipelineNode:
    def __init__(self, name, shape=(1, 3, 8, 8)):
        self.name = name
        self.shape = shape
        self.type = "tensor(float)"


class FakePipelineAssignedSubgraph:
    def __init__(self, ep_name):
        self.ep_name = ep_name


class FakePipelineSessionOptions:
    def __init__(self):
        self.entries = {}

    def add_session_config_entry(self, key, value):
        self.entries[key] = value


class FakePipelineSession:
    def __init__(self, graph_providers):
        self.graph_providers = graph_providers
        self.fallback_disabled = False

    def get_providers(self):
        return ["CUDAExecutionProvider", "CPUExecutionProvider"]

    def get_inputs(self):
        return [FakePipelineNode("images", (1, 3, 8, 8))]

    def get_outputs(self):
        return [FakePipelineNode("mask_logits", (1, 1, 8, 8))]

    def disable_fallback(self):
        self.fallback_disabled = True

    def get_provider_graph_assignment_info(self):
        return [FakePipelineAssignedSubgraph(provider) for provider in self.graph_providers]

    def run(self, output_names, inputs):
        return [np.zeros((1, 1, 8, 8), dtype=np.float32)]
