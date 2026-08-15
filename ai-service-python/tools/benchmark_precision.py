"""在同一批本地图片上比较 FAST 与 PRECISION 视觉推理。"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

from tools.visualize_detections import save_annotated_image

PROJECT_ROOT = Path(__file__).resolve().parents[2]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def _enum_value(value):
    return getattr(value, "value", value)


def _percentile(values: list[float], q: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = min(
        len(ordered) - 1,
        max(0, int(round((len(ordered) - 1) * q))),
    )
    return ordered[index]


def summarize_results(rows: list[dict]) -> dict:
    total = 0
    no_detection = 0
    large_boxes = 0
    near_full_cracks = 0
    trust_counts = {"HIGH": 0, "MEDIUM": 0, "LOW": 0, "UNSET": 0}
    class_counts: dict[str, int] = {}
    durations: list[float] = []
    failures = 0
    for row in rows:
        durations.append(float(row.get("durationMs", 0.0)))
        if row.get("error"):
            failures += 1
        detections = row.get("detections") or []
        if not detections:
            no_detection += 1
        for item in detections:
            total += 1
            code = str(item.classCode).upper()
            class_counts[code] = class_counts.get(code, 0) + 1
            box = item.boundingBox
            area = float(box.width) * float(box.height)
            if area >= 0.65:
                large_boxes += 1
            if code == "CRACK" and area >= 0.85:
                near_full_cracks += 1
            trust = _enum_value(getattr(item, "trustLevel", None)) or "UNSET"
            trust = str(trust).upper()
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


def _serialize_detection(item) -> dict:
    if hasattr(item, "model_dump"):
        return item.model_dump(mode="json")
    box = item.boundingBox
    return {
        "classCode": str(item.classCode),
        "className": getattr(item, "className", None),
        "confidence": float(getattr(item, "confidence", 0.0)),
        "boundingBox": {
            "x": float(box.x),
            "y": float(box.y),
            "width": float(box.width),
            "height": float(box.height),
            "coordinateType": str(getattr(box, "coordinateType", "NORMALIZED_XYWH")),
        },
        "trustLevel": _enum_value(getattr(item, "trustLevel", None)),
        "trustReasons": list(getattr(item, "trustReasons", None) or []),
        "diagnostics": dict(getattr(item, "diagnostics", None) or {}),
    }


def serialize_rows(rows: list[dict]) -> list[dict]:
    """把逐图结果转换为可提交的 JSON 明细，便于定位过检来源。"""
    serialized: list[dict] = []
    for row in rows:
        item = {
            "file": row.get("file"),
            "durationMs": round(float(row.get("durationMs", 0.0)), 1),
            "detections": [
                _serialize_detection(det) for det in (row.get("detections") or [])
            ],
        }
        if row.get("error"):
            item["error"] = str(row["error"])
        serialized.append(item)
    return serialized


def build_report(
    model_version: str,
    image_count: int,
    fast_summary: dict,
    precision_summary: dict,
    ground_truth_available: bool,
    peak_vram_mib: float | None = None,
    annotated_dir: str | None = None,
) -> str:
    lines = [
        "# FAST vs PRECISION 视觉推理对比报告",
        "",
        f"> 生成时间：{datetime.now(timezone.utc).isoformat()}",
        f"> 模型：AI-VISION-LOCAL-001 v{model_version}",
        f"> 图片数：{image_count}",
        "",
        "## 无监督运行统计",
        "",
        "| 模式 | Detection | 无检测图 | 大框>=0.65 | 近整幅CRACK>=0.85 | P50(ms) | P95(ms) | 失败 |",
        "|---|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for label, summary in (
        ("FAST", fast_summary),
        ("PRECISION", precision_summary),
    ):
        lines.append(
            f"| {label} | {summary['totalDetections']} | "
            f"{summary['noDetectionImages']} | {summary['largeBoxes']} | "
            f"{summary['nearFullCracks']} | {summary['p50Ms']:.1f} | "
            f"{summary['p95Ms']:.1f} | {summary['failures']} |"
        )
    lines += [
        "",
        "## 类别分布",
        "",
        f"- FAST：{fast_summary.get('classCounts', {})}",
        f"- PRECISION：{precision_summary.get('classCounts', {})}",
        "",
        "## PRECISION 可信度分布",
        "",
        f"- HIGH：{precision_summary['trustCounts']['HIGH']}",
        f"- MEDIUM：{precision_summary['trustCounts']['MEDIUM']}",
        f"- LOW：{precision_summary['trustCounts']['LOW']}",
        f"- UNSET：{precision_summary['trustCounts']['UNSET']}",
    ]
    if peak_vram_mib is not None:
        lines.append(f"- 峰值显存：{peak_vram_mib:.0f} MiB")
    if annotated_dir:
        lines.append(f"- 标注图输出目录：{annotated_dir}")
    lines += ["", "## 质量指标门禁", ""]
    if ground_truth_available:
        lines.append(
            "GROUND_TRUTH_AVAILABLE：可在独立有监督评估工具中计算分类质量指标。"
        )
    else:
        lines += [
            "GROUND_TRUTH_NOT_AVAILABLE",
            "",
            "当前只比较运行行为、大框退化和候选可信度；没有完整人工真值时不得对外宣称准确率、精确率、召回率或 F1 提升。",
        ]
    return "\n".join(lines) + "\n"


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _display_path(path: Path) -> str:
    try:
        return path.relative_to(PROJECT_ROOT).as_posix()
    except ValueError:
        return str(path)


def _collect_images(image_dir: Path) -> list[Path]:
    if not image_dir.is_dir():
        return []
    return sorted(
        (
            p
            for p in image_dir.rglob("*")
            if p.is_file() and p.suffix.lower() in IMAGE_EXTENSIONS
        ),
        key=lambda p: p.relative_to(image_dir).as_posix(),
    )


def _run_one(adapter, decoded, profile: str):
    if profile == "FAST":
        return adapter.predict(decoded)[1]
    from app.inference import _run_precision

    return _run_precision(adapter, decoded)[1]


def main() -> int:
    parser = argparse.ArgumentParser(
        description="FAST vs PRECISION RTX3060 视觉对比"
    )
    parser.add_argument(
        "--image-dir", default="data/validation/ai-vision/demo"
    )
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--version", default="1.1.0")
    parser.add_argument(
        "--report",
        default="data/model-benchmarks/vision-precision-comparison.md",
    )
    parser.add_argument(
        "--details",
        default="data/model-benchmarks/vision-precision-comparison.json",
    )
    parser.add_argument(
        "--save-annotated",
        action="store_true",
        help="保存 FAST/PRECISION 检测结果图片副本，优先使用 POLYGON 不规则高亮",
    )
    parser.add_argument(
        "--annotated-dir",
        default="data/model-benchmarks/annotated",
        help="检测结果图片副本输出目录",
    )
    args = parser.parse_args()

    image_dir = _resolve(args.image_dir)
    model_root = _resolve(args.model_root)
    report_path = _resolve(args.report)
    details_path = _resolve(args.details)
    annotated_dir = _resolve(args.annotated_dir)
    files = _collect_images(image_dir)
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

    if not torch.cuda.is_available():
        print("CUDA 不可用，无法运行 PRECISION 对比。", file=sys.stderr)
        return 2

    from app.adapters.grounded_sam2 import GroundedSam2TinyAdapter
    from app.config import Settings
    from app.image import decode_image
    from app.model_manifest import load_model_manifest

    settings = Settings()
    manifest_path = (
        model_root / "AI-VISION-LOCAL-001" / args.version / "manifest.json"
    )
    if not manifest_path.is_file():
        print(f"manifest 不存在：{manifest_path}", file=sys.stderr)
        return 2
    manifest = load_model_manifest(manifest_path, model_root)
    adapter = GroundedSam2TinyAdapter(
        manifest, settings, require_approved=False
    )

    fast_rows: list[dict] = []
    precision_rows: list[dict] = []
    torch.cuda.reset_peak_memory_stats()
    for path in files:
        decoded = decode_image(path.read_bytes(), settings)
        relative_path = path.relative_to(image_dir)
        for profile, target in (
            ("FAST", fast_rows),
            ("PRECISION", precision_rows),
        ):
            torch.cuda.synchronize()
            started = time.monotonic()
            try:
                detections = _run_one(adapter, decoded, profile)
                torch.cuda.synchronize()
                target.append(
                    {
                        "file": relative_path.as_posix(),
                        "durationMs": (time.monotonic() - started) * 1000.0,
                        "detections": detections,
                    }
                )
                if args.save_annotated:
                    output_path = annotated_dir / profile.lower() / relative_path
                    try:
                        save_annotated_image(
                            path,
                            detections,
                            output_path,
                            profile,
                        )
                    except Exception as render_ex:
                        print(
                            f"标注图保存失败 {relative_path} [{profile}]：{render_ex}",
                            file=sys.stderr,
                        )
            except Exception as ex:
                target.append(
                    {
                        "file": relative_path.as_posix(),
                        "durationMs": (time.monotonic() - started) * 1000.0,
                        "detections": [],
                        "error": str(ex),
                    }
                )

    peak_vram = torch.cuda.max_memory_allocated() / 1024 / 1024
    fast_summary = summarize_results(fast_rows)
    precision_summary = summarize_results(precision_rows)
    report = build_report(
        model_version=args.version,
        image_count=len(files),
        fast_summary=fast_summary,
        precision_summary=precision_summary,
        ground_truth_available=False,
        peak_vram_mib=peak_vram,
        annotated_dir=_display_path(annotated_dir) if args.save_annotated else None,
    )
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(report, encoding="utf-8")

    details = {
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "modelId": "AI-VISION-LOCAL-001",
        "modelVersion": args.version,
        "imageCount": len(files),
        "fastSummary": fast_summary,
        "precisionSummary": precision_summary,
        "fast": serialize_rows(fast_rows),
        "precision": serialize_rows(precision_rows),
    }
    if args.save_annotated:
        details["annotatedDir"] = _display_path(annotated_dir)
    details_path.parent.mkdir(parents=True, exist_ok=True)
    details_path.write_text(
        json.dumps(details, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(report)
    print(f"报告已写入：{report_path}")
    print(f"逐图明细已写入：{details_path}")
    if args.save_annotated:
        print(f"标注图已写入：{annotated_dir}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
