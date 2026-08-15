"""Grounding DINO + SAM2 的精度优先多尺度推理编排。"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from PIL import Image

from .precision import (
    Candidate,
    FusedCandidate,
    PrecisionInferenceOptions,
    TileRegion,
    TrustAssessment,
    box_area_ratio,
    fuse_candidates,
    generate_tiles,
    map_tile_box_to_image,
    needs_local_verification,
    score_trust,
)


@dataclass
class PrecisionCandidateResult:
    candidate: FusedCandidate
    trust: TrustAssessment
    mask: Any | None
    diagnostics: dict[str, Any]


class PrecisionInferenceEngine:
    """整图 + Tile 候选发现，大框裂缝复核后才执行 SAM2 分割。"""

    def __init__(self, adapter, options: PrecisionInferenceOptions | None = None) -> None:
        self.adapter = adapter
        self.options = options or PrecisionInferenceOptions()

    def run_pil(self, pil: Image.Image) -> list[PrecisionCandidateResult]:
        width, height = pil.size
        raw: list[Candidate] = []
        raw.extend(self._detect_candidates(pil, "FULL_IMAGE", "full"))

        tiles = generate_tiles(
            width,
            height,
            self.options.tile_rows,
            self.options.tile_cols,
            self.options.tile_overlap,
        )
        for tile in tiles:
            crop = pil.crop((tile.x0, tile.y0, tile.x1, tile.y1))
            raw.extend(
                self._detect_candidates(
                    crop,
                    "TILE",
                    f"tile-{tile.index}",
                    region=tile,
                )
            )

        fused = fuse_candidates(raw)
        formal: list[FusedCandidate] = []
        for candidate in fused:
            if needs_local_verification(candidate, self.options, width, height):
                replacements = self._verify_large_crack(pil, candidate)
                if replacements:
                    formal.extend(replacements)
                # 无法在局部再次定位的大框裂缝仅保留为诊断信号，不进入正式 Detection。
                continue
            formal.append(candidate)

        # Tile 与 LOCAL_VERIFY 可能对同一真实病害给出高度重叠的小框；在 SAM2 前再融合一次，
        # 既避免重复分割/重复 Detection，又保留多来源、多 Prompt 的独立证据。
        formal = self._deduplicate_final_candidates(formal)
        # 对最值得保留的少量候选进行独立 Prompt 局部复核。PROMPT_VERIFY 只增加文本证据，
        # 不计入 FULL_IMAGE/TILE/LOCAL_VERIFY 的跨尺度票数。
        formal = self._apply_prompt_consensus(pil, formal)

        # 精度优先：在 SAM2 之前就移除 LOW。mask 诊断只会进一步降级或拒绝，
        # 不会把 LOW 升级成 MEDIUM/HIGH，因此弱候选没有必要消耗分割计算。
        formal = [
            item
            for item in formal
            if score_trust(item, self.options, width, height).level in {"HIGH", "MEDIUM"}
        ]
        formal = sorted(formal, key=lambda item: item.max_confidence, reverse=True)
        formal = formal[: self.options.max_candidates_before_segmentation]
        if not formal:
            self._segment_final(pil, [])
            return []

        masks = self._segment_final(pil, formal)
        results: list[PrecisionCandidateResult] = []
        for index, candidate in enumerate(formal):
            mask = masks[index] if index < len(masks) else None
            assessment = score_trust(candidate, self.options, width, height)
            diagnostics = self._mask_diagnostics(mask, candidate, width, height)
            if diagnostics.get("reject"):
                assessment = self._force_low(
                    assessment, *diagnostics.get("reasons", [])
                )
            elif diagnostics.get("suspicious"):
                assessment = self._downgrade(
                    assessment, *diagnostics.get("reasons", [])
                )
            # SAM2 形态检查后掉到 LOW 的候选同样只保留为内部诊断，不对外输出。
            if assessment.level == "LOW":
                continue
            results.append(
                PrecisionCandidateResult(candidate, assessment, mask, diagnostics)
            )
        return results

    def _verify_large_crack(
        self, pil: Image.Image, candidate: FusedCandidate
    ) -> list[FusedCandidate]:
        width, height = pil.size
        x1, y1, x2, y2 = candidate.box_xyxy
        box_w = max(1.0, x2 - x1)
        box_h = max(1.0, y2 - y1)
        pad_x = box_w * self.options.local_verify_padding
        pad_y = box_h * self.options.local_verify_padding
        rx0 = max(0, int(round(x1 - pad_x)))
        ry0 = max(0, int(round(y1 - pad_y)))
        rx1 = min(width, int(round(x2 + pad_x)))
        ry1 = min(height, int(round(y2 + pad_y)))
        if rx1 <= rx0 or ry1 <= ry0:
            return []

        local_tiles = generate_tiles(
            rx1 - rx0,
            ry1 - ry0,
            rows=2,
            cols=2,
            overlap=self.options.tile_overlap,
        )
        verified_raw: list[Candidate] = []
        for local in local_tiles:
            region = TileRegion(
                index=local.index,
                row=local.row,
                col=local.col,
                x0=rx0 + local.x0,
                y0=ry0 + local.y0,
                x1=rx0 + local.x1,
                y1=ry0 + local.y1,
            )
            crop = pil.crop((region.x0, region.y0, region.x1, region.y1))
            candidates = self._detect_candidates(
                crop,
                "LOCAL_VERIFY",
                f"verify-{local.index}",
                region=region,
                class_code_filter="CRACK",
                threshold=self.options.local_verify_threshold,
            )
            for item in candidates:
                if item.class_code.upper() != "CRACK":
                    continue
                temp = fuse_candidates([item])[0]
                if box_area_ratio(temp, width, height) >= self.options.large_box_area:
                    continue
                if item.score < self.options.local_verify_threshold:
                    continue
                verified_raw.append(item)
        return fuse_candidates(verified_raw)

    def _apply_prompt_consensus(
        self, pil: Image.Image, candidates: list[FusedCandidate]
    ) -> list[FusedCandidate]:
        if not candidates or self.options.prompt_verify_candidates <= 0:
            return candidates
        width, height = pil.size
        ranked = sorted(candidates, key=lambda item: item.max_confidence, reverse=True)
        for candidate in ranked[: self.options.prompt_verify_candidates]:
            if candidate.prompt_votes >= self.options.minimum_prompt_votes:
                continue
            prompts = self._class_prompts(candidate.class_code)
            if len(prompts) < self.options.minimum_prompt_votes:
                continue
            region = self._candidate_region(candidate, width, height)
            crop = pil.crop((region.x0, region.y0, region.x1, region.y1))
            for prompt_index, prompt in enumerate(
                prompts[: self.options.prompt_verify_max_prompts]
            ):
                source_id = f"prompt-{candidate.class_code.lower()}-{prompt_index}"
                detected = self._detect_candidates(
                    crop,
                    "PROMPT_VERIFY",
                    source_id,
                    region=region,
                    class_code_filter=candidate.class_code,
                    threshold=self.options.prompt_verify_threshold,
                    prompt_override=prompt,
                )
                related = [
                    item
                    for item in detected
                    if item.class_code == candidate.class_code
                    and self._spatially_related(candidate.box_xyxy, item.box_xyxy)
                ]
                if related:
                    best = max(related, key=lambda item: item.score)
                    candidate.prompts.add(prompt.strip().lower())
                    candidate.sources.add("PROMPT_VERIFY")
                    candidate.source_ids.add(source_id)
                    candidate.max_confidence = max(
                        candidate.max_confidence, float(best.score)
                    )
                if candidate.prompt_votes >= self.options.minimum_prompt_votes:
                    break
        return candidates

    def _class_prompts(self, class_code: str) -> list[str]:
        if self.adapter is None:
            return []
        manifest_class = next(
            (
                item
                for item in self.adapter._manifest.classes
                if item.code == class_code
            ),
            None,
        )
        if manifest_class is None:
            return []
        prompts: list[str] = []
        for prompt in manifest_class.prompts:
            cleaned = prompt.strip().lower()
            if cleaned and cleaned not in prompts:
                prompts.append(cleaned)
        return prompts

    def _candidate_region(
        self, candidate: FusedCandidate, width: int, height: int
    ) -> TileRegion:
        x1, y1, x2, y2 = candidate.box_xyxy
        box_w = max(1.0, x2 - x1)
        box_h = max(1.0, y2 - y1)
        pad_x = max(box_w * self.options.local_verify_padding, width * 0.02)
        pad_y = max(box_h * self.options.local_verify_padding, height * 0.02)
        rx0 = max(0, int(round(x1 - pad_x)))
        ry0 = max(0, int(round(y1 - pad_y)))
        rx1 = min(width, int(round(x2 + pad_x)))
        ry1 = min(height, int(round(y2 + pad_y)))
        return TileRegion(-1, -1, -1, rx0, ry0, max(rx0 + 1, rx1), max(ry0 + 1, ry1))

    def _detect_candidates(
        self,
        pil: Image.Image,
        source: str,
        source_id: str,
        region: TileRegion | None = None,
        class_code_filter: str | None = None,
        threshold: float | None = None,
        prompt_override: str | None = None,
    ) -> list[Candidate]:
        """只执行 DINO 候选发现；这里绝不调用 SAM2。"""
        if self.adapter is None:
            return []
        from .adapters.grounded_sam2 import _extract_top_detections, _resize_long_side

        long_side = int(self.adapter._settings.vision_max_long_side or 1280)
        resized = _resize_long_side(pil, long_side)
        resized_width, resized_height = resized.size

        if prompt_override:
            cleaned = prompt_override.strip().lower()
            if not cleaned:
                return []
            phrases = [cleaned]
            prompt = cleaned + "."
        elif class_code_filter:
            phrases = self._class_prompts(class_code_filter)
            if not phrases:
                return []
            prompt = ". ".join(phrases) + "."
        else:
            prompt, phrases = self.adapter._build_prompt()

        inputs = self.adapter._dino_processor(
            images=resized, text=prompt, return_tensors="pt"
        ).to(self.adapter._device)
        for key, value in inputs.items():
            if value.is_floating_point():
                inputs[key] = value.to(self.adapter._dino_dtype)
        with self.adapter._torch.inference_mode():
            outputs = self.adapter._dino(**inputs)

        post_kwargs = dict(self.adapter._post_kwargs)
        effective = float(
            threshold
            if threshold is not None
            else self.options.candidate_box_threshold
        )
        for key in ("box_threshold", "threshold"):
            if key in post_kwargs:
                post_kwargs[key] = effective
        if "text_threshold" in post_kwargs:
            post_kwargs["text_threshold"] = (
                float(threshold)
                if threshold is not None
                else self.options.candidate_text_threshold
            )
        results = self.adapter._dino_processor.post_process_grounded_object_detection(
            outputs,
            inputs.input_ids,
            target_sizes=[(resized_height, resized_width)],
            **post_kwargs,
        )
        maximum = int(
            getattr(self.adapter._manifest.input, "max_detections", 10)
            or self.options.max_candidates_before_segmentation
        )
        boxes, scores, labels = _extract_top_detections(results, maximum)
        out: list[Candidate] = []
        for box, score, label in zip(boxes, scores, labels, strict=True):
            code, name = self.adapter._match_class(label, phrases)
            if code is None or (class_code_filter and code != class_code_filter):
                continue
            if isinstance(label, int) and 0 <= label < len(phrases):
                matched_prompt = phrases[label]
            else:
                matched_prompt = str(label).strip().lower()

            if region is None:
                sx = pil.width / float(resized_width)
                sy = pil.height / float(resized_height)
                mapped = [
                    float(box[0]) * sx,
                    float(box[1]) * sy,
                    float(box[2]) * sx,
                    float(box[3]) * sy,
                ]
            else:
                mapped = map_tile_box_to_image(
                    box,
                    region,
                    resized_width,
                    resized_height,
                    region.x1,
                    region.y1,
                )
                mapped[0] = max(float(region.x0), mapped[0])
                mapped[1] = max(float(region.y0), mapped[1])
                mapped[2] = min(float(region.x1), mapped[2])
                mapped[3] = min(float(region.y1), mapped[3])
            if mapped[2] <= mapped[0] or mapped[3] <= mapped[1]:
                continue
            out.append(
                Candidate(
                    mapped,
                    float(score),
                    code,
                    name,
                    matched_prompt,
                    source,
                    source_id,
                )
            )
        return out

    @staticmethod
    def _deduplicate_final_candidates(
        candidates: list[FusedCandidate], iou_threshold: float = 0.5
    ) -> list[FusedCandidate]:
        """最终候选二次融合：合并 Tile/局部复核重复框并累计独立证据。"""
        ordered = sorted(
            candidates, key=lambda item: item.max_confidence, reverse=True
        )
        kept: list[FusedCandidate] = []
        for candidate in ordered:
            matched: FusedCandidate | None = None
            for existing in kept:
                if existing.class_code != candidate.class_code:
                    continue
                if PrecisionInferenceEngine._box_iou(
                    existing.box_xyxy, candidate.box_xyxy
                ) >= iou_threshold:
                    matched = existing
                    break
            if matched is None:
                kept.append(candidate)
                continue
            matched.prompts.update(candidate.prompts)
            matched.sources.update(candidate.sources)
            matched.source_ids.update(candidate.source_ids)
            matched.local_verified = matched.local_verified or candidate.local_verified
            if candidate.max_confidence > matched.max_confidence:
                matched.max_confidence = candidate.max_confidence
                matched.box_xyxy = list(candidate.box_xyxy)
                matched.class_name = candidate.class_name
        return kept

    @staticmethod
    def _spatially_related(a: list[float], b: list[float]) -> bool:
        ax1, ay1, ax2, ay2 = a
        bx1, by1, bx2, by2 = b
        ix1, iy1 = max(ax1, bx1), max(ay1, by1)
        ix2, iy2 = min(ax2, bx2), min(ay2, by2)
        inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
        area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
        area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
        smaller = min(area_a, area_b)
        return smaller > 0 and inter / smaller >= 0.30

    @staticmethod
    def _box_iou(a: list[float], b: list[float]) -> float:
        ax1, ay1, ax2, ay2 = a
        bx1, by1, bx2, by2 = b
        ix1, iy1 = max(ax1, bx1), max(ay1, by1)
        ix2, iy2 = min(ax2, bx2), min(ay2, by2)
        inter = max(0.0, ix2 - ix1) * max(0.0, iy2 - iy1)
        area_a = max(0.0, ax2 - ax1) * max(0.0, ay2 - ay1)
        area_b = max(0.0, bx2 - bx1) * max(0.0, by2 - by1)
        union = area_a + area_b - inter
        return 0.0 if union <= 0 else inter / union

    def _segment_final(self, pil: Image.Image, candidates: list[FusedCandidate]):
        if not candidates or self.adapter is None:
            return []
        return self.adapter._sam2_masks(
            pil, [item.box_xyxy for item in candidates]
        )

    def _mask_diagnostics(
        self,
        mask,
        candidate: FusedCandidate,
        width: int,
        height: int,
    ) -> dict[str, Any]:
        diagnostics: dict[str, Any] = {
            "bboxAreaRatio": round(box_area_ratio(candidate, width, height), 4),
            "suspicious": False,
            "reject": False,
            "reasons": [],
        }
        if mask is None or not hasattr(mask, "sum"):
            return diagnostics
        mask_area = float(mask.sum())
        image_area = float(max(1, width * height))
        x1, y1, x2, y2 = candidate.box_xyxy
        bbox_area = float(max(1.0, (x2 - x1) * (y2 - y1)))
        diagnostics["maskAreaRatio"] = round(mask_area / image_area, 4)
        diagnostics["maskFillRatio"] = round(
            min(1.0, mask_area / bbox_area), 4
        )
        if candidate.class_code.upper() == "CRACK":
            box_w = max(1.0, x2 - x1)
            box_h = max(1.0, y2 - y1)
            aspect = max(box_w / box_h, box_h / box_w)
            fill = diagnostics["maskFillRatio"]
            # 裂缝通常应呈细长或低填充形态。近方形框内超过 55% 都被掩膜占据时，
            # 更像污渍/剥落/墙面纹理，不允许作为正式裂缝输出。
            if fill >= 0.55 and aspect < 3.0:
                diagnostics["reject"] = True
                diagnostics["reasons"].append("BLOCKY_CRACK_MASK")
            elif fill >= 0.35 and aspect < 3.0:
                diagnostics["suspicious"] = True
                diagnostics["reasons"].append("DENSE_CRACK_MASK")
            if diagnostics["maskAreaRatio"] >= self.options.near_full_box_area:
                diagnostics["reject"] = True
                diagnostics["reasons"].append("NEAR_FULL_MASK")
        return diagnostics

    @staticmethod
    def _downgrade(
        assessment: TrustAssessment, *reasons: str
    ) -> TrustAssessment:
        order = {"HIGH": "MEDIUM", "MEDIUM": "LOW", "LOW": "LOW"}
        merged = list(assessment.reasons)
        for reason in reasons:
            if reason and reason not in merged:
                merged.append(reason)
        return TrustAssessment(order[assessment.level], tuple(merged))

    @staticmethod
    def _force_low(
        assessment: TrustAssessment, *reasons: str
    ) -> TrustAssessment:
        merged = list(assessment.reasons)
        for reason in reasons:
            if reason and reason not in merged:
                merged.append(reason)
        return TrustAssessment("LOW", tuple(merged))
