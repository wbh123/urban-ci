"""RTX 3060 6GB 零样本视觉模型基准与性能报告。

默认仍测试已验收的 1.0.0 Tiny；精度优先 Base/Base+ 候选使用：

    python -m tools.benchmark_vision --version 1.1.0 --iterations 20

benchmark 直接读取指定版本 manifest，不修改 active runtime catalog，因此可在批准前安全测试 CANDIDATE。
"""

from __future__ import annotations

import argparse
import io
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]
IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def _threshold_overrides(box_threshold, text_threshold, env: dict) -> dict:
    """阈值覆盖（CLI > 环境变量 > Settings 默认），只返回需写入环境的覆盖。"""

    overrides: dict = {}
    if box_threshold is not None:
        overrides["URBAN_SAFE_AI_VISION_BOX_THRESHOLD"] = str(box_threshold)
    if text_threshold is not None:
        overrides["URBAN_SAFE_AI_VISION_TEXT_THRESHOLD"] = str(text_threshold)
    return overrides


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _make_crack_image(width: int = 1024, height: int = 768) -> bytes:
    from PIL import Image, ImageDraw

    image = Image.new("RGB", (width, height), (190, 186, 180))
    draw = ImageDraw.Draw(image)
    draw.line(
        [(60, int(height * 0.8)), (280, int(height * 0.72)), (500, int(height * 0.6)),
         (720, int(height * 0.46)), (width - 64, int(height * 0.32))],
        fill=(20, 18, 16), width=14,
    )
    draw.line([(280, int(height * 0.72)), (360, int(height * 0.9))], fill=(25, 22, 20), width=8)
    draw.line([(500, int(height * 0.6)), (620, int(height * 0.72))], fill=(25, 22, 20), width=6)
    draw.ellipse(
        [int(width * 0.74), int(height * 0.18), int(width * 0.84), int(height * 0.32)],
        fill=(160, 150, 140),
    )
    buffer = io.BytesIO()
    image.save(buffer, format="PNG")
    return buffer.getvalue()


def _collect_images(image_dir: Path) -> list[Path]:
    if not image_dir.is_dir():
        return []
    return sorted(
        (path for path in image_dir.iterdir() if path.suffix.lower() in IMAGE_EXTENSIONS),
        key=lambda p: p.name,
    )


def _detection_schema_pass(detections: list) -> bool:
    if not detections:
        return True
    for item in detections:
        box = item.boundingBox
        if not (0.0 <= box.x <= 1.0 and 0.0 <= box.y <= 1.0 and 0.0 < box.width <= 1.0 and 0.0 < box.height <= 1.0):
            return False
        if not item.classCode or not 0.0 <= item.confidence <= 1.0:
            return False
    return True


def _segmentation_schema_pass(detections: list) -> bool:
    for item in detections:
        if item.segmentation is None:
            continue
        if item.segmentation.type.value != "POLYGON":
            return False
        for point in item.segmentation.points:
            if len(point) != 2 or not (0.0 <= point[0] <= 1.0 and 0.0 <= point[1] <= 1.0):
                return False
    return True


def main() -> int:
    parser = argparse.ArgumentParser(description="零样本视觉模型 CUDA 基准")
    parser.add_argument("--iterations", type=int, default=20)
    parser.add_argument("--version", default="1.0.0", help="待测试模型内部版本；Base/Base+ 候选为 1.1.0")
    parser.add_argument("--image", default=None, help="测试图片路径；缺省生成合成裂缝图")
    parser.add_argument("--image-dir", default=None, help="真实图片目录批评估（可选）")
    parser.add_argument("--box-threshold", type=float, default=None,
                        help="覆盖 box threshold（CLI > 环境变量 > manifest 默认）；仅影响本次运行")
    parser.add_argument("--text-threshold", type=float, default=None,
                        help="覆盖 text threshold（CLI > 环境变量 > manifest 默认）；仅影响本次运行")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--report", default="data/model-benchmarks/rtx3060-6g-report.md")
    args = parser.parse_args()

    model_root = _resolve(args.model_root)
    report_path = _resolve(args.report)
    report_path.parent.mkdir(parents=True, exist_ok=True)

    os.environ.setdefault("URBAN_SAFE_AI_MODEL_ROOT", str(model_root))
    os.environ.setdefault("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    os.environ.setdefault("URBAN_SAFE_AI_VISUAL_DEVICE", "cuda")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_DTYPE", "float16")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_OFFLINE", "true")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_HF_HOME", str(model_root / "huggingface"))
    os.environ.setdefault("HF_HUB_OFFLINE", "1")
    os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
    # 阈值覆盖：CLI 参数 > 环境变量 > Settings 默认。仅覆盖本次进程，不修改 manifest/.env。
    os.environ.update(_threshold_overrides(args.box_threshold, args.text_threshold, os.environ))

    import torch
    if not torch.cuda.is_available():
        print("CUDA 不可用，无法运行演示机基准。", file=sys.stderr)
        return 2

    from app.adapters.grounded_sam2 import GroundedSam2TinyAdapter
    from app.config import Settings
    from app.image import decode_image
    from app.model_manifest import load_model_manifest

    settings = Settings()
    manifest_path = model_root / "AI-VISION-LOCAL-001" / args.version / "manifest.json"
    if not manifest_path.is_file():
        print(f"待测试 manifest 不存在：{manifest_path}", file=sys.stderr)
        return 2
    manifest = load_model_manifest(manifest_path, model_root)
    adapter = GroundedSam2TinyAdapter(manifest, settings, require_approved=False)

    image_bytes = Path(args.image).expanduser().read_bytes() if args.image else _make_crack_image()
    decoded = decode_image(image_bytes, settings)

    sam2_box_forward = False
    try:
        from PIL import Image as _PIL
        pil_image = _PIL.open(io.BytesIO(decoded.bytes_)).convert("RGB")
        masks = adapter._sam2_masks(pil_image, [[0.1, 0.1, 0.6, 0.6]])
        sam2_box_forward = masks is not None
    except Exception as ex:
        print(f"  SAM2 box-prompt 前向失败：{ex}", file=sys.stderr)

    torch.cuda.reset_peak_memory_stats()
    durations_ms: list[float] = []
    failures = 0
    last_detections: list = []
    dino_forwarded = False
    sam2_forwarded_in_predict = False
    for _ in range(args.iterations):
        started = time.monotonic()
        try:
            _, detections = adapter.predict(decoded)
            durations_ms.append((time.monotonic() - started) * 1000.0)
            last_detections = detections
            dino_forwarded = True
            if detections:
                sam2_forwarded_in_predict = True
        except Exception as ex:
            failures += 1
            durations_ms.append((time.monotonic() - started) * 1000.0)
            print(f"  推理失败：{ex}", file=sys.stderr)

    peak_bytes = torch.cuda.max_memory_allocated()
    props = torch.cuda.get_device_properties(0)
    total_bytes = props.total_memory
    gpu_name = torch.cuda.get_device_name(0)

    import transformers

    durations_ms.sort()
    count = len(durations_ms)
    p50 = durations_ms[count // 2] if count else 0.0
    p95 = durations_ms[min(count - 1, int(count * 0.95))] if count else 0.0
    average = sum(durations_ms) / count if count else 0.0

    class_counts: dict[str, int] = {}
    for item in last_detections:
        class_counts[item.classCode] = class_counts.get(item.classCode, 0) + 1
    sample = [
        {
            "classCode": item.classCode,
            "className": item.className,
            "confidence": round(item.confidence, 4),
            "boundingBox": {
                "x": round(item.boundingBox.x, 4),
                "y": round(item.boundingBox.y, 4),
                "width": round(item.boundingBox.width, 4),
                "height": round(item.boundingBox.height, 4),
            },
            "segmentation": (
                {"type": item.segmentation.type.value, "points": len(item.segmentation.points)}
                if item.segmentation is not None else None
            ),
        }
        for item in last_detections[:8]
    ]

    oom_fallbacks = int(getattr(adapter, "oom_fallback_count", 0))
    sam2_box_fallback = bool(getattr(adapter, "sam2_box_fallback", False))

    batch_rows: list[str] = []
    if args.image_dir:
        image_dir = _resolve(args.image_dir)
        files = _collect_images(image_dir)
        if not files:
            batch_rows.append("- image-dir 无匹配图片（jpg/png/webp）")
        for path in files:
            try:
                _, detections = adapter.predict(decode_image(path.read_bytes(), settings))
                codes: dict[str, int] = {}
                for item in detections:
                    codes[item.classCode] = codes.get(item.classCode, 0) + 1
                batch_rows.append(f"- `{path.name}`：detectionCount={len(detections)} classCounts={json.dumps(codes, ensure_ascii=False)}")
            except Exception as ex:
                batch_rows.append(f"- `{path.name}`：FAILED {ex}")

    detection_schema = _detection_schema_pass(last_detections)
    segmentation_schema = _segmentation_schema_pass(last_detections)
    api_pass = failures == 0
    dino_forward_pass = dino_forwarded and not failures
    sam2_forward_pass = (sam2_box_forward or sam2_forwarded_in_predict) and not failures
    quality_status = "QUALITY_EVALUATED_REPORT_ONLY" if args.image_dir and batch_rows else "QUALITY_NOT_EVALUATED"

    lines = [
        "# RTX 3060 6GB 本地视觉模型基准报告",
        "",
        f"> 生成时间：{datetime.now(timezone.utc).isoformat()}",
        f"> 命令：python -m tools.benchmark_vision --version {args.version} --iterations {args.iterations}",
        "",
        "## 环境",
        "",
        f"- torch：{torch.__version__}，CUDA 可用：`{torch.cuda.is_available()}`",
        f"- GPU 名称：{gpu_name}",
        f"- 总显存：{total_bytes / 1024 / 1024:.0f} MiB（{total_bytes} bytes）",
        f"- transformers：{transformers.__version__}",
        f"- 模型编号：AI-VISION-LOCAL-001 v{manifest.version}（adapter={manifest.adapter}）",
        f"- 检测器：{manifest.checkpoint.detector_repository}@{manifest.checkpoint.detector_revision[:12]}…",
        f"- 分割器：{manifest.checkpoint.segmenter_repository}@{manifest.checkpoint.segmenter_revision[:12]}…",
        f"- 权重摘要：{manifest.weight_sha256[:12]}…",
        f"- 精度：{settings.vision_dtype}；长边默认 {settings.vision_max_long_side}；batch=1；concurrency={settings.vision_max_concurrency}",
        "",
        "## Runtime",
        "",
        f"- CUDA_PASS：{'PASS' if torch.cuda.is_available() else 'FAIL'}",
        f"- DINO_FORWARD_PASS：{'PASS' if dino_forward_pass else 'FAIL'}",
        f"- SAM2_FORWARD_PASS：{'PASS' if sam2_forward_pass else 'FAIL'}",
        f"- API_PASS：{'PASS' if api_pass else 'FAIL'}",
        f"- SAM2_BOX_PROMPT：{'USED' if not sam2_box_fallback else 'FALLBACK_TO_POINT'}",
        "",
        "## Output Contract",
        "",
        f"- DETECTION_SCHEMA_PASS：{'PASS' if detection_schema else 'FAIL'}",
        f"- SEGMENTATION_SCHEMA_PASS：{'PASS' if segmentation_schema else 'FAIL'}",
        "",
        "## 性能",
        "",
        f"- 迭代次数：{args.iterations}",
        f"- 成功：{args.iterations - failures}，失败：{failures}",
        f"- OOM 降级次数：{oom_fallbacks}",
        f"- 耗时(ms)：p50=`{p50:.1f}`，p95=`{p95:.1f}`，min=`{durations_ms[0]:.1f}`，max=`{durations_ms[-1]:.1f}`，mean=`{average:.1f}`",
        f"- 峰值显存：{peak_bytes / 1024 / 1024:.0f} MiB",
        "",
        "## 检测样例（最后一次）",
        "",
        f"- detectionCount：{len(last_detections)}",
        f"- classCounts：`{json.dumps(class_counts, ensure_ascii=False)}`",
        f"- 样例：`{json.dumps(sample, ensure_ascii=False)}`",
        "",
        "## Model Quality（零样本效果，仅辅助筛查）",
        "",
        f"- 状态：{quality_status}",
        "- 说明：模型能前向不等于识别效果优秀；识别效果需用真实图片人工复核，不得据此给出确定性结论。",
        "",
        "## 批评估（--image-dir）",
        "",
    ] + batch_rows + [
        "",
        "> 说明：零样本视觉结果为“疑似”病害，仅用于辅助筛查，需人工复核；",
        "> 模型置信度不代表房屋危险概率。",
        "",
    ]
    report_path.write_text("\n".join(lines), encoding="utf-8")
    print("\n".join(lines))
    print(f"报告已写入：{report_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
