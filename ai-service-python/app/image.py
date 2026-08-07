"""图片解码与质量/适用性判断。

FastAPI 不信任客户端提供的文件名和内容类型，必须重新解码和校验。
本模块只使用 Pillow，不依赖深度学习框架，便于离线运行。
"""

from __future__ import annotations

import io
from dataclasses import dataclass

from PIL import Image, UnidentifiedImageError

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
    """解码后的图片信息。"""

    bytes_: bytes
    width: int
    height: int
    content_type: str
    quality_status: QualityStatus
    applicability: Applicability


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

    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except (UnidentifiedImageError, OSError, ValueError) as ex:
        # UnidentifiedImageError / OSError 均表示无法解码为图片。
        raise ImageDecodeFailedError() from ex

    raw_format = (image.format or "").upper()
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
        bytes_=data,
        width=width,
        height=height,
        content_type=content_type,
        quality_status=quality,
        applicability=applicability,
    )
