"""精度优先视觉推理的纯逻辑组件。

本模块不依赖 CUDA/PyTorch，只负责 Tile、坐标映射、候选融合、异常大框复核策略
和可解释的可信度评分。可信度仅表示模型候选可信程度，不等于房屋安全风险等级。
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Iterable


@dataclass(frozen=True)
class PrecisionInferenceOptions:
    tile_rows: int = 2
    tile_cols: int = 2
    tile_overlap: float = 0.18
    candidate_box_threshold: float = 0.22
    candidate_text_threshold: float = 0.22
    large_box_area: float = 0.65
    near_full_box_area: float = 0.85
    local_verify_padding: float = 0.20
    local_verify_threshold: float = 0.30
    minimum_prompt_votes: int = 2
    prompt_verify_candidates: int = 3
    prompt_verify_max_prompts: int = 3
    prompt_verify_threshold: float = 0.28
    medium_confidence_floor: float = 0.28
    max_candidates_before_segmentation: int = 10
    request_timeout_seconds: int = 15


@dataclass(frozen=True)
class TileRegion:
    index: int
    row: int
    col: int
    x0: int
    y0: int
    x1: int
    y1: int

    @property
    def width(self) -> int:
        return self.x1 - self.x0

    @property
    def height(self) -> int:
        return self.y1 - self.y0


@dataclass(frozen=True)
class Candidate:
    box_xyxy: list[float]
    score: float
    class_code: str
    class_name: str
    prompt: str | None
    source: str
    source_id: str


@dataclass
class FusedCandidate:
    box_xyxy: list[float]
    max_confidence: float
    class_code: str
    class_name: str
    prompts: set[str] = field(default_factory=set)
    sources: set[str] = field(default_factory=set)
    source_ids: set[str] = field(default_factory=set)
    local_verified: bool = False

    @property
    def prompt_votes(self) -> int:
        return len(self.prompts)

    @property
    def scale_votes(self) -> int:
        """只统计真实观察尺度，不把独立 Prompt 复核伪装成跨尺度证据。"""
        scale_sources = {"FULL_IMAGE", "TILE", "LOCAL_VERIFY"}
        return len(self.sources.intersection(scale_sources))


@dataclass(frozen=True)
class TrustAssessment:
    level: str
    reasons: tuple[str, ...]


def generate_tiles(
    width: int,
    height: int,
    rows: int = 2,
    cols: int = 2,
    overlap: float = 0.18,
) -> list[TileRegion]:
    if width <= 0 or height <= 0:
        raise ValueError("图片宽高必须为正数")
    if rows <= 0 or cols <= 0:
        raise ValueError("Tile 行列数必须为正数")
    if not 0.0 <= overlap < 0.5:
        raise ValueError("Tile overlap 必须位于 [0, 0.5)")

    base_w = width / cols
    base_h = height / rows
    pad_x = base_w * overlap / 2.0
    pad_y = base_h * overlap / 2.0
    tiles: list[TileRegion] = []
    index = 0
    for row in range(rows):
        for col in range(cols):
            raw_x0 = col * base_w - (pad_x if col > 0 else 0.0)
            raw_x1 = (col + 1) * base_w + (pad_x if col < cols - 1 else 0.0)
            raw_y0 = row * base_h - (pad_y if row > 0 else 0.0)
            raw_y1 = (row + 1) * base_h + (pad_y if row < rows - 1 else 0.0)
            x0 = max(0, min(width - 1, int(round(raw_x0))))
            y0 = max(0, min(height - 1, int(round(raw_y0))))
            x1 = max(x0 + 1, min(width, int(round(raw_x1))))
            y1 = max(y0 + 1, min(height, int(round(raw_y1))))
            tiles.append(TileRegion(index, row, col, x0, y0, x1, y1))
            index += 1
    return tiles


def map_tile_box_to_image(
    box_xyxy: Iterable[float],
    tile: TileRegion,
    tile_width: int,
    tile_height: int,
    image_width: int,
    image_height: int,
) -> list[float]:
    if tile_width <= 0 or tile_height <= 0 or image_width <= 0 or image_height <= 0:
        raise ValueError("图片与 Tile 尺寸必须为正数")
    x1, y1, x2, y2 = [float(v) for v in box_xyxy]
    sx = tile.width / float(tile_width)
    sy = tile.height / float(tile_height)
    mapped = [
        tile.x0 + x1 * sx,
        tile.y0 + y1 * sy,
        tile.x0 + x2 * sx,
        tile.y0 + y2 * sy,
    ]
    mapped[0] = max(0.0, min(float(image_width), mapped[0]))
    mapped[1] = max(0.0, min(float(image_height), mapped[1]))
    mapped[2] = max(mapped[0], min(float(image_width), mapped[2]))
    mapped[3] = max(mapped[1], min(float(image_height), mapped[3]))
    return mapped


def _iou(a: list[float], b: list[float]) -> float:
    ax1, ay1, ax2, ay2 = a
    bx1, by1, bx2, by2 = b
    ix1, iy1 = max(ax1, bx1), max(ay1, by1)
    ix2, iy2 = min(ax2, bx2), min(ay2, by2)
    inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
    area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
    area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
    union = area_a + area_b - inter
    return 0.0 if union <= 0 else inter / union


def _new_fused(candidate: Candidate) -> FusedCandidate:
    prompts = (
        {candidate.prompt.strip().lower()}
        if candidate.prompt and candidate.prompt.strip()
        else set()
    )
    return FusedCandidate(
        box_xyxy=[float(v) for v in candidate.box_xyxy],
        max_confidence=float(candidate.score),
        class_code=candidate.class_code,
        class_name=candidate.class_name,
        prompts=prompts,
        sources={candidate.source},
        source_ids={candidate.source_id},
        local_verified=candidate.source == "LOCAL_VERIFY",
    )


def fuse_candidates(
    candidates: Iterable[Candidate], iou_threshold: float = 0.5
) -> list[FusedCandidate]:
    ordered = sorted(candidates, key=lambda c: float(c.score), reverse=True)
    fused: list[FusedCandidate] = []
    for candidate in ordered:
        match = None
        for existing in fused:
            if existing.class_code != candidate.class_code:
                continue
            if _iou(existing.box_xyxy, candidate.box_xyxy) >= iou_threshold:
                match = existing
                break
        if match is None:
            fused.append(_new_fused(candidate))
            continue
        if candidate.prompt and candidate.prompt.strip():
            match.prompts.add(candidate.prompt.strip().lower())
        match.sources.add(candidate.source)
        match.source_ids.add(candidate.source_id)
        match.local_verified = match.local_verified or candidate.source == "LOCAL_VERIFY"
        if float(candidate.score) > match.max_confidence:
            match.max_confidence = float(candidate.score)
            match.box_xyxy = [float(v) for v in candidate.box_xyxy]
            match.class_name = candidate.class_name
    return sorted(fused, key=lambda c: c.max_confidence, reverse=True)


def box_area_ratio(
    candidate: FusedCandidate, image_width: int, image_height: int
) -> float:
    if image_width <= 0 or image_height <= 0:
        return 0.0
    x1, y1, x2, y2 = candidate.box_xyxy
    area = max(0.0, x2 - x1) * max(0.0, y2 - y1)
    return min(1.0, area / float(image_width * image_height))


def needs_local_verification(
    candidate: FusedCandidate,
    options: PrecisionInferenceOptions,
    image_width: int,
    image_height: int,
) -> bool:
    if candidate.class_code.upper() != "CRACK":
        return False
    return box_area_ratio(candidate, image_width, image_height) >= options.large_box_area


def score_trust(
    candidate: FusedCandidate,
    options: PrecisionInferenceOptions,
    image_width: int,
    image_height: int,
) -> TrustAssessment:
    reasons: list[str] = []
    area = box_area_ratio(candidate, image_width, image_height)
    prompt_confirmed = candidate.prompt_votes >= options.minimum_prompt_votes
    cross_scale_confirmed = candidate.scale_votes >= 2
    local_confirmed = candidate.local_verified

    if prompt_confirmed:
        reasons.append("MULTI_PROMPT_CONFIRMED")
    if cross_scale_confirmed:
        reasons.append("CROSS_SCALE_CONFIRMED")
    if local_confirmed:
        reasons.append("LOCAL_VERIFY_CONFIRMED")

    large_crack = (
        candidate.class_code.upper() == "CRACK" and area >= options.large_box_area
    )
    if large_crack and not local_confirmed:
        reasons.append("LOW_TRUST_LARGE_BOX")
        return TrustAssessment("LOW", tuple(reasons))

    independent_evidence = sum(
        [prompt_confirmed, cross_scale_confirmed, local_confirmed]
    )
    if (
        candidate.max_confidence >= 0.30
        and prompt_confirmed
        and cross_scale_confirmed
    ):
        return TrustAssessment("HIGH", tuple(reasons))

    # 精度优先：MEDIUM 必须至少有两类独立证据；单一证据即使置信度高也只保留为诊断候选。
    if independent_evidence < 2:
        reasons.append("INSUFFICIENT_INDEPENDENT_EVIDENCE")
        return TrustAssessment("LOW", tuple(reasons))

    if candidate.max_confidence < options.medium_confidence_floor:
        reasons.append("BELOW_MEDIUM_CONFIDENCE_FLOOR")
        return TrustAssessment("LOW", tuple(reasons))

    return TrustAssessment("MEDIUM", tuple(reasons))
