"""人工批准零样本视觉模型：CANDIDATE → APPROVED，并切换 active runtime catalog。

精度优先 Base/Base+ 使用 AI-VISION-LOCAL-001/1.1.0；批准前不会覆盖当前 active catalog。
批准成功时先备份现有 runtime-catalog.json 为 runtime-catalog.previous.json，再原子写入新 catalog。
"""

from __future__ import annotations

import argparse
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

from app.model_digest import combine_digests, dir_digest
from app.vision_manifest import load_zero_shot_manifest

PROJECT_ROOT = Path(__file__).resolve().parents[2]
MODEL_ID = "AI-VISION-LOCAL-001"
DEFAULT_VERSION = "1.1.0"

BASE_BENCHMARK_MARKERS = (
    "CUDA_PASS：PASS",
    "DINO_FORWARD_PASS：PASS",
    "SAM2_FORWARD_PASS：PASS",
    "API_PASS：PASS",
    "DETECTION_SCHEMA_PASS：PASS",
    "SEGMENTATION_SCHEMA_PASS：PASS",
    "失败：0",
    "OOM 降级次数：0",
)
SAM2_MARKER_USED = "SAM2_BOX_PROMPT：USED"
SAM2_MARKER_FALLBACK = "SAM2_BOX_PROMPT：FALLBACK_TO_POINT"


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _benchmark_pass(report: Path, allow_sam2_point_fallback: bool) -> bool:
    if not report.is_file():
        return False
    text = report.read_text(encoding="utf-8")
    if not all(marker in text for marker in BASE_BENCHMARK_MARKERS):
        return False
    if allow_sam2_point_fallback:
        return SAM2_MARKER_USED in text or SAM2_MARKER_FALLBACK in text
    return SAM2_MARKER_USED in text


def _activate_catalog(model_root: Path, version: str) -> None:
    catalog_path = model_root / "runtime-catalog.json"
    previous_path = model_root / "runtime-catalog.previous.json"
    if catalog_path.is_file():
        shutil.copy2(catalog_path, previous_path)

    catalog = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": MODEL_ID,
        "models": [{
            "modelId": MODEL_ID,
            "version": version,
            "manifestPath": f"{MODEL_ID}/{version}/manifest.json",
            "enabled": True,
        }],
    }
    temporary = model_root / "runtime-catalog.json.tmp"
    temporary.write_text(json.dumps(catalog, ensure_ascii=False, indent=2), encoding="utf-8")
    temporary.replace(catalog_path)


def main() -> int:
    parser = argparse.ArgumentParser(description="人工批准零样本视觉模型并切换 active runtime")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--version", default=DEFAULT_VERSION)
    parser.add_argument("--approver", required=True, help="执行批准的人工姓名/工号")
    parser.add_argument("--report", default="data/model-benchmarks/rtx3060-6g-report.md")
    parser.add_argument("--allow-sam2-point-fallback", action="store_true")
    args = parser.parse_args()

    if not args.approver.strip():
        print("必须提供 --approver，禁止自动批准。", file=sys.stderr)
        return 2

    model_root = _resolve(args.model_root)
    report = _resolve(args.report)
    manifest_path = model_root / MODEL_ID / args.version / "manifest.json"
    if not manifest_path.is_file():
        print(f"manifest 不存在：{manifest_path}", file=sys.stderr)
        return 2

    manifest = load_zero_shot_manifest(manifest_path, model_root)
    if manifest.status != "CANDIDATE":
        print(f"模型状态为 {manifest.status}，只有 CANDIDATE 需要批准。", file=sys.stderr)
        return 2
    if manifest.version != args.version:
        print("manifest version 与 --version 不一致。", file=sys.stderr)
        return 2
    if not manifest.identity_verified:
        print("模型身份尚未通过固定 revision 权重校验，禁止 APPROVED。", file=sys.stderr)
        return 2

    try:
        detector_sha, _ = dir_digest(manifest.checkpoint.detector_dir)
        segmenter_sha, _ = dir_digest(manifest.checkpoint.segmenter_dir)
    except OSError as ex:
        print(f"权重目录不可读：{ex}", file=sys.stderr)
        return 2
    if combine_digests(detector_sha, segmenter_sha) != manifest.weight_sha256:
        print("SHA 校验失败：本地权重摘要与 manifest 不一致，拒绝批准。", file=sys.stderr)
        return 2

    if not _benchmark_pass(report, args.allow_sam2_point_fallback):
        print(f"GPU benchmark 未通过完整门禁（{report}），拒绝批准。", file=sys.stderr)
        return 2

    approved_at = datetime.now(timezone.utc).isoformat()
    payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    payload["status"] = "APPROVED"
    payload["approvedBy"] = args.approver.strip()
    payload["approvedAt"] = approved_at
    manifest_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    _activate_catalog(model_root, args.version)

    print(f"批准完成：{MODEL_ID} v{args.version} → APPROVED")
    print(f"  approvedBy：{args.approver.strip()}")
    print(f"  approvedAt：{approved_at}")
    print("  active runtime-catalog.json 已切换")
    print("  旧 active catalog 已备份为 runtime-catalog.previous.json，可用于回滚")
    return 0


if __name__ == "__main__":
    sys.exit(main())
