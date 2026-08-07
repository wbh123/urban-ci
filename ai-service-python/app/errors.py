"""FastAPI 推理服务异常与稳定错误码。"""

from __future__ import annotations


class InferenceServiceError(Exception):
    def __init__(self, status_code: int, error_code: str, message: str) -> None:
        super().__init__(message)
        self.status_code = status_code
        self.error_code = error_code
        self.message = message


class ImageEmptyError(InferenceServiceError):
    def __init__(self) -> None:
        super().__init__(400, "AI_IMAGE_EMPTY", "图片为空")


class ImageTooLargeError(InferenceServiceError):
    def __init__(self) -> None:
        super().__init__(413, "AI_IMAGE_TOO_LARGE", "图片超过大小限制")


class ImageUnsupportedFormatError(InferenceServiceError):
    def __init__(self) -> None:
        super().__init__(415, "AI_IMAGE_UNSUPPORTED_FORMAT", "不支持的图片格式")


class ImageDecodeFailedError(InferenceServiceError):
    def __init__(self) -> None:
        super().__init__(422, "AI_IMAGE_DECODE_FAILED", "图片解码失败或已损坏")


class ModelUnavailableError(InferenceServiceError):
    def __init__(self, message: str = "模型不可用") -> None:
        super().__init__(503, "AI_MODEL_UNAVAILABLE", message)
