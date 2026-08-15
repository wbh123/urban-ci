from __future__ import annotations

import io

from PIL import Image

from app.config import Settings
from app.image import decode_image, open_normalized_image


def _jpeg_with_orientation(width: int, height: int, orientation: int) -> bytes:
    image = Image.new("RGB", (width, height), (120, 80, 40))
    exif = Image.Exif()
    exif[274] = orientation
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", exif=exif)
    return buffer.getvalue()


def test_decode_image_uses_exif_corrected_dimensions_and_preserves_source(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE", "1")
    data = _jpeg_with_orientation(120, 80, 6)

    decoded = decode_image(data, Settings())

    assert (decoded.width, decoded.height) == (80, 120)
    assert decoded.source_bytes == data
    assert decoded.bytes_ != data
    inference_image = Image.open(io.BytesIO(decoded.bytes_))
    assert inference_image.size == (80, 120)


def test_decode_image_does_not_reencode_when_orientation_is_normal(monkeypatch):
    monkeypatch.setenv("URBAN_SAFE_AI_LOW_QUALITY_MIN_SIDE", "1")
    data = _jpeg_with_orientation(120, 80, 1)

    decoded = decode_image(data, Settings())

    assert decoded.source_bytes == data
    assert decoded.bytes_ == data
    assert (decoded.width, decoded.height) == (120, 80)


def test_open_normalized_image_matches_browser_visible_orientation():
    data = _jpeg_with_orientation(120, 80, 6)

    image, raw_format = open_normalized_image(data)

    assert raw_format == "JPEG"
    assert image.size == (80, 120)
