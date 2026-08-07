"""人工智能推理适配器。"""

from .mock import DeterministicMockAdapter, MOCK_WARNINGS
from .onnx_crack_segmentation import OnnxCrackSegmentationAdapter

__all__ = [
    "DeterministicMockAdapter",
    "MOCK_WARNINGS",
    "OnnxCrackSegmentationAdapter",
]
