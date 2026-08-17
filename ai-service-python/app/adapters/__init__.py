"""人工智能推理适配器。"""

from .grounded_sam2 import GroundedSam2TinyAdapter
from .mock import DeterministicMockAdapter, MOCK_WARNINGS
from .onnx_crack_segmentation import OnnxCrackSegmentationAdapter
from .yolox_building_defect import YoloXBuildingDefectAdapter

__all__ = [
    "DeterministicMockAdapter",
    "GroundedSam2TinyAdapter",
    "MOCK_WARNINGS",
    "OnnxCrackSegmentationAdapter",
    "YoloXBuildingDefectAdapter",
]
