"""独立 ACCURACY 多模型批处理实验编排。"""
from __future__ import annotations

import gc
import time
from typing import Callable

from PIL import Image

from .accuracy import LocatorCandidate, allowed_classes, fuse_cross_model_candidates


ACCURACY_LARGE_CRACK_BOX_AREA_RATIO = 0.65
ACCURACY_LARGE_CRACK_MASK_AREA_RATIO = 0.18
ACCURACY_LARGE_CRACK_MASK_FILL_RATIO = 0.25


def _large_crack_mask_diagnostics(candidate, mask, width: int, height: int) -> dict:
    """用 SAM 掩膜形态复核 ACCURACY 中的大范围裂缝候选。

    仅当候选框本身覆盖大范围区域，且 SAM 掩膜同时具有较高整图面积占比和框内填充率时，
    才视为面状伪裂缝并拒绝。大框但低填充的细长裂缝继续保留，以保护召回。
    """
    if width <= 0 or height <= 0:
        raise ValueError("image dimensions must be positive")

    x1, y1, x2, y2 = [float(v) for v in candidate.box_xyxy]
    bbox_area = max(1.0, (x2 - x1) * (y2 - y1))
    image_area = float(max(1, width * height))
    bbox_ratio = min(1.0, bbox_area / image_area)

    diagnostics = {
        "bboxAreaRatio": round(bbox_ratio, 4),
        "maskAreaRatio": None,
        "maskFillRatio": None,
        "reject": False,
        "reasons": [],
    }
    if mask is None or str(candidate.class_code).upper() != "CRACK":
        return diagnostics

    try:
        raw_area = mask.sum()
        if hasattr(raw_area, "item"):
            raw_area = raw_area.item()
        mask_area = max(0.0, float(raw_area))
    except Exception:
        return diagnostics

    mask_area_ratio = min(1.0, mask_area / image_area)
    mask_fill_ratio = min(1.0, mask_area / bbox_area)
    diagnostics["maskAreaRatio"] = round(mask_area_ratio, 4)
    diagnostics["maskFillRatio"] = round(mask_fill_ratio, 4)

    if (
        bbox_ratio >= ACCURACY_LARGE_CRACK_BOX_AREA_RATIO
        and mask_area_ratio >= ACCURACY_LARGE_CRACK_MASK_AREA_RATIO
        and mask_fill_ratio >= ACCURACY_LARGE_CRACK_MASK_FILL_RATIO
    ):
        diagnostics["reject"] = True
        diagnostics["reasons"].append("LARGE_SURFACE_LIKE_CRACK_MASK")
    return diagnostics


class _ExistingGroundingLocator:
    def __init__(self, adapter) -> None:
        self.adapter = adapter

    def locate(self, image: Image.Image, class_codes: set[str]) -> list[LocatorCandidate]:
        if not class_codes:
            return []
        from .precision import fuse_candidates, generate_tiles
        from .precision_inference import PrecisionInferenceEngine

        engine = PrecisionInferenceEngine(self.adapter)
        out: list[LocatorCandidate] = []
        tiles = generate_tiles(
            image.width,
            image.height,
            rows=2,
            cols=2,
            overlap=0.18,
        )
        for code in sorted(class_codes):
            raw = engine._detect_candidates(
                image,
                "FULL_IMAGE",
                f"accuracy-full-{code.lower()}",
                class_code_filter=code,
                threshold=0.22,
            )
            for tile in tiles:
                crop = image.crop((tile.x0, tile.y0, tile.x1, tile.y1))
                raw.extend(
                    engine._detect_candidates(
                        crop,
                        "TILE",
                        f"accuracy-tile-{tile.index}-{code.lower()}",
                        region=tile,
                        class_code_filter=code,
                        threshold=0.22,
                    )
                )
            for item in fuse_candidates(raw):
                out.append(
                    LocatorCandidate(
                        list(item.box_xyxy),
                        float(item.max_confidence),
                        item.class_code,
                        "GROUNDING_DINO",
                    )
                )
        return out


def _release_detector(adapter) -> None:
    if hasattr(adapter, "release_detector"):
        adapter.release_detector()
        return
    for name in ("_dino", "_dino_processor"):
        if hasattr(adapter, name):
            setattr(adapter, name, None)
    gc.collect()
    torch = getattr(adapter, "_torch", None)
    if torch is not None and torch.cuda.is_available():
        torch.cuda.empty_cache()


def _release_grounded(adapter) -> None:
    if hasattr(adapter, "close"):
        adapter.close()
        return
    for name in ("_dino", "_dino_processor", "_sam", "_sam_processor"):
        if hasattr(adapter, name):
            setattr(adapter, name, None)
    gc.collect()
    torch = getattr(adapter, "_torch", None)
    if torch is not None and torch.cuda.is_available():
        torch.cuda.empty_cache()


def _class_name_map(adapter) -> dict[str, str]:
    manifest = getattr(adapter, "_manifest", None)
    classes = getattr(manifest, "classes", []) if manifest is not None else []
    return {str(item.code): str(item.name) for item in classes}


def _detection_payload(base, candidate, mask_diagnostics: dict | None = None) -> dict:
    if isinstance(base, dict):
        payload = dict(base)
    elif hasattr(base, "model_dump"):
        payload = base.model_dump(mode="json")
    else:
        payload = dict(vars(base))
    payload["trustLevel"] = candidate.trust_level
    payload["trustReasons"] = list(candidate.trust_reasons)
    diagnostics = dict(payload.get("diagnostics") or {})
    diagnostics["sources"] = list(candidate.sources)
    diagnostics["semanticConfidence"] = round(candidate.semantic_confidence, 4)
    diagnostics["accuracyExperimental"] = True
    if mask_diagnostics:
        diagnostics["bboxAreaRatio"] = mask_diagnostics.get("bboxAreaRatio")
        diagnostics["maskAreaRatio"] = mask_diagnostics.get("maskAreaRatio")
        diagnostics["maskFillRatio"] = mask_diagnostics.get("maskFillRatio")
        if mask_diagnostics.get("reasons"):
            diagnostics["maskGeometryReasons"] = list(mask_diagnostics["reasons"])
    payload["diagnostics"] = diagnostics
    return payload


class AccuracyBatchRunner:
    """按 Qwen -> Grounding -> Florence -> SAM 的阶段顺序处理整批图片。"""

    def __init__(
        self,
        qwen_factory: Callable[[], object],
        grounded_factory: Callable[[], object],
        florence_factory: Callable[[], object],
        grounded_locator_factory: Callable[[object], object] | None = None,
    ) -> None:
        self.qwen_factory = qwen_factory
        self.grounded_factory = grounded_factory
        self.florence_factory = florence_factory
        self.grounded_locator_factory = grounded_locator_factory or _ExistingGroundingLocator
        self.last_image_durations_ms: list[float] = []
        self.last_stage_durations_ms: dict[str, list[float]] = {}
        self.last_batch_wall_ms: float = 0.0
        self.last_semantic_gates: list[object] = []

    def run_batch(self, images: list[Image.Image]) -> list[list[dict]]:
        if not images:
            self.last_image_durations_ms = []
            self.last_stage_durations_ms = {}
            self.last_batch_wall_ms = 0.0
            self.last_semantic_gates = []
            return []

        batch_started = time.monotonic()
        stage = {
            name: [0.0 for _ in images]
            for name in ("qwen", "grounding", "florence", "sam")
        }
        qwen = self.qwen_factory()
        gates = []
        try:
            for index, image in enumerate(images):
                started = time.monotonic()
                gates.append(qwen.classify(image))
                stage["qwen"][index] = (time.monotonic() - started) * 1000.0
        finally:
            qwen.close()
        self.last_semantic_gates = list(gates)

        allowed_per_image = [allowed_classes(gate) for gate in gates]
        grounded = self.grounded_factory()
        try:
            ground_locator = self.grounded_locator_factory(grounded)
            grounded_rows: list[list[LocatorCandidate]] = []
            for index, (image, allowed) in enumerate(
                zip(images, allowed_per_image, strict=True)
            ):
                started = time.monotonic()
                grounded_rows.append(ground_locator.locate(image, allowed))
                stage["grounding"][index] = (time.monotonic() - started) * 1000.0

            _release_detector(grounded)

            florence = self.florence_factory()
            florence_rows: list[list[LocatorCandidate]] = []
            try:
                for index, (image, allowed) in enumerate(
                    zip(images, allowed_per_image, strict=True)
                ):
                    started = time.monotonic()
                    florence_rows.append(florence.locate(image, allowed))
                    stage["florence"][index] = (time.monotonic() - started) * 1000.0
            finally:
                florence.close()

            names = _class_name_map(grounded)
            all_results: list[list[dict]] = []
            for index, (image, gate, g_rows, f_rows) in enumerate(
                zip(images, gates, grounded_rows, florence_rows, strict=True)
            ):
                formal = fuse_cross_model_candidates(
                    gate,
                    g_rows + f_rows,
                    image.width,
                    image.height,
                )
                if not formal:
                    all_results.append([])
                    continue

                boxes = [item.box_xyxy for item in formal]
                started = time.monotonic()
                masks = grounded._sam2_masks(image, boxes)

                kept_formal = []
                kept_masks = []
                kept_mask_diagnostics = []
                for candidate_index, candidate in enumerate(formal):
                    mask = masks[candidate_index] if candidate_index < len(masks) else None
                    geometry = _large_crack_mask_diagnostics(
                        candidate,
                        mask,
                        image.width,
                        image.height,
                    )
                    if geometry.get("reject"):
                        continue
                    kept_formal.append(candidate)
                    kept_masks.append(mask)
                    kept_mask_diagnostics.append(geometry)

                formal = kept_formal
                masks = kept_masks
                if not formal:
                    all_results.append([])
                    stage["sam"][index] = (time.monotonic() - started) * 1000.0
                    continue

                boxes = [item.box_xyxy for item in formal]
                scores = [item.score for item in formal]
                codes = [item.class_code for item in formal]
                class_names = [names.get(code, code) for code in codes]
                base_items = grounded._build_detections(
                    boxes,
                    scores,
                    codes,
                    class_names,
                    image.width,
                    image.height,
                    masks,
                )
                row = [
                    _detection_payload(base, candidate, geometry)
                    for base, candidate, geometry in zip(
                        base_items,
                        formal,
                        kept_mask_diagnostics,
                        strict=False,
                    )
                ]
                all_results.append(row)
                stage["sam"][index] = (time.monotonic() - started) * 1000.0
            self.last_stage_durations_ms = stage
            self.last_image_durations_ms = [
                sum(stage[name][i] for name in stage)
                for i in range(len(images))
            ]
            self.last_batch_wall_ms = (time.monotonic() - batch_started) * 1000.0
            return all_results
        finally:
            _release_grounded(grounded)
