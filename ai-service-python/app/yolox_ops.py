"""YOLOX ONNX 预处理、head 解码、NMS 与坐标标准化纯函数。"""

from __future__ import annotations

import cv2
import numpy as np
from PIL import Image


YOLOX_STRIDES = (8, 16, 32)


def letterbox_rgb(
    image: Image.Image,
    width: int,
    height: int,
    pad_value: int = 114,
) -> tuple[np.ndarray, float]:
    """按官方 YOLOX top-left letterbox 生成 BGR/CHW/float32 输入张量。"""

    if width <= 0 or height <= 0:
        raise ValueError("YOLOX 输入尺寸必须大于 0")
    if not 0 <= pad_value <= 255:
        raise ValueError("YOLOX padValue 必须位于 0~255")

    rgb = np.asarray(image.convert("RGB"), dtype=np.uint8)
    source_height, source_width = rgb.shape[:2]
    if source_width <= 0 or source_height <= 0:
        raise ValueError("YOLOX 输入图片尺寸非法")

    ratio = min(height / source_height, width / source_width)
    resized_width = max(1, int(source_width * ratio))
    resized_height = max(1, int(source_height * ratio))
    resized_rgb = cv2.resize(
        rgb,
        (resized_width, resized_height),
        interpolation=cv2.INTER_LINEAR,
    )
    canvas_rgb = np.full((height, width, 3), pad_value, dtype=np.uint8)
    canvas_rgb[:resized_height, :resized_width] = resized_rgb

    canvas_bgr = canvas_rgb[:, :, ::-1]
    tensor = np.ascontiguousarray(canvas_bgr.transpose(2, 0, 1), dtype=np.float32)
    return tensor, float(ratio)


def decode_yolox(
    output: np.ndarray,
    input_size: tuple[int, int],
    class_count: int,
) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """等价实现官方 YOLOX ``demo_postprocess`` 并返回 xyxy/score/classId。"""

    if class_count <= 0:
        raise ValueError("YOLOX class_count 必须大于 0")
    height, width = input_size
    if height <= 0 or width <= 0:
        raise ValueError("YOLOX input_size 非法")

    values = np.asarray(output, dtype=np.float32)
    if values.ndim != 3 or values.shape[0] != 1:
        raise ValueError("YOLOX 输出必须为 [1,N,5+C]")
    if values.shape[2] != 5 + class_count:
        raise ValueError("YOLOX 输出类别维度与 manifest 不一致")

    grids: list[np.ndarray] = []
    expanded_strides: list[np.ndarray] = []
    for stride in YOLOX_STRIDES:
        hsize = height // stride
        wsize = width // stride
        if hsize <= 0 or wsize <= 0:
            raise ValueError("YOLOX 输入尺寸小于模型 stride")
        xv, yv = np.meshgrid(np.arange(wsize), np.arange(hsize))
        grid = np.stack((xv, yv), axis=2).reshape(1, -1, 2).astype(np.float32)
        grids.append(grid)
        expanded_strides.append(
            np.full((1, grid.shape[1], 1), stride, dtype=np.float32)
        )

    grid = np.concatenate(grids, axis=1)
    strides = np.concatenate(expanded_strides, axis=1)
    if values.shape[1] != grid.shape[1]:
        raise ValueError("YOLOX 输出候选数量与 8/16/32 stride 网格不一致")

    decoded = values.copy()
    decoded[..., :2] = (decoded[..., :2] + grid) * strides
    decoded[..., 2:4] = np.exp(decoded[..., 2:4]) * strides
    predictions = decoded[0]

    cxcywh = predictions[:, :4]
    boxes = np.empty_like(cxcywh, dtype=np.float32)
    boxes[:, 0] = cxcywh[:, 0] - cxcywh[:, 2] / 2.0
    boxes[:, 1] = cxcywh[:, 1] - cxcywh[:, 3] / 2.0
    boxes[:, 2] = cxcywh[:, 0] + cxcywh[:, 2] / 2.0
    boxes[:, 3] = cxcywh[:, 1] + cxcywh[:, 3] / 2.0

    class_scores = predictions[:, 5:]
    class_ids = class_scores.argmax(axis=1).astype(np.int64)
    scores = predictions[:, 4] * class_scores[np.arange(len(class_ids)), class_ids]
    return boxes, scores.astype(np.float32), class_ids


def multiclass_nms(
    boxes: np.ndarray,
    scores: np.ndarray,
    class_ids: np.ndarray,
    iou_threshold: float,
    max_detections: int,
) -> list[int]:
    """按类别执行 NMS，最终按分数降序返回原始索引。"""

    if not 0.0 <= iou_threshold <= 1.0:
        raise ValueError("NMS IoU 阈值必须位于 0~1")
    if max_detections <= 0:
        raise ValueError("maximumDetections 必须大于 0")

    boxes = np.asarray(boxes, dtype=np.float32)
    scores = np.asarray(scores, dtype=np.float32)
    class_ids = np.asarray(class_ids, dtype=np.int64)
    if boxes.ndim != 2 or boxes.shape[1] != 4:
        raise ValueError("boxes 必须为 [N,4]")
    if len(boxes) != len(scores) or len(boxes) != len(class_ids):
        raise ValueError("boxes/scores/class_ids 长度不一致")

    kept: list[int] = []
    for class_id in np.unique(class_ids):
        candidates = np.flatnonzero(class_ids == class_id)
        candidates = candidates[np.argsort(scores[candidates])[::-1]]
        while candidates.size > 0:
            current = int(candidates[0])
            kept.append(current)
            if candidates.size == 1:
                break
            rest = candidates[1:]
            ious = _iou_one_to_many(boxes[current], boxes[rest])
            candidates = rest[ious <= iou_threshold]

    kept.sort(key=lambda index: float(scores[index]), reverse=True)
    return kept[:max_detections]


def _iou_one_to_many(box: np.ndarray, boxes: np.ndarray) -> np.ndarray:
    x1 = np.maximum(box[0], boxes[:, 0])
    y1 = np.maximum(box[1], boxes[:, 1])
    x2 = np.minimum(box[2], boxes[:, 2])
    y2 = np.minimum(box[3], boxes[:, 3])
    intersection = np.maximum(0.0, x2 - x1) * np.maximum(0.0, y2 - y1)
    area_a = max(0.0, float(box[2] - box[0])) * max(0.0, float(box[3] - box[1]))
    area_b = np.maximum(0.0, boxes[:, 2] - boxes[:, 0]) * np.maximum(
        0.0, boxes[:, 3] - boxes[:, 1]
    )
    union = area_a + area_b - intersection
    return np.divide(
        intersection,
        union,
        out=np.zeros_like(intersection, dtype=np.float32),
        where=union > 0,
    )


def to_normalized_xywh(
    box_xyxy: np.ndarray,
    *,
    ratio: float,
    source_width: int,
    source_height: int,
) -> tuple[float, float, float, float] | None:
    """将 letterbox 坐标回映射原图并转换成 0~1 NORMALIZED_XYWH。"""

    values = np.asarray(box_xyxy, dtype=np.float64).reshape(-1)
    if values.size != 4 or not np.isfinite(values).all():
        return None
    if not np.isfinite(ratio) or ratio <= 0 or source_width <= 0 or source_height <= 0:
        return None

    x1, y1, x2, y2 = values / ratio
    x1 = float(np.clip(x1, 0.0, source_width))
    x2 = float(np.clip(x2, 0.0, source_width))
    y1 = float(np.clip(y1, 0.0, source_height))
    y2 = float(np.clip(y2, 0.0, source_height))
    if x2 <= x1 or y2 <= y1:
        return None

    return (
        x1 / source_width,
        y1 / source_height,
        (x2 - x1) / source_width,
        (y2 - y1) / source_height,
    )
