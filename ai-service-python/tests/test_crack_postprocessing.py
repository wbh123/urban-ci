"""裂缝分割后处理安全门禁回归测试。"""

from __future__ import annotations

import numpy as np

from app.adapters.onnx_crack_segmentation import _filter_component_candidates
from app.model_manifest import ModelThresholds


def _thresholds(**overrides) -> ModelThresholds:
    values = {
        "mask": 0.5,
        "min_component_pixels": 1,
        "max_component_area_ratio": 0.60,
        "max_bounding_box_area_ratio": 0.90,
        "minimum_mean_confidence": 0.50,
        "maximum_detections": 30,
    }
    values.update(overrides)
    return ModelThresholds(**values)


def test_rejects_near_full_image_component():
    probabilities = np.full((10, 10), 0.95, dtype=np.float32)
    component = [(y, x) for y in range(10) for x in range(10)]

    candidates, oversized = _filter_component_candidates(
        probabilities, [component], _thresholds()
    )

    assert candidates == []
    assert oversized is True


def test_rejects_sparse_component_with_near_full_image_box():
    probabilities = np.full((10, 10), 0.9, dtype=np.float32)
    component = [(0, 0), (0, 9), (9, 0), (9, 9)]

    candidates, oversized = _filter_component_candidates(
        probabilities,
        [component],
        _thresholds(max_component_area_ratio=0.9, max_bounding_box_area_ratio=0.5),
    )

    assert candidates == []
    assert oversized is True


def test_filters_low_mean_confidence_without_marking_oversized():
    probabilities = np.full((8, 8), 0.2, dtype=np.float32)
    component = [(1, 1), (1, 2), (2, 1), (2, 2)]

    candidates, oversized = _filter_component_candidates(
        probabilities,
        [component],
        _thresholds(minimum_mean_confidence=0.7),
    )

    assert candidates == []
    assert oversized is False


def test_accepts_local_component_and_limits_detection_count():
    probabilities = np.full((10, 10), 0.95, dtype=np.float32)
    components = [
        [(1, 1), (1, 2), (2, 1), (2, 2)],
        [(5, 5), (5, 6), (6, 5), (6, 6)],
    ]

    candidates, oversized = _filter_component_candidates(
        probabilities,
        components,
        _thresholds(maximum_detections=1),
    )

    assert oversized is False
    assert len(candidates) == 1
    assert candidates[0].bounding_box_area_ratio == 0.04
    assert candidates[0].confidence > 0.9
