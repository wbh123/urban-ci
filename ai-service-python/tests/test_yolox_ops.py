import numpy as np
from PIL import Image

from app.yolox_ops import (
    decode_yolox,
    letterbox_rgb,
    multiclass_nms,
    to_normalized_xywh,
)


def test_letterbox_preserves_ratio_and_pads_bottom_for_wide_image():
    image = Image.new("RGB", (100, 50), color=(10, 20, 30))
    tensor, ratio = letterbox_rgb(image, 200, 200, 114)

    assert ratio == 2.0
    assert tensor.shape == (3, 200, 200)
    assert tensor.dtype == np.float32
    assert np.all(tensor[:, 50, 50] == np.array([10, 20, 30], dtype=np.float32))
    assert np.all(tensor[:, 150, 50] == 114.0)


def test_decode_yolox_decodes_stride_grid_and_scores():
    output = np.zeros((1, 8400, 7), dtype=np.float32)
    index = 10 * 80 + 10
    output[0, index, :4] = [0.5, 0.5, np.log(2.0), np.log(2.0)]
    output[0, index, 4:] = [0.9, 0.8, 0.1]

    boxes, scores, class_ids = decode_yolox(output, (640, 640), 2)

    np.testing.assert_allclose(boxes[index], [76.0, 76.0, 92.0, 92.0], atol=1e-4)
    assert abs(float(scores[index]) - 0.72) < 1e-6
    assert int(class_ids[index]) == 0


def test_multiclass_nms_suppresses_same_class_but_keeps_other_class():
    boxes = np.array([
        [10, 10, 50, 50],
        [12, 12, 49, 49],
        [12, 12, 49, 49],
    ], dtype=np.float32)
    scores = np.array([0.95, 0.90, 0.85], dtype=np.float32)
    classes = np.array([0, 0, 1], dtype=np.int64)

    keep = multiclass_nms(boxes, scores, classes, iou_threshold=0.45, max_detections=10)

    assert keep == [0, 2]


def test_to_normalized_xywh_maps_letterboxed_coordinates_back_to_source():
    box = np.array([20.0, 10.0, 120.0, 60.0], dtype=np.float32)

    normalized = to_normalized_xywh(
        box,
        ratio=2.0,
        source_width=100,
        source_height=50,
    )

    assert normalized is not None
    np.testing.assert_allclose(normalized, [0.1, 0.1, 0.5, 0.5], atol=1e-6)


def test_to_normalized_xywh_rejects_non_finite_or_empty_boxes():
    assert to_normalized_xywh(
        np.array([np.nan, 0.0, 1.0, 1.0], dtype=np.float32),
        ratio=1.0,
        source_width=100,
        source_height=100,
    ) is None
    assert to_normalized_xywh(
        np.array([10.0, 10.0, 10.0, 20.0], dtype=np.float32),
        ratio=1.0,
        source_width=100,
        source_height=100,
    ) is None
