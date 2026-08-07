"""无需模型权重的本地图片质量与适用性预检。"""

from __future__ import annotations

import io

import numpy as np
from PIL import Image, UnidentifiedImageError

from .config import Settings
from .errors import (
    ImageDecodeFailedError,
    ImageEmptyError,
    ImageTooLargeError,
    ImageUnsupportedFormatError,
)
from .image import FORMAT_TO_CONTENT_TYPE
from .schemas import ImageQualityResponse

LOCAL_IMAGE_QUALITY_MODEL_ID = "LOCAL-IMAGE-QUALITY-001"
LOCAL_IMAGE_QUALITY_MODEL_VERSION = "0.1.0"

# 这些阈值只用于低成本拍摄质量门控，不代表结构病害概率。
_BLANK_CONTRAST_THRESHOLD = 0.015
_UNDEREXPOSED_THRESHOLD = 0.08
_OVEREXPOSED_THRESHOLD = 0.92
_BLUR_SHARPNESS_THRESHOLD = 0.012


def analyze_image_quality(
    image_bytes: bytes,
    settings: Settings,
    request_id: str = "QUALITY",
) -> ImageQualityResponse:
    """解码图片并返回确定性的亮度、对比度与清晰度指标。"""

    if not image_bytes:
        raise ImageEmptyError()
    if len(image_bytes) > settings.max_image_size_bytes:
        raise ImageTooLargeError()

    try:
        image = Image.open(io.BytesIO(image_bytes))
        image.load()
    except (UnidentifiedImageError, OSError, ValueError) as ex:
        raise ImageDecodeFailedError() from ex

    image_format = (image.format or "").upper()
    content_type = FORMAT_TO_CONTENT_TYPE.get(image_format)
    if content_type is None:
        raise ImageUnsupportedFormatError()

    width, height = image.size
    if width <= 0 or height <= 0:
        raise ImageDecodeFailedError()

    grayscale = np.asarray(image.convert("L"), dtype=np.float32) / 255.0
    brightness = float(np.mean(grayscale))
    contrast = float(np.std(grayscale))
    sharpness = _gradient_sharpness(grayscale)

    blank = contrast < _BLANK_CONTRAST_THRESHOLD
    underexposed = brightness < _UNDEREXPOSED_THRESHOLD
    overexposed = brightness > _OVEREXPOSED_THRESHOLD
    blur_detected = sharpness < _BLUR_SHARPNESS_THRESHOLD
    low_resolution = min(width, height) < settings.low_quality_min_side

    reasons: list[str] = []
    if blank:
        reasons.append("IMAGE_BLANK_OR_LOW_CONTRAST")
    if underexposed:
        reasons.append("IMAGE_UNDEREXPOSED")
    if overexposed:
        reasons.append("IMAGE_OVEREXPOSED")
    if blur_detected:
        reasons.append("IMAGE_BLURRED")
    if low_resolution:
        reasons.append("IMAGE_RESOLUTION_TOO_LOW")

    low_quality = bool(reasons)
    return ImageQualityResponse(
        requestId=request_id.strip() or "QUALITY",
        modelId=LOCAL_IMAGE_QUALITY_MODEL_ID,
        modelVersion=LOCAL_IMAGE_QUALITY_MODEL_VERSION,
        contentType=content_type,
        width=width,
        height=height,
        brightness=round(brightness, 6),
        contrast=round(contrast, 6),
        sharpness=round(sharpness, 6),
        blank=blank,
        underexposed=underexposed,
        overexposed=overexposed,
        blurDetected=blur_detected,
        lowResolution=low_resolution,
        lowQuality=low_quality,
        reshootRecommended=low_quality,
        reasons=reasons,
    )


def _gradient_sharpness(grayscale: np.ndarray) -> float:
    """使用相邻像素梯度均值估计清晰度，避免引入 OpenCV。"""

    if grayscale.ndim != 2 or min(grayscale.shape) < 2:
        return 0.0
    horizontal = np.abs(np.diff(grayscale, axis=1))
    vertical = np.abs(np.diff(grayscale, axis=0))
    return float((np.mean(horizontal) + np.mean(vertical)) / 2.0)
