"""零样本视觉模型清单加载与准入校验测试（无需真实权重，可离线运行）。"""

from __future__ import annotations

import json

import pytest

from app.model_manifest import ModelManifestError, load_model_manifest
from app.vision_manifest import load_zero_shot_manifest

DETECTOR_SHA = "a2bb814dd30d776dcf7e30523b00659f4f141c71"
SEGMENTER_SHA = "de431c4043854a71d8101e17995dfe596bf101a5"


def _payload() -> dict:
    return {
        "schemaVersion": 1,
        "modelId": "AI-VISION-LOCAL-001",
        "modelName": "Test Vision Model",
        "version": "1.0.0",
        "status": "CANDIDATE",
        "identityVerified": False,
        "task": "ZERO_SHOT_VISUAL_DEFECT",
        "adapter": "grounded-sam2-tiny-v1",
        "source": {
            "type": "ZERO_SHOT_OPEN_WEIGHTS",
            "repository": "modelscope",
            "revision": f"{DETECTOR_SHA}/{SEGMENTER_SHA}",
            "license": "Apache-2.0",
        },
        "classes": [
            {"code": "CRACK", "name": "疑似裂缝", "prompts": ["wall crack", "concrete crack"]},
            {"code": "SPALLING", "name": "疑似剥落", "prompts": ["concrete spalling"]},
        ],
        "checkpoint": {
            "detectorRepository": "IDEA-Research/grounding-dino-tiny",
            "segmenterRepository": "facebook/sam2.1-hiera-tiny",
            "detectorRevision": DETECTOR_SHA,
            "segmenterRevision": SEGMENTER_SHA,
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
        "approvedBy": "",
        "approvedAt": "",
    }


def _write(root, payload: dict) -> str:
    package = root / payload["modelId"] / payload["version"]
    package.mkdir(parents=True)
    path = package / "manifest.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return str(path)


def test_load_valid_candidate(tmp_path):
    manifest = load_zero_shot_manifest(_write(tmp_path, _payload()), tmp_path)
    assert manifest.model_id == "AI-VISION-LOCAL-001"
    assert manifest.status == "CANDIDATE"
    assert manifest.adapter == "grounded-sam2-tiny-v1"
    assert manifest.checkpoint.detector_revision == DETECTOR_SHA
    assert manifest.checkpoint.segmenter_revision == SEGMENTER_SHA
    assert manifest.checkpoint.sha256 == "a" * 64
    assert len(manifest.classes) == 2
    assert manifest.classes[0].code == "CRACK"
    assert manifest.classes[0].prompts == ("wall crack", "concrete crack")


def test_load_valid_approved(tmp_path):
    payload = _payload()
    payload["status"] = "APPROVED"
    payload["identityVerified"] = True
    payload["approvedBy"] = "T-REVIEWER"
    payload["approvedAt"] = "2026-08-11T00:00:00Z"
    manifest = load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)
    assert manifest.status == "APPROVED"
    assert manifest.identity_verified is True
    assert manifest.approved_by == "T-REVIEWER"


def test_candidate_without_identity_is_allowed(tmp_path):
    # CANDIDATE 允许 identityVerified=false（可能尚未联网身份校验）。
    manifest = load_zero_shot_manifest(_write(tmp_path, _payload()), tmp_path)
    assert manifest.status == "CANDIDATE"
    assert manifest.identity_verified is False


def test_approved_without_identity_rejected(tmp_path):
    payload = _payload()
    payload["status"] = "APPROVED"
    payload["identityVerified"] = False
    payload["approvedBy"] = "T-REVIEWER"
    payload["approvedAt"] = "2026-08-11T00:00:00Z"
    with pytest.raises(ModelManifestError, match="identityVerified"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_load_model_manifest_dispatches_vision(tmp_path):
    manifest = load_model_manifest(_write(tmp_path, _payload()), tmp_path)
    assert manifest.adapter == "grounded-sam2-tiny-v1"


def test_revision_main_rejected(tmp_path):
    payload = _payload()
    payload["checkpoint"]["detectorRevision"] = "main"
    with pytest.raises(ModelManifestError, match="固定 40 位 commit SHA"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_revision_short_hash_rejected(tmp_path):
    payload = _payload()
    payload["checkpoint"]["segmenterRevision"] = "a2bb814"
    with pytest.raises(ModelManifestError, match="固定 40 位 commit SHA"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_approved_with_pending_approver_rejected(tmp_path):
    payload = _payload()
    payload["status"] = "APPROVED"
    payload["identityVerified"] = True
    payload["approvedBy"] = "PENDING-HUMAN-REVIEW"
    payload["approvedAt"] = "2026-08-11T00:00:00Z"
    with pytest.raises(ModelManifestError, match="真实审批人"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_approved_with_pending_time_rejected(tmp_path):
    payload = _payload()
    payload["status"] = "APPROVED"
    payload["identityVerified"] = True
    payload["approvedBy"] = "T-REVIEWER"
    payload["approvedAt"] = "PENDING"
    with pytest.raises(ModelManifestError, match="审批时间"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_unknown_status_rejected(tmp_path):
    payload = _payload()
    payload["status"] = "REJECTED"
    with pytest.raises(ModelManifestError, match="CANDIDATE 或 APPROVED"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_license_must_be_verified(tmp_path):
    payload = _payload()
    payload["source"]["license"] = "UNKNOWN"
    with pytest.raises(ModelManifestError, match="许可证"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_sha256_must_be_valid_hex(tmp_path):
    payload = _payload()
    payload["checkpoint"]["sha256"] = "not-a-valid-hash"
    with pytest.raises(ModelManifestError, match="sha256"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_adapter_mismatch_rejected(tmp_path):
    payload = _payload()
    payload["adapter"] = "onnx-crack-segmentation-v1"
    with pytest.raises(ModelManifestError):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_path_outside_model_root_rejected(tmp_path, tmp_path_factory):
    other = tmp_path_factory.mktemp("other-root")
    path = _write(other, _payload())
    with pytest.raises(ModelManifestError, match="模型根目录内"):
        load_zero_shot_manifest(path, tmp_path)


def test_missing_classes_rejected(tmp_path):
    payload = _payload()
    payload["classes"] = []
    with pytest.raises(ModelManifestError, match="类别"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_empty_prompts_rejected(tmp_path):
    payload = _payload()
    payload["classes"][0]["prompts"] = []
    with pytest.raises(ModelManifestError, match="prompts"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_missing_checkpoint_rejected(tmp_path):
    payload = _payload()
    del payload["checkpoint"]
    with pytest.raises(ModelManifestError, match="checkpoint"):
        load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)


def test_neutral_adapter_id_grounded_sam2_v1_accepted(tmp_path):
    payload = _payload()
    payload["adapter"] = "grounded-sam2-v1"
    manifest = load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)
    assert manifest.adapter == "grounded-sam2-v1"


def test_legacy_adapter_id_tiny_accepted(tmp_path):
    payload = _payload()
    payload["adapter"] = "grounded-sam2-tiny-v1"
    manifest = load_zero_shot_manifest(_write(tmp_path, payload), tmp_path)
    assert manifest.adapter == "grounded-sam2-tiny-v1"
