"""零样本视觉模型真实图片质量评估（与 Runtime 基准分离）。

Runtime 基准（benchmark_vision.py）确认“模型身份 + 运行稳定”；本工具做独立的
业务效果验证：用真实图片批量推理，并按 data/validation/ai-vision/manifest.json
中的期望标签统计命中/漏检/误检。

- 期望标签以 manifest 为准，文件夹名不作为绝对真值；
- 样本太少时不计算误导性 Accuracy，只给样本命中情况与人工评价；
- 无图片/无人工标注 → QUALITY_NOT_EVALUATED，不伪造结果。

用法：

    python -m tools.evaluate_vision_quality \
      --image-dir ../data/validation/ai-vision \
      --model-root ../data/model-cache \
      --report ../data/model-benchmarks/ai-vision-quality-report.md
"""

from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parents[2]

IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}
VALIDATION_MANIFEST = "manifest.json"


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _collect_images(image_dir: Path) -> list[Path]:
    if not image_dir.is_dir():
        return []
    return sorted(
        (path for path in image_dir.rglob("*") if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS),
        key=lambda p: p.relative_to(image_dir).as_posix(),
    )


def _expected_map(image_dir: Path) -> dict[str, list[str]]:
    manifest_path = image_dir / VALIDATION_MANIFEST
    if not manifest_path.is_file():
        return {}
    try:
        payload = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    result: dict[str, list[str]] = {}
    for entry in payload.get("images", []):
        if isinstance(entry, dict) and isinstance(entry.get("file"), str):
            result[entry["file"]] = list(entry.get("expected", []) or [])
    return result


def _compare_labels(expected: list[str] | set[str], detected: set[str]) -> tuple[set[str], set[str], set[str]]:
    """比较人工期望标签与模型输出，统一转 set，避免 list/set 混用。"""
    expected_set = set(expected)
    return (
        expected_set & detected,
        expected_set - detected,
        detected - expected_set,
    )


def main() -> int:
    parser = argparse.ArgumentParser(description="零样本视觉模型真实图片质量评估")
    parser.add_argument("--image-dir", required=True, help="真实图片目录（含子目录），必须含 manifest.json")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--report", default="data/model-benchmarks/ai-vision-quality-report.md")
    args = parser.parse_args()

    image_dir = _resolve(args.image_dir)
    report_path = _resolve(args.report)
    model_root = _resolve(args.model_root)

    os.environ.setdefault("URBAN_SAFE_AI_MODEL_ROOT", str(model_root))
    os.environ.setdefault("URBAN_SAFE_AI_MODEL_CATALOG_PATH", "runtime-catalog.json")
    os.environ.setdefault("URBAN_SAFE_AI_VISUAL_DEVICE", "cuda")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_DTYPE", "float16")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_OFFLINE", "true")
    os.environ.setdefault("URBAN_SAFE_AI_VISION_SHA_MODE", "STRICT")
    os.environ.setdefault("HF_HUB_OFFLINE", "1")

    files = _collect_images(image_dir)
    expected = _expected_map(image_dir)
    if not files:
        print("image-dir 无匹配图片（jpg/png/webp）。", file=sys.stderr)
        return 2
    if not expected:
        print("manifest.json 无 images 期望标签，拒绝自动评估（文件夹名不作真值）。", file=sys.stderr)
        return 2

    from app.adapters.grounded_sam2 import GroundedSam2TinyAdapter
    from app.config import Settings
    from app.image import decode_image
    from app.model_manifest import load_model_manifest

    settings = Settings()
    manifest = load_model_manifest(model_root / "AI-VISION-LOCAL-001/1.0.0/manifest.json", model_root)
    adapter = GroundedSam2TinyAdapter(manifest, settings, require_approved=False)

    per_class: dict[str, dict[str, int]] = {}
    rows: list[str] = []
    normal_fp = 0
    normal_count = 0

    def bump(cls: str, key: str) -> None:
        bucket = per_class.setdefault(cls, {"expected": 0, "hit": 0, "miss": 0, "fp": 0})
        bucket[key] = bucket.get(key, 0) + 1

    for path in files:
        relative = path.relative_to(image_dir).as_posix()
        expect = expected.get(relative)
        if expect is None:
            rows.append(f"- `{relative}`：无期望标签，跳过")
            continue
        try:
            _, detections = adapter.predict(decode_image(path.read_bytes(), settings))
        except Exception as ex:
            rows.append(f"- `{relative}`：推理失败 {ex}")
            continue
        detected = {item.classCode for item in detections}
        if not expect:
            normal_count += 1
            if detected:
                normal_fp += 1
                rows.append(
                    f"- `{relative}`：期望无检测，误检 {sorted(detected)}"
                )
            else:
                rows.append(f"- `{relative}`：期望无检测，正确无检测")
            continue
        expected_set = set(expect)
        hit, miss, fp = _compare_labels(expected_set, detected)
        for cls in expected_set:
            bump(cls, "expected")
            if cls in hit:
                bump(cls, "hit")
            else:
                bump(cls, "miss")
        for cls in fp:
            bump(cls, "fp")
        if fp or miss:
            detail = []
            if miss:
                detail.append(f"漏检 {sorted(miss)}")
            if fp:
                detail.append(f"误检 {sorted(fp)}")
            rows.append(f"- `{relative}`：期望 {sorted(expected_set)}，命中 {sorted(hit)}，" + "，".join(detail))
        else:
            rows.append(f"- `{relative}`：期望 {sorted(expected_set)}，命中 {sorted(hit)}")

    total = sum(1 for r in rows if not r.endswith("跳过"))
    lines = [
        "# AI-VISION-LOCAL-001 真实图片质量评估报告",
        "",
        f"> 生成时间：{datetime.now(timezone.utc).isoformat()}",
        f"> 命令：python -m tools.evaluate_vision_quality --image-dir {image_dir}",
        "",
        "## 样本",
        "",
        f"- 样本数：{total}",
        f"- 正常/非建筑图片：{normal_count}（误检 {normal_fp}）",
        "",
        "## 按类别",
        "",
        "| 类别 | 期望样本 | 命中 | 漏检 | 误检 |",
        "|---|---|---|---|---|",
    ]
    for cls in sorted(per_class):
        b = per_class[cls]
        lines.append(
            f"| {cls} | {b['expected']} | {b['hit']} | {b['miss']} | {b['fp']} |"
        )
    lines += [
        "",
        "## 逐样本",
        "",
    ] + rows + [
        "",
        "## 结论",
        "",
        f"- 状态：{'QUALITY_EVALUATED_REPORT_ONLY' if total else 'QUALITY_NOT_EVALUATED'}",
        "- 说明：比赛零样本评估，不是模型科研精度报告；样本过少时命中/漏检/误检",
        "  只作人工参考，不计算误导性 Accuracy。",
        "- 零样本结果一律为“疑似”病害，仅辅助筛查，最终以专业人工复核为准。",
        "",
    ]
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text("\n".join(lines), encoding="utf-8")

    print("\n".join(lines))
    print(f"报告已写入：{report_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())