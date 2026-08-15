"""evaluate_vision_quality 纯逻辑测试（无需 GPU/权重）。"""

from __future__ import annotations

import json

from tools.evaluate_vision_quality import _collect_images, _compare_labels, _expected_map


def test_collect_images_is_recursive(tmp_path):
    (tmp_path / "crack").mkdir()
    (tmp_path / "normal").mkdir()
    (tmp_path / "crack" / "001.jpg").write_bytes(b"j")
    (tmp_path / "normal" / "wall.png").write_bytes(b"p")
    (tmp_path / "skip.txt").write_bytes(b"t")
    images = _collect_images(tmp_path)
    assert [p.relative_to(tmp_path).as_posix() for p in images] == ["crack/001.jpg", "normal/wall.png"]


def test_expected_map_reads_manifest(tmp_path):
    (tmp_path / "manifest.json").write_text(
        json.dumps({"images": [{"file": "crack/001.jpg", "expected": ["CRACK"]}]}), encoding="utf-8"
    )
    assert _expected_map(tmp_path) == {"crack/001.jpg": ["CRACK"]}


def test_expected_map_empty_when_no_manifest(tmp_path):
    assert _expected_map(tmp_path) == {}


def test_compare_labels_handles_list_expected_without_type_error():
    hit, miss, false_positive = _compare_labels(["CRACK"], {"CRACK"})
    assert hit == {"CRACK"}
    assert miss == set()
    assert false_positive == set()


def test_compare_labels_reports_miss_and_false_positive():
    hit, miss, false_positive = _compare_labels(["CRACK"], {"WATER_STAIN"})
    assert hit == set()
    assert miss == {"CRACK"}
    assert false_positive == {"WATER_STAIN"}


def test_compare_labels_supports_normal_image_expectation():
    hit, miss, false_positive = _compare_labels([], {"CRACK"})
    assert hit == set()
    assert miss == set()
    assert false_positive == {"CRACK"}
