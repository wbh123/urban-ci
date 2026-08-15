"""图片解码与质量/适用性判断。

FastAPI 不信任客户端提供的文件名和内容类型，必须重新解码和校验。
本模块只使用 Pillow，不依赖深度学习框架，便于离线运行。
"""

from __future__ import annotations

import io
from dataclasses import dataclass

from PIL import Image, ImageOps, UnidentifiedImageError

from .config import Settings
from .errors import (
    ImageDecodeFailedError,
    ImageEmptyError,
    ImageTooLargeError,
    ImageUnsupportedFormatError,
)
from .schemas import Applicability, QualityStatus

# Pillow 支持的格式名到 MIME 的映射，仅暴露第三阶段允许的三类。
FORMAT_TO_CONTENT_TYPE = {
    "JPEG": "image/jpeg",
    "PNG": "image/png",
    "WEBP": "image/webp",
}


@dataclass
class DecodedImage:
    """解码后的图片信息。

    ``bytes_`` 是模型推理使用的字节：普通图片保持原字节，带旋转 EXIF 的图片会
    转正后重新编码；``source_bytes`` 始终保留原始上传字节用于审计/追溯。
    """

    bytes_: bytes
    source_bytes: bytes
    width: int
    height: int
    content_type: str
    quality_status: QualityStatus
    applicability: Applicability


def open_normalized_image(data: bytes) -> tuple[Image.Image, str]:
    """按 EXIF Orientation 转正图片，返回浏览器可见方向对应的 Pillow 图像与原始格式。"""

    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except (UnidentifiedImageError, OSError, ValueError) as ex:
        raise ImageDecodeFailedError() from ex

    raw_format = (image.format or "").upper()
    try:
        normalized = ImageOps.exif_transpose(image)
    except (OSError, ValueError) as ex:
        raise ImageDecodeFailedError() from ex
    return normalized, raw_format


def _exif_orientation(data: bytes) -> int:
    try:
        with Image.open(io.BytesIO(data)) as image:
            value = image.getexif().get(274, 1)
            return int(value) if value is not None else 1
    except (UnidentifiedImageError, OSError, ValueError, TypeError):
        return 1


def _inference_bytes(data: bytes, normalized: Image.Image, raw_format: str) -> bytes:
    """仅在 EXIF 需要转正时重新编码，避免普通图片无意义地改变输入字节。"""

    if _exif_orientation(data) in (0, 1):
        return data
    buffer = io.BytesIO()
    try:
        if raw_format == "JPEG":
            normalized.convert("RGB").save(buffer, format="JPEG", quality=95)
        elif raw_format == "PNG":
            normalized.save(buffer, format="PNG")
        elif raw_format == "WEBP":
            normalized.save(buffer, format="WEBP", lossless=True)
        else:
            return data
    except (OSError, ValueError) as ex:
        raise ImageDecodeFailedError() from ex
    return buffer.getvalue()


def decode_image(data: bytes, settings: Settings) -> DecodedImage:
    """校验大小、格式、解码与质量，返回受控图片信息。

    校验顺序固定，保证同一输入得到同一错误：
    1. 空文件；
    2. 超过大小限制；
    3. 格式与解码；
    4. 低质量（最小边过小）。
    """
    if not data:
        raise ImageEmptyError()
    if len(data) > settings.max_image_size_bytes:
        raise ImageTooLargeError()

    image, raw_format = open_normalized_image(data)
    content_type = FORMAT_TO_CONTENT_TYPE.get(raw_format)
    if content_type is None:
        # Pillow 解码成功但格式不在白名单（例如 GIF/BMP），按不支持格式拒绝。
        raise ImageUnsupportedFormatError()

    width, height = image.size
    if width <= 0 or height <= 0:
        raise ImageDecodeFailedError()

    min_side = min(width, height)
    if min_side < settings.low_quality_min_side:
        quality = QualityStatus.LOW_QUALITY
        applicability = Applicability.LOW_QUALITY
    else:
        quality = QualityStatus.ACCEPTABLE
        applicability = Applicability.APPLICABLE

    return DecodedImage(
        bytes_=_inference_bytes(data, image, raw_format),
        source_bytes=data,
        width=width,
        height=height,
        content_type=content_type,
        quality_status=quality,
        applicability=applicability,
    )
