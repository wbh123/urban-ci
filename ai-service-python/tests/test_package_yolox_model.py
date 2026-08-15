from __future__ import annotations

import hashlib
import json
from pathlib import Path

from tools.package_yolox_model import build_approved_yolox_package


def test_build_approved_yolox_package_writes_manifest_with_verified_sha(tmp_path: Path):
    candidate = {
        "schemaVersion": 1,
        "candidateOnly": True,
        "modelId": "AI-BUILDING-YOLOX-001",
        "modelName": "YOLOX-S 建筑病害检测",
        "targetVersion": "1.0.0",
        "status": "CANDIDATE",
        "task": "BUILDING_DEFECT_DETECTION",
        "adapter": "yolox-building-defect-v1",
        "upstream": {
            "repository": "https://github.com/Megvii-BaseDetection/YOLOX",
            "revision": "6ddff4824372906469a7fae2dc3206c7aa4bbaee",
            "license": "Apache-2.0"
        },
        "classes": [
            {"code": "CRACK", "name": "裂缝"}, {"code": "SPALLING", "name": "剥落"},
            {"code": "EXPOSED_REBAR", "name": "钢筋外露"}, {"code": "CORROSION", "name": "锈蚀"},
            {"code": "WATER_SEEPAGE", "name": "渗水"}, {"code": "EFFLORESCENCE", "name": "泛碱"},
            {"code": "WALL_DAMAGE", "name": "墙体破损"}
        ],
        "input": {"width": 640, "height": 640, "padValue": 114},
        "thresholds": {"score": 0.35, "nmsIou": 0.45, "maximumDetections": 30}
    }
    profile = tmp_path / "candidate.json"
    profile.write_text(json.dumps(candidate, ensure_ascii=False), encoding="utf-8")
    weight = tmp_path / "trained.onnx"
    weight.write_bytes(b"real-ish-yolox")
    sha = hashlib.sha256(weight.read_bytes()).hexdigest()
    verification = tmp_path / "verification.json"
    verification.write_text(json.dumps({"modelId": "AI-BUILDING-YOLOX-001", "targetVersion": "1.0.0", "weightSha256": sha, "runtimeContractPassed": True, "eligibleForApprovalReview": True}), encoding="utf-8")

    package = tmp_path / "package"
    manifest = build_approved_yolox_package(candidate_profile=profile, weight_path=weight, verification_path=verification, output_dir=package, approved_by="Local Demo Review", approved_at="2026-08-15T15:00:00Z", metrics_path=None)

    payload = json.loads(manifest.read_text(encoding="utf-8"))
    assert payload["status"] == "APPROVED"
    assert payload["adapter"] == "yolox-building-defect-v1"
    assert payload["weightSha256"] == sha
    assert payload["weightFile"] == "model.onnx"
    assert payload["metrics"]["dataset"] == "DEMO-SMOKE-NOT-INDEPENDENT"
    assert (package / "model.onnx").read_bytes() == weight.read_bytes()
    assert (package / "verification.json").is_file()
