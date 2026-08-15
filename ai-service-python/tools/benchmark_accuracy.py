"""PRECISION vs ACCURACY 本地视觉多模型 A/B 基准。"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

PROJECT_ROOT = Path(__file__).resolve().parents[2]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def _get(value: Any, name: str, default=None):
    if value is None:
        return default
    if isinstance(value, dict):
        return value.get(name, default)
    return getattr(value, name, default)


def _enum_value(value: Any):
    return getattr(value, "value", value)


def _percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * q))))
    return ordered[index]


def _serialize_detection(item: Any) -> dict:
    if isinstance(item, dict):
        return json.loads(json.dumps(item, ensure_ascii=False, default=str))
    if hasattr(item, "model_dump"):
        return item.model_dump(mode="json")
    box = _get(item, "boundingBox")
    segmentation = _get(item, "segmentation")
    return {
        "classCode": str(_get(item, "classCode", "")),
        "className": _get(item, "className"),
        "confidence": float(_get(item, "confidence", 0.0) or 0.0),
        "boundingBox": {
            "x": float(_get(box, "x", 0.0)),
            "y": float(_get(box, "y", 0.0)),
            "width": float(_get(box, "width", 0.0)),
            "height": float(_get(box, "height", 0.0)),
            "coordinateType": str(_enum_value(_get(box, "coordinateType", "NORMALIZED_XYWH"))),
        },
        "segmentation": segmentation,
        "trustLevel": _enum_value(_get(item, "trustLevel")),
        "trustReasons": list(_get(item, "trustReasons", []) or []),
        "diagnostics": dict(_get(item, "diagnostics", {}) or {}),
    }


def _serialize_gate(gate: Any) -> dict:
    classes = _get(gate, "classes", {}) or {}
    return {
        str(code): {
            "present": _get(decision, "present"),
            "confidence": round(float(_get(decision, "confidence", 0.0) or 0.0), 4),
        }
        for code, decision in sorted(classes.items())
    }


def serialize_rows(rows: list[dict]) -> list[dict]:
    output: list[dict] = []
    for row in rows:
        item = {
            "file": row.get("file"),
            "durationMs": round(float(row.get("durationMs", 0.0)), 1),
            "detections": [_serialize_detection(det) for det in row.get("detections", [])],
        }
        if row.get("error"):
            item["error"] = str(row["error"])
        output.append(item)
    return output


def summarize_rows(rows: list[dict]) -> dict:
    total = 0
    no_detection = 0
    large_boxes = 0
    near_full_cracks = 0
    failures = 0
    durations: list[float] = []
    trust_counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0, "UNSET": 0}
    class_counts: dict[str, int] = {}
    for row in rows:
        durations.append(float(row.get("durationMs", 0.0) or 0.0))
        if row.get("error"):
            failures += 1
        detections = row.get("detections") or []
        if not detections:
            no_detection += 1
        for det in detections:
            total += 1
            code = str(_get(det, "classCode", "UNKNOWN")).upper()
            class_counts[code] = class_counts.get(code, 0) + 1
            box = _get(det, "boundingBox")
            area = float(_get(box, "width", 0.0) or 0.0) * float(_get(box, "height", 0.0) or 0.0)
            if area >= 0.65:
                large_boxes += 1
            if code == "CRACK" and area >= 0.85:
                near_full_cracks += 1
            trust = str(_enum_value(_get(det, "trustLevel")) or "UNSET").upper()
            trust_counts[trust if trust in trust_counts else "UNSET"] += 1
    return {
        "images": len(rows),
        "failures": failures,
        "totalDetections": total,
        "noDetectionImages": no_detection,
        "largeBoxes": large_boxes,
        "nearFullCracks": near_full_cracks,
        "trustCounts": trust_counts,
        "classCounts": dict(sorted(class_counts.items())),
        "p50Ms": round(_percentile(durations, 0.50), 1),
        "p95Ms": round(_percentile(durations, 0.95), 1),
        "maxMs": round(max(durations), 1) if durations else 0.0,
    }


def build_report(
    image_count: int,
    vision_version: str,
    precision_summary: dict,
    accuracy_summary: dict,
    accuracy_batch_wall_ms: float,
    ground_truth_available: bool,
    precision_peak_vram_mib: float | None = None,
    accuracy_peak_vram_mib: float | None = None,
) -> str:
    lines = [
        "# PRECISION vs ACCURACY 视觉推理对比报告",
        "",
        f"> 生成时间：{datetime.now(timezone.utc).isoformat()}",
        f"> 基线模型：AI-VISION-LOCAL-001 v{vision_version}",
        f"> 图片数：{image_count}",
        f"> ACCURACY 整批端到端耗时：{accuracy_batch_wall_ms / 1000.0:.2f} s",
        "",
        "## 无监督运行统计",
        "",
        "| 模式 | Detection | 无检测图 | 大框>=0.65 | 近整幅CRACK>=0.85 | P50(ms) | P95(ms) | 失败 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for label, summary in (("PRECISION", precision_summary), ("ACCURACY", accuracy_summary)):
        lines.append(
            f"| {label} | {summary['totalDetections']} | {summary['noDetectionImages']} | "
            f"{summary['largeBoxes']} | {summary['nearFullCracks']} | {summary['p50Ms']:.1f} | "
            f"{summary['p95Ms']:.1f} | {summary['failures']} |"
        )
    lines += [
        "",
        "## 类别分布",
        "",
        f"- PRECISION：{precision_summary.get('classCounts', {})}",
        f"- ACCURACY：{accuracy_summary.get('classCounts', {})}",
        "",
        "## ACCURACY 可信度分布",
        "",
        f"- HIGH：{accuracy_summary['trustCounts']['HIGH']}",
        f"- MEDIUM：{accuracy_summary['trustCounts']['MEDIUM']}",
        f"- LOW：{accuracy_summary['trustCounts']['LOW']}",
        f"- UNSET：{accuracy_summary['trustCounts']['UNSET']}",
    ]
    if precision_peak_vram_mib is not None:
        lines.append(f"- PRECISION 峰值显存：{precision_peak_vram_mib:.0f} MiB")
    if accuracy_peak_vram_mib is not None:
        lines.append(f"- ACCURACY 峰值显存：{accuracy_peak_vram_mib:.0f} MiB")
    lines += ["", "## 质量指标门禁", ""]
    if ground_truth_available:
        lines.append("GROUND_TRUTH_AVAILABLE：可由独立人工真值工具计算图片级质量指标。")
    else:
        lines += [
            "GROUND_TRUTH_NOT_AVAILABLE",
            "",
            "当前报告只比较候选行为、耗时、显存与错误模式；没有完整人工真值时不得宣称 Accuracy、Precision、Recall 或 F1 提升。",
        ]
    return "\n".join(lines) + "\n"


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _collect_images(image_dir: Path) -> list[Path]:
    if not image_dir.is_dir():
        return []
    return sorted(
        (p for p in image_dir.rglob("*") if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS),
        key=lambda p: p.relative_to(image_dir).as_posix(),
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="PRECISION vs ACCURACY RTX3060 视觉对比")
    parser.add_argument("--image-dir", default="data/validation/ai-vision/demo")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--vision-version", default="1.1.0")
    parser.add_argument("--limit", type=int, default=0, help="仅测试前 N 张；0 表示全部")
    parser.add_argument("--qwen-max-side", type=int, default=1024)
    parser.add_argument("--report", default="data/model-benchmarks/vision-accuracy-comparison.md")
    parser.add_argument("--details", default="data/model-benchmarks/vision-accuracy-comparison.json")
    parser.add_argument("--save-annotated", action="store_true")
    parser.add_argument("--annotated-dir", default="data/model-benchmarks/annotated-accuracy")
    args = parser.parse_args()

    image_dir = _resolve(args.image_dir)
    model_root = _resolve(args.model_root)
    report_path = _resolve(args.report)
    details_path = _resolve(args.details)
    annotated_dir = _resolve(args.annotated_dir)
    files = _collect_images(image_dir)
    if args.limit > 0:
        files = files[: args.limit]
    if not files:
        print(f"没有可评估图片：{image_dir}", file=sys.stderr)
        return 2

    os.environ.setdefault("URBAN_SAFE_AI_MODEL_ROOT", str(model_root))
    os.environ.setdefault("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    os.environ.setdefault("URBAN_SAFE_AI_VISUAL_DEVICE", "cuda")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_DTYPE", "float16")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_OFFLINE", "true")
    os.environ.setdefault("HF_HUB_OFFLINE", "1")

    import torch
    from PIL import Image, ImageOps
    from app.accuracy_inference import AccuracyBatchRunner, _release_grounded
    from app.adapters.florence2_locator import Florence2Locator
    from app.adapters.grounded_sam2 import GroundedSam2TinyAdapter
    from app.adapters.qwen3_vl_classifier import Qwen3VlClassifier
    from app.config import Settings
    from app.image import decode_image
    from app.inference import _run_precision
    from app.model_manifest import load_model_manifest
    from tools.download_accuracy_models import accuracy_model_paths, check_model_dir

    if not torch.cuda.is_available():
        print("CUDA 不可用，无法运行 ACCURACY A/B。", file=sys.stderr)
        return 2

    accuracy_paths = accuracy_model_paths(model_root)
    for label, path in (("Qwen3-VL", accuracy_paths.qwen), ("Florence-2", accuracy_paths.florence)):
        ok, missing = check_model_dir(path)
        if not ok:
            print(f"{label} 权重不完整：{path}；缺少 {', '.join(missing)}。请先运行 download_accuracy_models。", file=sys.stderr)
            return 2

    settings = Settings()
    manifest_path = model_root / "AI-VISION-LOCAL-001" / args.vision_version / "manifest.json"
    if not manifest_path.is_file():
        print(f"视觉基线 manifest 不存在：{manifest_path}", file=sys.stderr)
        return 2
    manifest = load_model_manifest(manifest_path, model_root)

    precision_adapter = GroundedSam2TinyAdapter(manifest, settings, require_approved=False)
    precision_rows: list[dict] = []
    torch.cuda.reset_peak_memory_stats()
    try:
        for path in files:
            decoded = decode_image(path.read_bytes(), settings)
            torch.cuda.synchronize()
            started = time.monotonic()
            detections = _run_precision(precision_adapter, decoded)[1]
            torch.cuda.synchronize()
            precision_rows.append({
                "file": path.relative_to(image_dir).as_posix(),
                "durationMs": (time.monotonic() - started) * 1000.0,
                "detections": detections,
            })
    finally:
        precision_peak = torch.cuda.max_memory_allocated() / 1024 / 1024
        _release_grounded(precision_adapter)

    pil_images = []
    for path in files:
        with Image.open(path) as opened:
            pil_images.append(ImageOps.exif_transpose(opened).convert("RGB").copy())

    runner = AccuracyBatchRunner(
        qwen_factory=lambda: Qwen3VlClassifier(
            accuracy_paths.qwen, device="cuda", max_side=args.qwen_max_side
        ),
        grounded_factory=lambda: GroundedSam2TinyAdapter(manifest, settings, require_approved=False),
        florence_factory=lambda: Florence2Locator(accuracy_paths.florence, device="cuda"),
    )
    torch.cuda.reset_peak_memory_stats()
    accuracy_started = time.monotonic()
    try:
        accuracy_detection_rows = runner.run_batch(pil_images)
        torch.cuda.synchronize()
    except Exception as ex:
        print(f"ACCURACY 批处理失败：{ex}", file=sys.stderr)
        return 3
    accuracy_wall = (time.monotonic() - accuracy_started) * 1000.0
    accuracy_peak = torch.cuda.max_memory_allocated() / 1024 / 1024
    durations = runner.last_image_durations_ms or [accuracy_wall / len(files)] * len(files)
    accuracy_rows = [
        {
            "file": path.relative_to(image_dir).as_posix(),
            "durationMs": durations[index],
            "detections": accuracy_detection_rows[index],
        }
        for index, path in enumerate(files)
    ]

    if args.save_annotated:
        from tools.visualize_detections import save_annotated_image

        for row, path in zip(precision_rows, files, strict=True):
            relative = path.relative_to(image_dir)
            save_annotated_image(path, row["detections"], annotated_dir / "precision" / relative, "PRECISION")
        for row, path in zip(accuracy_rows, files, strict=True):
            relative = path.relative_to(image_dir)
            save_annotated_image(path, row["detections"], annotated_dir / "accuracy" / relative, "ACCURACY")

    precision_summary = summarize_rows(precision_rows)
    accuracy_summary = summarize_rows(accuracy_rows)
    report = build_report(
        image_count=len(files),
        vision_version=args.vision_version,
        precision_summary=precision_summary,
        accuracy_summary=accuracy_summary,
        accuracy_batch_wall_ms=runner.last_batch_wall_ms or accuracy_wall,
        ground_truth_available=False,
        precision_peak_vram_mib=precision_peak,
        accuracy_peak_vram_mib=accuracy_peak,
    )
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

    qwen_gates = [
        {
            "file": path.relative_to(image_dir).as_posix(),
            "classes": _serialize_gate(gate),
        }
        for path, gate in zip(files, runner.last_semantic_gates, strict=True)
    ]
    details = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "baselineModelId": "AI-VISION-LOCAL-001",
        "visionVersion": args.vision_version,
        "imageCount": len(files),
        "qwenMaxSide": args.qwen_max_side,
        "precisionSummary": precision_summary,
        "accuracySummary": accuracy_summary,
        "accuracyBatchWallMs": round(runner.last_batch_wall_ms or accuracy_wall, 1),
        "accuracyStageDurationsMs": runner.last_stage_durations_ms,
        "qwenSemanticGates": qwen_gates,
        "precision": serialize_rows(precision_rows),
        "accuracy": serialize_rows(accuracy_rows),
    }
    details_path.parent.mkdir(parents=True, exist_ok=True)
    details_path.write_text(json.dumps(details, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(report)
    print(f"报告已写入：{report_path}")
    print(f"逐图明细已写入：{details_path}")
    if args.save_annotated:
        print(f"检测后图片已写入：{annotated_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
