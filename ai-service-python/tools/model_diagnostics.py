"""在本地 CUDA 环境中导出 ONNX 原始输出、模型概率和裂缝前景分数。"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Iterable

import numpy as np
from PIL import Image

from app.adapters.onnx_crack_segmentation import (
    OnnxCrackSegmentationAdapter,
    summarize_output_tensor,
)
from app.config import Settings
from app.image import decode_image
from app.model_manifest import load_model_manifest


IMAGE_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp"}
DEFAULT_THRESHOLDS = (0.01, 0.02, 0.05, 0.1, 0.15, 0.25, 0.5, 0.75, 0.9)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="诊断 CUDA ONNX 模型的原始输出、前景极性和阈值行为"
    )
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--model-root", type=Path, required=True)
    parser.add_argument("--images", type=Path, nargs="+", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--cuda-device-id", type=int, default=0)
    parser.add_argument(
        "--thresholds",
        type=float,
        nargs="*",
        default=list(DEFAULT_THRESHOLDS),
    )
    return parser


def main() -> None:
    args = build_parser().parse_args()
    manifest = load_model_manifest(args.manifest, args.model_root)
    adapter = OnnxCrackSegmentationAdapter(
        manifest,
        cuda_device_id=args.cuda_device_id,
    )
    images = _discover_images(args.images)
    if not images:
        raise SystemExit("没有找到可诊断的 JPEG、PNG 或 WebP 图片")

    output_root = args.output.expanduser().resolve()
    output_root.mkdir(parents=True, exist_ok=True)
    samples: list[dict[str, object]] = []
    settings = Settings()

    for index, image_path in enumerate(images, start=1):
        image_bytes = image_path.read_bytes()
        decoded = decode_image(image_bytes, settings)
        raw_output, model_probabilities, foreground_scores = adapter.raw_output(decoded)
        sample_dir = output_root / f"{index:03d}-{_safe_name(image_path.stem)}"
        sample_dir.mkdir(parents=True, exist_ok=True)

        np.save(sample_dir / "raw-output.npy", raw_output)
        np.save(sample_dir / "model-probabilities.npy", model_probabilities)
        np.save(sample_dir / "foreground-scores.npy", foreground_scores)
        _save_grayscale(
            sample_dir / "raw-output.png", _normalize_for_preview(raw_output)
        )
        _save_grayscale(
            sample_dir / "model-probabilities.png", model_probabilities
        )
        _save_grayscale(sample_dir / "foreground-scores.png", foreground_scores)
        _save_grayscale(
            sample_dir / "manifest-mask.png",
            (foreground_scores >= manifest.thresholds.mask).astype(np.float32),
        )

        sample_summary = {
            "image": str(image_path),
            "width": decoded.width,
            "height": decoded.height,
            "outputActivation": manifest.output_activation,
            "foregroundPolarity": manifest.foreground_polarity,
            "interpolation": manifest.input.interpolation,
            "manifestMaskThreshold": manifest.thresholds.mask,
            "rawOutput": summarize_output_tensor(raw_output, args.thresholds),
            "modelProbabilities": summarize_output_tensor(
                model_probabilities, args.thresholds
            ),
            "foregroundScores": summarize_output_tensor(
                foreground_scores, args.thresholds
            ),
        }
        (sample_dir / "summary.json").write_text(
            json.dumps(sample_summary, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8",
        )
        samples.append(sample_summary)

    report = {
        "schemaVersion": 2,
        "modelId": manifest.model_id,
        "modelVersion": manifest.version,
        "weightSha256": manifest.weight_sha256,
        "outputActivation": manifest.output_activation,
        "foregroundPolarity": manifest.foreground_polarity,
        "interpolation": manifest.input.interpolation,
        "executionProvider": adapter.execution_provider(),
        "thresholds": list(args.thresholds),
        "sampleCount": len(samples),
        "samples": samples,
    }
    report_path = output_root / "diagnostics.json"
    report_path.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"诊断完成：{report_path}")


def _discover_images(paths: Iterable[Path]) -> list[Path]:
    found: list[Path] = []
    for path in paths:
        resolved = path.expanduser().resolve()
        if resolved.is_file() and resolved.suffix.lower() in IMAGE_SUFFIXES:
            found.append(resolved)
        elif resolved.is_dir():
            found.extend(
                item
                for item in sorted(resolved.rglob("*"))
                if item.is_file() and item.suffix.lower() in IMAGE_SUFFIXES
            )
        else:
            raise FileNotFoundError(f"图片路径不存在或格式不支持：{resolved}")
    return sorted(dict.fromkeys(found))


def _normalize_for_preview(values: np.ndarray) -> np.ndarray:
    array = np.asarray(values, dtype=np.float32)
    minimum = float(array.min())
    maximum = float(array.max())
    if maximum <= minimum:
        return np.zeros_like(array, dtype=np.float32)
    return (array - minimum) / (maximum - minimum)


def _save_grayscale(path: Path, values: np.ndarray) -> None:
    array = np.asarray(values, dtype=np.float32)
    array = np.clip(array, 0.0, 1.0)
    Image.fromarray(np.rint(array * 255.0).astype(np.uint8), mode="L").save(path)


def _safe_name(value: str) -> str:
    normalized = "".join(
        ch if ch.isalnum() or ch in {"-", "_"} else "-" for ch in value
    )
    return normalized.strip("-") or "image"


if __name__ == "__main__":
    main()
