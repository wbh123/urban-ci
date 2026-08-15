from pathlib import Path

import pytest
from PIL import Image

from app.adapters.florence2_locator import (
    CLASS_PHRASES,
    Florence2Locator,
    _load_with_fallback,
)
from app.errors import ModelUnavailableError


class FakeBackend:
    def __init__(self, results=None):
        self.results = results if results is not None else []
        self.calls = []
        self.closed = False

    def locate_phrase(self, image, phrase):
        self.calls.append(phrase)
        return self.results

    def close(self):
        self.closed = True


def test_florence_loader_falls_back_to_next_official_loader():
    calls = []

    def broken():
        calls.append("multimodal")
        raise RuntimeError("primary failed")

    expected = object()

    def working():
        calls.append("native")
        return expected

    model, loader_name = _load_with_fallback([
        ("AutoModelForMultimodalLM", broken),
        ("Florence2ForConditionalGeneration", working),
    ])
    assert model is expected
    assert loader_name == "Florence2ForConditionalGeneration"
    assert calls == ["multimodal", "native"]


def test_florence_loader_reports_all_underlying_errors():
    def first():
        raise RuntimeError("first boom")

    def second():
        raise ValueError("second boom")

    with pytest.raises(ModelUnavailableError) as exc:
        _load_with_fallback([
            ("loader-a", first),
            ("loader-b", second),
        ])
    message = str(exc.value)
    assert "loader-a" in message
    assert "first boom" in message
    assert "loader-b" in message
    assert "second boom" in message


def test_florence_locator_converts_candidates_to_common_xyxy():
    backend = FakeBackend([{"bbox": [20, 10, 180, 90]}])
    locator = Florence2Locator(Path("unused"), backend=backend)
    result = locator.locate(Image.new("RGB", (200, 100)), {"CRACK"})
    assert len(result) == 1
    assert result[0].box_xyxy == [20.0, 10.0, 180.0, 90.0]
    assert result[0].source == "FLORENCE2"
    assert result[0].class_code == "CRACK"
    assert result[0].score == 0.5
    assert backend.calls == [CLASS_PHRASES["CRACK"]]


def test_florence_locator_only_runs_requested_classes():
    backend = FakeBackend([])
    locator = Florence2Locator(Path("unused"), backend=backend)
    locator.locate(Image.new("RGB", (100, 100)), {"WATER_STAIN", "CRACK"})
    assert set(backend.calls) == {CLASS_PHRASES["CRACK"], CLASS_PHRASES["WATER_STAIN"]}


def test_florence_locator_empty_result_is_normal():
    locator = Florence2Locator(Path("unused"), backend=FakeBackend([]))
    assert locator.locate(Image.new("RGB", (100, 100)), {"CRACK"}) == []


def test_florence_locator_rejects_unknown_class():
    locator = Florence2Locator(Path("unused"), backend=FakeBackend([]))
    with pytest.raises(ModelUnavailableError, match="不支持的类别"):
        locator.locate(Image.new("RGB", (100, 100)), {"ALIEN"})


def test_florence_locator_malformed_result_fails_explicitly():
    locator = Florence2Locator(Path("unused"), backend=FakeBackend([{"box": [1, 2, 3, 4]}]))
    with pytest.raises(ModelUnavailableError, match="结果格式"):
        locator.locate(Image.new("RGB", (100, 100)), {"CRACK"})


def test_florence_locator_clips_box_to_image_and_drops_invalid_box():
    backend = FakeBackend([
        {"bbox": [-10, -5, 120, 110]},
        {"bbox": [50, 50, 40, 60]},
    ])
    locator = Florence2Locator(Path("unused"), backend=backend)
    result = locator.locate(Image.new("RGB", (100, 100)), {"CRACK"})
    assert len(result) == 1
    assert result[0].box_xyxy == [0.0, 0.0, 100.0, 100.0]


def test_florence_locator_close_delegates_to_backend():
    backend = FakeBackend([])
    locator = Florence2Locator(Path("unused"), backend=backend)
    locator.close()
    assert backend.closed is True
