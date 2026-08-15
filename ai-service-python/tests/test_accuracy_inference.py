from types import SimpleNamespace

import pytest
from PIL import Image

from app.accuracy import (
    AccuracyCandidate,
    LocatorCandidate,
    SemanticClassDecision,
    SemanticGateResult,
)
from app.accuracy_inference import AccuracyBatchRunner, _large_crack_mask_diagnostics
from app.errors import ModelUnavailableError


def gate(present=True, confidence=0.9):
    return SemanticGateResult({
        "CRACK": SemanticClassDecision("CRACK", present, confidence),
    })


class FakeQwen:
    def __init__(self, events, gates, fail=False):
        self.events = events
        self.gates = list(gates)
        self.index = 0
        self.fail = fail
        events.append("qwen-load")

    def classify(self, image):
        self.events.append("qwen-classify")
        if self.fail:
            raise ModelUnavailableError("qwen failed")
        result = self.gates[self.index]
        self.index += 1
        return result

    def close(self):
        self.events.append("qwen-close")


class FakeGrounded:
    def __init__(self, events):
        self.events = events
        self._manifest = SimpleNamespace(
            classes=[SimpleNamespace(code="CRACK", name="疑似裂缝")]
        )
        events.append("grounded-load")

    def release_detector(self):
        self.events.append("grounded-release-detector")

    def _sam2_masks(self, image, boxes):
        self.events.append("sam-segment")
        return [None for _ in boxes]

    def _build_detections(self, boxes, scores, codes, names, width, height, masks):
        result = []
        for i, (box, score, code, name) in enumerate(zip(boxes, scores, codes, names), 1):
            x1, y1, x2, y2 = box
            result.append({
                "sequence": i,
                "classCode": code,
                "className": name,
                "confidence": score,
                "boundingBox": {
                    "x": x1 / width,
                    "y": y1 / height,
                    "width": (x2 - x1) / width,
                    "height": (y2 - y1) / height,
                    "coordinateType": "NORMALIZED_XYWH",
                },
                "segmentation": None,
            })
        return result

    def close(self):
        self.events.append("grounded-close")


class FakeGroundLocator:
    def __init__(self, events, results):
        self.events = events
        self.results = list(results)
        self.index = 0

    def locate(self, image, allowed):
        self.events.append(("ground-locate", frozenset(allowed)))
        result = self.results[self.index]
        self.index += 1
        return result


class FakeFlorence:
    def __init__(self, events, results, fail=False):
        self.events = events
        self.results = list(results)
        self.index = 0
        self.fail = fail
        events.append("florence-load")

    def locate(self, image, allowed):
        self.events.append(("florence-locate", frozenset(allowed)))
        if self.fail:
            raise ModelUnavailableError("florence failed")
        result = self.results[self.index]
        self.index += 1
        return result

    def close(self):
        self.events.append("florence-close")


class FakeMask:
    def __init__(self, area):
        self.area = area

    def sum(self):
        return self.area


def formal_candidate(box, code="CRACK"):
    return AccuracyCandidate(
        box_xyxy=list(box),
        score=0.5,
        class_code=code,
        sources=("FLORENCE2", "GROUNDING_DINO"),
        semantic_confidence=0.9,
        trust_level="HIGH",
        trust_reasons=("SEMANTIC_GATE_CONFIRMED", "CROSS_MODEL_CONFIRMED"),
    )


def test_large_surface_like_crack_mask_is_rejected():
    result = _large_crack_mask_diagnostics(
        formal_candidate([0, 0, 900, 800]),
        FakeMask(250_000),
        1000,
        1000,
    )
    assert result["bboxAreaRatio"] == 0.72
    assert result["maskAreaRatio"] == 0.25
    assert result["maskFillRatio"] > 0.34
    assert result["reject"] is True
    assert "LARGE_SURFACE_LIKE_CRACK_MASK" in result["reasons"]


def test_large_low_fill_crack_mask_is_kept():
    result = _large_crack_mask_diagnostics(
        formal_candidate([0, 0, 900, 800]),
        FakeMask(30_000),
        1000,
        1000,
    )
    assert result["bboxAreaRatio"] == 0.72
    assert result["maskAreaRatio"] == 0.03
    assert result["maskFillRatio"] < 0.05
    assert result["reject"] is False


def test_normal_crack_box_is_not_affected_by_surface_like_mask_rule():
    result = _large_crack_mask_diagnostics(
        formal_candidate([100, 100, 600, 600]),
        FakeMask(100_000),
        1000,
        1000,
    )
    assert result["bboxAreaRatio"] == 0.25
    assert result["maskFillRatio"] == 0.4
    assert result["reject"] is False


def test_missing_mask_never_reduces_large_crack_recall():
    result = _large_crack_mask_diagnostics(
        formal_candidate([0, 0, 900, 800]),
        None,
        1000,
        1000,
    )
    assert result["reject"] is False


def test_accuracy_batch_runner_uses_stage_order_and_cross_model_confirmation():
    events = []
    qwen_gates = [gate(True, 0.9), gate(True, 0.9)]
    gd = [[LocatorCandidate([10, 10, 40, 40], 0.45, "CRACK", "GROUNDING_DINO")]] * 2
    fl = [[LocatorCandidate([12, 12, 42, 42], 0.5, "CRACK", "FLORENCE2")]] * 2
    runner = AccuracyBatchRunner(
        qwen_factory=lambda: FakeQwen(events, qwen_gates),
        grounded_factory=lambda: FakeGrounded(events),
        florence_factory=lambda: FakeFlorence(events, fl),
        grounded_locator_factory=lambda grounded: FakeGroundLocator(events, gd),
    )
    results = runner.run_batch([Image.new("RGB", (100, 100)), Image.new("RGB", (100, 100))])
    assert len(results) == 2
    assert results[0][0]["trustLevel"] == "HIGH"
    assert "CROSS_MODEL_CONFIRMED" in results[0][0]["trustReasons"]
    assert events.index("qwen-close") < events.index("grounded-load")
    assert events.index("grounded-release-detector") < events.index("florence-load")
    assert events.index("florence-close") < events.index("sam-segment")
    assert events[-1] == "grounded-close"


def test_accuracy_batch_runner_does_not_locate_high_confidence_absent_class():
    events = []
    runner = AccuracyBatchRunner(
        qwen_factory=lambda: FakeQwen(events, [gate(False, 0.95)]),
        grounded_factory=lambda: FakeGrounded(events),
        florence_factory=lambda: FakeFlorence(events, [[]]),
        grounded_locator_factory=lambda grounded: FakeGroundLocator(events, [[]]),
    )
    results = runner.run_batch([Image.new("RGB", (100, 100))])
    assert results == [[]]
    assert ("ground-locate", frozenset()) in events
    assert ("florence-locate", frozenset()) in events
    assert "sam-segment" not in events


def test_accuracy_batch_runner_only_segments_formal_candidates():
    events = []
    runner = AccuracyBatchRunner(
        qwen_factory=lambda: FakeQwen(events, [gate(None, 0.4)]),
        grounded_factory=lambda: FakeGrounded(events),
        florence_factory=lambda: FakeFlorence(events, [[]]),
        grounded_locator_factory=lambda grounded: FakeGroundLocator(
            events,
            [[LocatorCandidate([10, 10, 40, 40], 0.8, "CRACK", "GROUNDING_DINO")]],
        ),
    )
    results = runner.run_batch([Image.new("RGB", (100, 100))])
    assert results == [[]]
    assert "sam-segment" not in events


def test_accuracy_batch_runner_propagates_qwen_failure_without_fallback():
    events = []
    runner = AccuracyBatchRunner(
        qwen_factory=lambda: FakeQwen(events, [gate()], fail=True),
        grounded_factory=lambda: FakeGrounded(events),
        florence_factory=lambda: FakeFlorence(events, [[]]),
        grounded_locator_factory=lambda grounded: FakeGroundLocator(events, [[]]),
    )
    with pytest.raises(ModelUnavailableError, match="qwen failed"):
        runner.run_batch([Image.new("RGB", (100, 100))])
    assert "grounded-load" not in events
    assert "qwen-close" in events


def test_accuracy_batch_runner_propagates_florence_failure_and_releases_models():
    events = []
    runner = AccuracyBatchRunner(
        qwen_factory=lambda: FakeQwen(events, [gate()]),
        grounded_factory=lambda: FakeGrounded(events),
        florence_factory=lambda: FakeFlorence(events, [[]], fail=True),
        grounded_locator_factory=lambda grounded: FakeGroundLocator(
            events,
            [[LocatorCandidate([10, 10, 40, 40], 0.5, "CRACK", "GROUNDING_DINO")]],
        ),
    )
    with pytest.raises(ModelUnavailableError, match="florence failed"):
        runner.run_batch([Image.new("RGB", (100, 100))])
    assert "florence-close" in events
    assert events[-1] == "grounded-close"
