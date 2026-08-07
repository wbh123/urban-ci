"""统一推理适配器协议。"""

from __future__ import annotations

from typing import Protocol

from ..image import DecodedImage
from ..schemas import Applicability, DetectionItem, ModelBrief


class InferenceAdapter(Protocol):
    """模型注册表只依赖该协议，不依赖具体深度学习框架。"""

    def model_info(self) -> ModelBrief: ...

    def execution_provider(self) -> str | None:
        """返回实际执行后端；真实模型必须是 CUDAExecutionProvider。"""
        ...

    def predict(
        self, image: DecodedImage
    ) -> tuple[Applicability, list[DetectionItem]]:
        """对解码后的图片执行推理并返回标准化结果。"""
        ...
