from PIL import Image

from app.precision import Candidate, PrecisionInferenceOptions
from app.precision_inference import PrecisionInferenceEngine


class FakeMask:
    def __init__(self, area):
        self.area = area

    def sum(self):
        return self.area


class FakePrecisionEngine(PrecisionInferenceEngine):
    def __init__(self, scripted, masks=None):
        super().__init__(adapter=None, options=PrecisionInferenceOptions())
        self.scripted = scripted
        self.detect_calls = []
        self.segmented_boxes = []
        self.masks = masks

    def _detect_candidates(
        self,
        pil,
        source,
        source_id,
        region=None,
        class_code_filter=None,
        threshold=None,
        prompt_override=None,
    ):
        self.detect_calls.append(
            (source, source_id, class_code_filter, prompt_override)
        )
        return list(
            self.scripted.get(
                (source, source_id, prompt_override),
                self.scripted.get((source, source_id), []),
            )
        )

    def _class_prompts(self, class_code):
        if class_code == "CRACK":
            return ["wall crack", "concrete crack", "surface crack"]
        return ["concrete spalling", "damaged concrete surface"]

    def _segment_final(self, pil, candidates):
        self.segmented_boxes = [list(c.box_xyxy) for c in candidates]
        if self.masks is not None:
            return list(self.masks[: len(candidates)])
        return [None] * len(candidates)


def c(
    box,
    score=0.35,
    code="CRACK",
    prompt="wall crack",
    source="FULL_IMAGE",
    source_id="full",
):
    return Candidate(
        list(box),
        score,
        code,
        "疑似裂缝" if code == "CRACK" else "疑似剥落",
        prompt,
        source,
        source_id,
    )


def test_precision_runs_one_full_and_four_tile_scans():
    scripted = {("FULL_IMAGE", "full"): [c([100, 100, 200, 200])]}
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    sources = [(call[0], call[1]) for call in engine.detect_calls]
    assert sources[:5] == [
        ("FULL_IMAGE", "full"),
        ("TILE", "tile-0"),
        ("TILE", "tile-1"),
        ("TILE", "tile-2"),
        ("TILE", "tile-3"),
    ]
    assert result == []
    assert engine.segmented_boxes == []


def test_low_trust_single_view_candidate_is_filtered_before_sam():
    scripted = {
        ("FULL_IMAGE", "full"): [c([100, 100, 220, 220], score=0.42)]
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))

    assert result == []
    assert engine.segmented_boxes == []


def test_near_full_crack_without_local_confirmation_is_not_segmented_or_returned():
    scripted = {
        ("FULL_IMAGE", "full"): [c([0, 0, 980, 780], score=0.42)]
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert result == []
    assert engine.segmented_boxes == []
    assert any(call[0] == "LOCAL_VERIFY" for call in engine.detect_calls)


def test_local_only_large_crack_replacement_is_still_filtered():
    scripted = {
        ("FULL_IMAGE", "full"): [c([0, 0, 980, 780], score=0.42)],
        ("LOCAL_VERIFY", "verify-0"): [
            c(
                [120, 130, 240, 300],
                score=0.38,
                source="LOCAL_VERIFY",
                source_id="verify-0",
            )
        ],
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert result == []
    assert engine.segmented_boxes == []


def test_large_crack_replacement_with_tile_and_prompt_confirmation_is_retained():
    scripted = {
        ("FULL_IMAGE", "full"): [c([0, 0, 980, 780], score=0.42)],
        ("TILE", "tile-0"): [
            c(
                [118, 128, 242, 302],
                score=0.36,
                prompt="concrete crack",
                source="TILE",
                source_id="tile-0",
            )
        ],
        ("LOCAL_VERIFY", "verify-0"): [
            c(
                [120, 130, 240, 300],
                score=0.40,
                prompt="wall crack",
                source="LOCAL_VERIFY",
                source_id="verify-0",
            )
        ],
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert len(result) == 1
    assert result[0].candidate.box_xyxy == [120.0, 130.0, 240.0, 300.0]
    assert "LOCAL_VERIFY_CONFIRMED" in result[0].trust.reasons
    assert "CROSS_SCALE_CONFIRMED" in result[0].trust.reasons


def test_cross_scale_candidates_are_segmented_only_after_fusion():
    scripted = {
        ("FULL_IMAGE", "full"): [
            c([100, 100, 300, 300], score=0.34, prompt="wall crack")
        ],
        ("TILE", "tile-0"): [
            c(
                [105, 105, 295, 295],
                score=0.36,
                prompt="concrete crack",
                source="TILE",
                source_id="tile-0",
            )
        ],
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert len(result) == 1
    assert result[0].trust.level == "HIGH"
    assert engine.segmented_boxes == [[105.0, 105.0, 295.0, 295.0]]


def test_same_prompt_cross_scale_candidate_gets_independent_prompt_consensus():
    scripted = {
        ("FULL_IMAGE", "full"): [
            c([100, 100, 300, 300], score=0.34, prompt="wall crack")
        ],
        ("TILE", "tile-0"): [
            c(
                [105, 105, 295, 295],
                score=0.35,
                prompt="wall crack",
                source="TILE",
                source_id="tile-0",
            )
        ],
        ("PROMPT_VERIFY", "prompt-crack-0", "wall crack"): [
            c(
                [108, 108, 292, 292],
                score=0.36,
                prompt="wall crack",
                source="PROMPT_VERIFY",
                source_id="prompt-crack-0",
            )
        ],
        ("PROMPT_VERIFY", "prompt-crack-1", "concrete crack"): [
            c(
                [110, 110, 290, 290],
                score=0.33,
                prompt="concrete crack",
                source="PROMPT_VERIFY",
                source_id="prompt-crack-1",
            )
        ],
    }
    engine = FakePrecisionEngine(scripted)
    result = engine.run_pil(Image.new("RGB", (1000, 800)))

    assert len(result) == 1
    assert result[0].candidate.prompt_votes >= 2
    assert result[0].candidate.scale_votes == 2
    assert result[0].trust.level == "HIGH"
    prompt_calls = [call for call in engine.detect_calls if call[0] == "PROMPT_VERIFY"]
    assert any(call[3] == "wall crack" for call in prompt_calls)
    assert any(call[3] == "concrete crack" for call in prompt_calls)


def test_blocky_crack_mask_is_rejected_after_sam():
    scripted = {
        ("FULL_IMAGE", "full"): [
            c([100, 100, 300, 300], score=0.40, prompt="wall crack")
        ],
        ("TILE", "tile-0"): [
            c(
                [105, 105, 295, 295],
                score=0.38,
                prompt="concrete crack",
                source="TILE",
                source_id="tile-0",
            )
        ],
    }
    # 190x190 最终框面积约 36100，mask 占 80%，且框近似方形，应视为块状伪裂缝。
    engine = FakePrecisionEngine(scripted, masks=[FakeMask(28880)])
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert result == []


def test_slender_low_fill_crack_mask_remains_high():
    scripted = {
        ("FULL_IMAGE", "full"): [
            c([100, 100, 300, 300], score=0.40, prompt="wall crack")
        ],
        ("TILE", "tile-0"): [
            c(
                [105, 105, 295, 295],
                score=0.38,
                prompt="concrete crack",
                source="TILE",
                source_id="tile-0",
            )
        ],
    }
    engine = FakePrecisionEngine(scripted, masks=[FakeMask(1800)])
    result = engine.run_pil(Image.new("RGB", (1000, 800)))
    assert len(result) == 1
    assert result[0].trust.level == "HIGH"
    assert result[0].diagnostics["maskFillRatio"] < 0.10
