"""ACCURACY 多模型实验模式的纯逻辑门控与候选融合。"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

ALLOWED_CLASS_CODES = {
    "CRACK",
    "SPALLING",
    "EXPOSED_REBAR",
    "CORROSION",
    "WATER_STAIN",
    "SURFACE_DAMAGE",
}
SPECIFIC_DAMAGE_CLASS_CODES = ALLOWED_CLASS_CODES - {"SURFACE_DAMAGE"}
LOCATOR_SOURCES = {"GROUNDING_DINO", "FLORENCE2"}
SURFACE_DAMAGE_ALLOW_THRESHOLD = 0.85
SURFACE_DAMAGE_MAX_AREA_RATIO = 0.25


@dataclass(frozen=True)
class SemanticClassDecision:
    code: str
    present: bool | None
    confidence: float

    def __post_init__(self) -> None:
        code = self.code.upper()
        if code not in ALLOWED_CLASS_CODES:
            raise ValueError(f"unsupported class code: {self.code}")
        if not 0.0 <= float(self.confidence) <= 1.0:
            raise ValueError("semantic confidence must be within [0, 1]")
        object.__setattr__(self, "code", code)


@dataclass(frozen=True)
class SemanticGateResult:
    classes: dict[str, SemanticClassDecision]

    def __post_init__(self) -> None:
        normalized: dict[str, SemanticClassDecision] = {}
        for key, decision in self.classes.items():
            code = str(key).upper()
            if code != decision.code:
                raise ValueError("semantic gate key and decision code mismatch")
            normalized[code] = decision
        object.__setattr__(self, "classes", normalized)


@dataclass(frozen=True)
class LocatorCandidate:
    box_xyxy: list[float]
    score: float
    class_code: str
    source: str

    def __post_init__(self) -> None:
        if len(self.box_xyxy) != 4:
            raise ValueError("box_xyxy must have four coordinates")
        x1, y1, x2, y2 = [float(v) for v in self.box_xyxy]
        if x2 <= x1 or y2 <= y1:
            raise ValueError("candidate box must have positive area")
        code = self.class_code.upper()
        source = self.source.upper()
        if code not in ALLOWED_CLASS_CODES:
            raise ValueError(f"unsupported class code: {self.class_code}")
        if source not in LOCATOR_SOURCES:
            raise ValueError(f"unsupported locator source: {self.source}")
        if not 0.0 <= float(self.score) <= 1.0:
            raise ValueError("locator score must be within [0, 1]")
        object.__setattr__(self, "box_xyxy", [x1, y1, x2, y2])
        object.__setattr__(self, "class_code", code)
        object.__setattr__(self, "source", source)


@dataclass(frozen=True)
class AccuracyCandidate:
    box_xyxy: list[float]
    score: float
    class_code: str
    sources: tuple[str, ...]
    semantic_confidence: float
    trust_level: str
    trust_reasons: tuple[str, ...]


def allowed_classes(
    gate: SemanticGateResult,
    semantic_allow_threshold: float = 0.55,
    surface_damage_allow_threshold: float = SURFACE_DAMAGE_ALLOW_THRESHOLD,
) -> set[str]:
    """返回允许进入定位器的类别，SURFACE_DAMAGE 仅作为严格兜底类别。"""

    specific = {
        code
        for code, item in gate.classes.items()
        if (
            code in SPECIFIC_DAMAGE_CLASS_CODES
            and item.present is True
            and item.confidence >= semantic_allow_threshold
        )
    }
    if specific:
        return specific

    surface = gate.classes.get("SURFACE_DAMAGE")
    if (
        surface is not None
        and surface.present is True
        and surface.confidence >= surface_damage_allow_threshold
    ):
        return {"SURFACE_DAMAGE"}
    return set()


def _intersection_metrics(a: list[float], b: list[float]) -> tuple[float, float]:
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
    union = area_a + area_b - inter
    iou = 0.0 if union <= 0 else inter / union
    smaller = min(area_a, area_b)
    overlap_small = 0.0 if smaller <= 0 else inter / smaller
    return iou, overlap_small


def _spatial_match(a: LocatorCandidate, b: LocatorCandidate) -> bool:
    iou, overlap_small = _intersection_metrics(a.box_xyxy, b.box_xyxy)
    return iou >= 0.35 or overlap_small >= 0.60


def _within_image(box: list[float], width: int, height: int) -> list[float]:
    if width <= 0 or height <= 0:
        raise ValueError("image dimensions must be positive")
    x1, y1, x2, y2 = box
    return [
        max(0.0, min(float(width), x1)),
        max(0.0, min(float(height), y1)),
        max(0.0, min(float(width), x2)),
        max(0.0, min(float(height), y2)),
    ]


def _box_area_ratio(box: list[float], width: int, height: int) -> float:
    if width <= 0 or height <= 0:
        raise ValueError("image dimensions must be positive")
    x1, y1, x2, y2 = box
    area = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    return area / float(width * height)


def _candidate_priority(item: AccuracyCandidate) -> tuple[int, int, float]:
    trust_rank = {"HIGH": 2, "MEDIUM": 1}.get(str(item.trust_level).upper(), 0)
    return trust_rank, len(set(item.sources)), float(item.score)


def deduplicate_accuracy_candidates(
    candidates: Iterable[AccuracyCandidate],
    iou_threshold: float = 0.55,
    containment_threshold: float = 0.80,
) -> list[AccuracyCandidate]:
    """保守实例级去重：只合并同类别且高度重叠/高度包含的正式候选。"""

    ordered = sorted(candidates, key=_candidate_priority, reverse=True)
    kept: list[AccuracyCandidate] = []
    for candidate in ordered:
        duplicate = False
        for existing in kept:
            if existing.class_code != candidate.class_code:
                continue
            iou, overlap_small = _intersection_metrics(
                existing.box_xyxy,
                candidate.box_xyxy,
            )
            if iou >= iou_threshold or overlap_small >= containment_threshold:
                duplicate = True
                break
        if not duplicate:
            kept.append(candidate)
    return sorted(kept, key=lambda item: item.score, reverse=True)


def fuse_cross_model_candidates(
    gate: SemanticGateResult,
    candidates: Iterable[LocatorCandidate],
    image_width: int,
    image_height: int,
    semantic_allow_threshold: float = 0.55,
    semantic_confirm_threshold: float = 0.70,
    single_locator_max_area_ratio: float = 0.65,
    surface_damage_allow_threshold: float = SURFACE_DAMAGE_ALLOW_THRESHOLD,
    surface_damage_max_area_ratio: float = SURFACE_DAMAGE_MAX_AREA_RATIO,
) -> list[AccuracyCandidate]:
    """融合 Qwen 语义门控与双定位器；泛化表面损伤只作为严格兜底结果。"""

    permitted = allowed_classes(
        gate,
        semantic_allow_threshold,
        surface_damage_allow_threshold,
    )
    ordered = sorted(
        (item for item in candidates if item.class_code in permitted),
        key=lambda item: item.score,
        reverse=True,
    )
    used: set[int] = set()
    output: list[AccuracyCandidate] = []

    for index, primary in enumerate(ordered):
        if index in used:
            continue
        decision = gate.classes.get(primary.class_code)
        if decision is None or decision.present is not True:
            continue
        if decision.confidence < semantic_allow_threshold:
            continue

        group = [primary]
        used.add(index)
        for other_index in range(index + 1, len(ordered)):
            if other_index in used:
                continue
            other = ordered[other_index]
            if other.class_code != primary.class_code:
                continue
            if other.source == primary.source:
                continue
            if _spatial_match(primary, other):
                group.append(other)
                used.add(other_index)
                break

        sources = tuple(sorted({item.source for item in group}))
        cross_model = len(sources) >= 2
        is_surface_damage = primary.class_code == "SURFACE_DAMAGE"

        # “其他表面损伤”是兜底语义，必须由两个独立定位器共同确认。
        if is_surface_damage and not cross_model:
            continue

        semantic_strong = decision.confidence >= semantic_confirm_threshold

        reasons = ["SEMANTIC_GATE_CONFIRMED"]
        if cross_model:
            reasons.append("CROSS_MODEL_CONFIRMED")

        if cross_model and semantic_strong:
            trust = "HIGH"
        elif cross_model:
            # 中等语义置信度必须由两个独立定位器共同确认。
            trust = "MEDIUM"
        elif semantic_strong:
            # 具体病害的高语义置信度可允许单定位器进入人工复核，但不升为 HIGH。
            trust = "MEDIUM"
        else:
            continue

        grounding = [item for item in group if item.source == "GROUNDING_DINO"]
        best = (
            max(grounding, key=lambda item: item.score)
            if grounding
            else max(group, key=lambda item: item.score)
        )
        box = _within_image(best.box_xyxy, image_width, image_height)
        if box[2] <= box[0] or box[3] <= box[1]:
            continue

        area_ratio = _box_area_ratio(box, image_width, image_height)
        if is_surface_damage and area_ratio > surface_damage_max_area_ratio:
            continue
        if not cross_model and area_ratio >= single_locator_max_area_ratio:
            continue

        output.append(
            AccuracyCandidate(
                box_xyxy=box,
                score=float(best.score),
                class_code=best.class_code,
                sources=sources,
                semantic_confidence=float(decision.confidence),
                trust_level=trust,
                trust_reasons=tuple(reasons),
            )
        )

    return deduplicate_accuracy_candidates(output)
