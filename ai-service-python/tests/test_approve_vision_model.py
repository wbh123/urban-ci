"""approve_vision_model 门禁测试（identityVerified + benchmark 全 marker）。"""

from __future__ import annotations

import json
import sys

from app.model_digest import combine_digests, dir_digest
from tools import approve_vision_model

DETECTOR_SHA = "a2bb814dd30d776dcf7e30523b00659f4f141c71"
SEGMENTER_SHA = "de431c4043854a71d8101e17995dfe596bf101a5"


def _write_dirs(root) -> str:
    detector = root / "AI-VISION-LOCAL-001/1.0.0/detector"
    segmenter = root / "AI-VISION-LOCAL-001/1.0.0/segmenter"
    detector.mkdir(parents=True)
    segmenter.mkdir(parents=True)
    (detector / "model.safetensors").write_bytes(b"detector-weights")
    (detector / "config.json").write_bytes(b"{}")
    (segmenter / "model.safetensors").write_bytes(b"segmenter-weights")
    d_sha, _ = dir_digest(detector)
    s_sha, _ = dir_digest(segmenter)
    return combine_digests(d_sha, s_sha)


def _manifest(root, *, identity_verified: bool) -> str:
    package = root / "AI-VISION-LOCAL-001/1.0.0"
    package.mkdir(parents=True)
    payload = {
        "schemaVersion": 1,
        "modelId": "AI-VISION-LOCAL-001",
        "modelName": "Test Vision",
        "version": "1.0.0",
        "status": "CANDIDATE",
        "identityVerified": identity_verified,
        "task": "ZERO_SHOT_VISUAL_DEFECT",
        "adapter": "grounded-sam2-tiny-v1",
        "source": {
            "type": "ZERO_SHOT_OPEN_WEIGHTS",
            "repository": "modelscope",
            "revision": f"{DETECTOR_SHA}/{SEGMENTER_SHA}",
            "license": "Apache-2.0",
        },
        "classes": [{"code": "CRACK", "name": "疑似裂缝", "prompts": ["wall crack"]}],
        "checkpoint": {
            "detectorRepository": "IDEA-Research/grounding-dino-tiny",
            "segmenterRepository": "facebook/sam2.1-hiera-tiny",
            "detectorRevision": DETECTOR_SHA,
            "segmenterRevision": SEGMENTER_SHA,
            "detectorDir": "AI-VISION-LOCAL-001/1.0.0/detector",
            "segmenterDir": "AI-VISION-LOCAL-001/1.0.0/segmenter",
            "sha256": _write_dirs(root),
            "sizeBytes": 1024,
        },
        "input": {"maxLongSide": 1280, "boxThreshold": 0.25, "textThreshold": 0.25, "maxDetections": 10},
        "license": "Apache-2.0",
        "approvedBy": "",
        "approvedAt": "",
    }
    path = package / "manifest.json"
    path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    return str(path)


_FULL_REPORT = """# RTX 3060 6GB 本地视觉模型基准报告

## Runtime
- CUDA_PASS：PASS
- DINO_FORWARD_PASS：PASS
- SAM2_FORWARD_PASS：PASS
- API_PASS：PASS
- SAM2_BOX_PROMPT：USED

## Output Contract
- DETECTION_SCHEMA_PASS：PASS
- SEGMENTATION_SCHEMA_PASS：PASS

## 性能
- 成功：20，失败：0
- OOM 降级次数：0
"""


def _write_report(root, text: str) -> str:
    path = root / "rtx3060-6g-report.md"
    path.write_text(text, encoding="utf-8")
    return str(path)


def _run_approve(monkeypatch, root, report, extra=None):
    args = [
        "approve", "--model-root", str(root), "--version", "1.0.0",
        "--report", report, "--approver", "T-REVIEWER",
    ]
    if extra:
        args.extend(extra)
    monkeypatch.setattr(sys, "argv", args)
    return approve_vision_model.main()


def test_refuse_when_identity_not_verified(tmp_path, monkeypatch):
    _manifest(tmp_path, identity_verified=False)
    assert _run_approve(monkeypatch, tmp_path, _write_report(tmp_path, _FULL_REPORT)) == 2


def test_refuse_when_benchmark_missing_marker(tmp_path, monkeypatch):
    _manifest(tmp_path, identity_verified=True)
    report = _write_report(tmp_path, _FULL_REPORT.replace("API_PASS：PASS", ""))
    assert _run_approve(monkeypatch, tmp_path, report) == 2


def test_refuse_when_sam2_point_fallback(tmp_path, monkeypatch):
    _manifest(tmp_path, identity_verified=True)
    report = _write_report(
        tmp_path, _FULL_REPORT.replace("SAM2_BOX_PROMPT：USED", "SAM2_BOX_PROMPT：FALLBACK_TO_POINT")
    )
    assert _run_approve(monkeypatch, tmp_path, report) == 2
    assert _run_approve(monkeypatch, tmp_path, report, extra=["--allow-sam2-point-fallback"]) == 0


def test_approve_success_with_full_markers_and_backup(tmp_path, monkeypatch):
    _manifest(tmp_path, identity_verified=True)
    old_catalog = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": "AI-VISION-LOCAL-001",
        "models": [{
            "modelId": "AI-VISION-LOCAL-001",
            "version": "0.9.0",
            "manifestPath": "AI-VISION-LOCAL-001/0.9.0/manifest.json",
            "enabled": True,
        }],
    }
    (tmp_path / "runtime-catalog.json").write_text(json.dumps(old_catalog), encoding="utf-8")
    report = _write_report(tmp_path, _FULL_REPORT)
    assert _run_approve(monkeypatch, tmp_path, report) == 0

    payload = json.loads((tmp_path / "AI-VISION-LOCAL-001/1.0.0/manifest.json").read_text())
    assert payload["status"] == "APPROVED"
    catalog = json.loads((tmp_path / "runtime-catalog.json").read_text())
    assert catalog["models"][0]["version"] == "1.0.0"
    assert catalog["models"][0]["enabled"] is True
    previous = json.loads((tmp_path / "runtime-catalog.previous.json").read_text())
    assert previous["models"][0]["version"] == "0.9.0"
