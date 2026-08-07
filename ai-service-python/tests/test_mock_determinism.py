"""确定性 MOCK 适配器测试。

同一图片和同一模型版本多次推理必须返回结构一致结果。
"""

from __future__ import annotations

import hashlib

from app.adapters.mock import DeterministicMockAdapter
from app.image import decode_image
from app.schemas import Applicability, InferenceMode
from app.config import get_settings
from tests.conftest import find_image_for_branch, make_image_bytes


def _adapter():
    settings = get_settings()
    return DeterministicMockAdapter(
        model_id=settings.mock_model_id,
        model_name=settings.mock_model_name,
        model_version=settings.mock_model_version,
    )


def _decoded(image_bytes):
    return decode_image(image_bytes, get_settings())


def test_same_image_produces_identical_result(jpeg_bytes):
    adapter = _adapter()
    decoded = _decoded(jpeg_bytes)

    first = adapter.predict(decoded)
    for _ in range(3):
        assert adapter.predict(decoded) == first


def test_different_model_version_may_change_result(jpeg_bytes):
    """不同模型版本允许结果变化，但必须可解释并记录版本。"""
    settings = get_settings()
    decoded = _decoded(jpeg_bytes)
    adapter_v1 = DeterministicMockAdapter(
        settings.mock_model_id, settings.mock_model_name, "0.1.0"
    )
    adapter_v2 = DeterministicMockAdapter(
        settings.mock_model_id, settings.mock_model_name, "0.2.0"
    )
    # 结果各自确定。
    assert adapter_v1.predict(decoded) == adapter_v1.predict(decoded)
    assert adapter_v2.predict(decoded) == adapter_v2.predict(decoded)
    # 种子不同，允许不同；此处不强制相异，只验证各自稳定。


def test_seed_uses_image_bytes_and_model_version(jpeg_bytes):
    digest = hashlib.sha256(jpeg_bytes + b"0.1.0").hexdigest()
    expected_branch = int(digest[0:2], 16) % 4
    decoded = _decoded(jpeg_bytes)
    applicability, detections = _adapter().predict(decoded)

    if expected_branch == 0:
        assert applicability == Applicability.APPLICABLE
        assert len(detections) == 1
    elif expected_branch == 1:
        assert applicability == Applicability.APPLICABLE
        assert len(detections) == 2
    elif expected_branch == 2:
        assert applicability == Applicability.NO_DEFECT_FOUND
        assert detections == []
    else:
        assert applicability == Applicability.NOT_APPLICABLE
        assert detections == []


def test_all_branches_covered_deterministically():
    """四个分支都能被确定性地触发。"""
    branches_seen = set()
    for target in range(4):
        image_bytes = find_image_for_branch(target)
        decoded = _decoded(image_bytes)
        applicability, detections = _adapter().predict(decoded)
        if applicability == Applicability.APPLICABLE and len(detections) == 1:
            branches_seen.add(0)
        elif applicability == Applicability.APPLICABLE and len(detections) == 2:
            branches_seen.add(1)
        elif applicability == Applicability.NO_DEFECT_FOUND:
            branches_seen.add(2)
        elif applicability == Applicability.NOT_APPLICABLE:
            branches_seen.add(3)
    assert branches_seen == {0, 1, 2, 3}


def test_low_quality_image_has_no_detections(tiny_png):
    decoded = _decoded(tiny_png)
    applicability, detections = _adapter().predict(decoded)
    assert applicability == Applicability.LOW_QUALITY
    assert detections == []
