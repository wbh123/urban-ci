"""ONNX 独立评估与准入提升。"""
from __future__ import annotations

import argparse
import csv
import json
import shutil
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable

import numpy as np
from PIL import Image

from app.adapters.onnx_crack_segmentation import (
    PIL_RESAMPLING,
    _foreground_probabilities,
    _output_probabilities,
)
from .pipeline_common import MANIFEST_FILENAME, _check_minimum, _float_range, _read_json, _sha256
from .pipeline_model import _create_onnx_session, _validate_package_with_runtime, _verify_onnx_contract


def _command_evaluate(args: argparse.Namespace) -> None:
    package_dir = args.package.expanduser().resolve()
    payload = _read_json(package_dir / MANIFEST_FILENAME)
    model_input = payload["input"]
    input_size = int(model_input["width"])
    if input_size != int(model_input["height"]):
        raise RuntimeError("当前评估工具仅支持正方形固定输入")
    onnx_path = package_dir / str(payload["weightFile"])
    _verify_onnx_contract(onnx_path, input_size=input_size)
    pairs = _read_split_rows(args.split)
    thresholds = [args.threshold]
    if args.search_threshold:
        thresholds = _float_range(args.threshold_min, args.threshold_max, args.threshold_step)
    predictions: list[tuple[np.ndarray, np.ndarray]] = []
    session = _create_onnx_session(onnx_path)
    output_activation = str(payload.get("outputActivation") or "LOGITS")
    foreground_polarity = str(payload.get("foregroundPolarity") or "HIGH_PROBABILITY")
    interpolation = str(model_input.get("interpolation") or "BILINEAR").upper()
    mean = np.asarray(model_input["mean"], dtype=np.float32)
    std = np.asarray(model_input["std"], dtype=np.float32)
    for image_path, mask_path, _group in pairs:
        batch = _preprocess_image(
            image_path,
            input_size,
            mean=mean,
            std=std,
            interpolation=interpolation,
        )
        output = session.run(["mask_logits"], {"images": batch})[0]
        if output.shape != (1, 1, input_size, input_size):
            raise RuntimeError(f"ONNX 输出形状不合法：{output.shape}")
        model_probabilities = _output_probabilities(output[0, 0], output_activation)
        foreground_scores = _foreground_probabilities(
            model_probabilities, foreground_polarity
        )
        target = _load_mask(mask_path, input_size=input_size, polarity=args.mask_polarity)
        predictions.append((foreground_scores, target))
    candidates = [
        {"threshold": threshold, "metrics": _calculate_metrics(predictions, threshold)}
        for threshold in thresholds
    ]
    selected = max(
        candidates,
        key=lambda item: (
            item["metrics"]["pixelF1"] + item["metrics"]["iou"],
            item["metrics"]["imageRecall"],
            -item["metrics"]["falsePositiveImageRate"],
        ),
    )
    result = {
        "schemaVersion": 2,
        "modelId": payload["modelId"],
        "version": payload["version"],
        "weightSha256": _sha256(onnx_path),
        "dataset": str(args.split.expanduser().resolve()),
        "sampleCount": len(predictions),
        "selectedThreshold": selected["threshold"],
        "metrics": selected["metrics"],
        "thresholdCandidates": candidates if args.search_threshold else [],
        "inferenceContract": {
            "outputActivation": output_activation.upper(),
            "foregroundPolarity": foreground_polarity.upper(),
            "interpolation": interpolation,
        },
        "evaluatedAt": datetime.now(timezone.utc).isoformat(),
    }
    output_path = args.output.expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))


def _command_promote(args: argparse.Namespace) -> None:
    package_dir = args.package.expanduser().resolve()
    manifest_path = package_dir / MANIFEST_FILENAME
    original_manifest = manifest_path.read_text(encoding="utf-8")
    payload = json.loads(original_manifest)
    if str(payload.get("status")).upper() != "CANDIDATE":
        raise RuntimeError("只有 CANDIDATE 模型包可以执行准入提升")
    evaluation_path = args.evaluation.expanduser().resolve()
    evaluation = _read_json(evaluation_path)
    if evaluation.get("modelId") != payload.get("modelId"):
        raise RuntimeError("评估记录与模型编号不一致")
    weight_path = package_dir / str(payload["weightFile"])
    actual_sha = _sha256(weight_path)
    if evaluation.get("weightSha256") != actual_sha:
        raise RuntimeError("评估记录对应的权重 SHA-256 与当前模型不一致")
    expected_contract = {
        "outputActivation": str(payload.get("outputActivation") or "LOGITS").upper(),
        "foregroundPolarity": str(
            payload.get("foregroundPolarity") or "HIGH_PROBABILITY"
        ).upper(),
        "interpolation": str(
            payload.get("input", {}).get("interpolation") or "BILINEAR"
        ).upper(),
    }
    if evaluation.get("inferenceContract") != expected_contract:
        raise RuntimeError("评估记录使用的推理契约与当前模型清单不一致")
    metrics = evaluation.get("metrics")
    if not isinstance(metrics, dict):
        raise RuntimeError("评估记录缺少 metrics")
    failures: list[str] = []
    _check_minimum(failures, "pixelF1", metrics, args.minimum_pixel_f1)
    _check_minimum(failures, "iou", metrics, args.minimum_iou)
    _check_minimum(failures, "imageRecall", metrics, args.minimum_image_recall)
    false_positive_rate = float(metrics.get("falsePositiveImageRate", 1.0))
    if false_positive_rate > args.maximum_false_positive_image_rate:
        failures.append(
            f"falsePositiveImageRate={false_positive_rate:.4f} > "
            f"{args.maximum_false_positive_image_rate:.4f}"
        )
    if failures:
        raise RuntimeError("模型未达到准入门槛：\n- " + "\n- ".join(failures))
    payload["weightSha256"] = actual_sha
    payload["status"] = "APPROVED"
    payload["thresholds"]["mask"] = float(evaluation["selectedThreshold"])
    payload["metrics"] = {
        "dataset": str(evaluation["dataset"]),
        "pixelF1": float(metrics["pixelF1"]),
        "iou": float(metrics["iou"]),
        "imageRecall": float(metrics["imageRecall"]),
    }
    payload["approvedBy"] = args.approved_by
    payload["approvedAt"] = args.approved_at or datetime.now(timezone.utc).isoformat()
    evaluation_target = package_dir / "evaluation.json"
    original_evaluation = evaluation_target.read_bytes() if evaluation_target.is_file() else None
    try:
        manifest_path.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
        shutil.copy2(evaluation_path, evaluation_target)
        _validate_package_with_runtime(package_dir)
    except Exception:
        manifest_path.write_text(original_manifest, encoding="utf-8")
        if original_evaluation is None:
            evaluation_target.unlink(missing_ok=True)
        else:
            evaluation_target.write_bytes(original_evaluation)
        raise
    print(f"模型已通过本地准入并标记为 APPROVED：{manifest_path}")


def _read_split_rows(path: Path) -> list[tuple[Path, Path, str]]:
    path = path.expanduser().resolve()
    rows = []
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file, delimiter="\t")
        if reader.fieldnames is None or not {"image", "mask", "group"}.issubset(
            reader.fieldnames
        ):
            raise ValueError("评估清单必须包含 image、mask、group")
        for row in reader:
            image = Path(row["image"]).expanduser().resolve()
            mask = Path(row["mask"]).expanduser().resolve()
            if not image.is_file() or not mask.is_file():
                raise FileNotFoundError(f"评估文件不存在：{image} / {mask}")
            rows.append((image, mask, row["group"]))
    if not rows:
        raise ValueError("评估清单为空")
    return rows


def _preprocess_image(
    path: Path,
    input_size: int,
    *,
    mean: np.ndarray,
    std: np.ndarray,
    interpolation: str,
) -> np.ndarray:
    try:
        resampling = PIL_RESAMPLING[interpolation]
    except KeyError as ex:
        raise RuntimeError(f"不支持的图片缩放插值：{interpolation}") from ex
    image = Image.open(path).convert("RGB").resize(
        (input_size, input_size), resampling
    )
    array = np.asarray(image, dtype=np.float32) / 255.0
    array = (array - mean) / std
    return np.transpose(array, (2, 0, 1))[None, ...].astype(np.float32)


def _load_mask(path: Path, *, input_size: int, polarity: str) -> np.ndarray:
    mask = Image.open(path).convert("L").resize(
        (input_size, input_size), Image.Resampling.NEAREST
    )
    binary = np.asarray(mask, dtype=np.uint8) >= 127
    if polarity == "black-crack" or (
        polarity == "auto" and float(binary.mean()) > 0.5
    ):
        binary = ~binary
    return binary


def _calculate_metrics(
    predictions: Iterable[tuple[np.ndarray, np.ndarray]], threshold: float
) -> dict[str, float]:
    intersection = prediction_pixels = target_pixels = union = 0
    positive_images = detected_positive_images = negative_images = false_positive_images = 0
    for foreground_scores, target in predictions:
        predicted = foreground_scores >= threshold
        intersection += int(np.logical_and(predicted, target).sum())
        prediction_pixels += int(predicted.sum())
        target_pixels += int(target.sum())
        union += int(np.logical_or(predicted, target).sum())
        if bool(target.any()):
            positive_images += 1
            detected_positive_images += int(bool(predicted.any()))
        else:
            negative_images += 1
            false_positive_images += int(bool(predicted.any()))
    denominator = prediction_pixels + target_pixels
    return {
        "pixelF1": 2.0 * intersection / denominator if denominator else 1.0,
        "iou": intersection / union if union else 1.0,
        "imageRecall": detected_positive_images / positive_images if positive_images else 1.0,
        "falsePositiveImageRate": false_positive_images / negative_images if negative_images else 0.0,
    }
