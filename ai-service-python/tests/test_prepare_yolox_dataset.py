from __future__ import annotations

import json
from pathlib import Path

import numpy as np
import pytest
from PIL import Image

from tools.prepare_yolox_dataset import BUILDING_DEFECT_CLASSES, prepare_crack_mask_dataset


def _write_rgb(path: Path, size=(64, 48)) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.new("RGB", size, (120, 120, 120)).save(path)


def _write_mask(path: Path, box=(10, 8, 30, 20), size=(64, 48)) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    data = np.zeros((size[1], size[0]), dtype=np.uint8)
    x1, y1, x2, y2 = box
    data[y1:y2, x1:x2] = 255
    Image.fromarray(data).save(path)


def test_prepare_crack_mask_dataset_creates_seven_class_coco_and_bbox(tmp_path: Path):
    images = tmp_path / "images"
    masks = tmp_path / "masks"
    for index in range(6):
        _write_rgb(images / f"sample-{index}.jpg")
        _write_mask(masks / f"sample-{index}.png")

    output = tmp_path / "coco"
    summary = prepare_crack_mask_dataset(
        images_dir=images,
        masks_dir=masks,
        output_dir=output,
        seed=7,
        train_ratio=0.5,
        val_ratio=0.25,
        min_component_pixels=8,
        link_mode="copy",
    )

    assert summary["positiveImages"] == 6
    assert summary["classCodes"] == [item[0] for item in BUILDING_DEFECT_CLASSES]
    payload = json.loads((output / "annotations" / "instances_train2017.json").read_text(encoding="utf-8"))
    assert [item["name"] for item in payload["categories"]] == [item[1] for item in BUILDING_DEFECT_CLASSES]
    assert all(item["category_id"] == 1 for item in payload["annotations"])
    assert all(len(item["bbox"]) == 4 and item["bbox"][2] > 0 and item["bbox"][3] > 0 for item in payload["annotations"])


def test_prepare_crack_mask_dataset_supports_negative_images(tmp_path: Path):
    images = tmp_path / "images"
    masks = tmp_path / "masks"
    negatives = tmp_path / "negatives"
    for index in range(4):
        _write_rgb(images / f"p-{index}.jpg")
        _write_mask(masks / f"p-{index}.png")
    for index in range(2):
        _write_rgb(negatives / f"n-{index}.jpg")

    summary = prepare_crack_mask_dataset(
        images_dir=images,
        masks_dir=masks,
        negative_images_dir=negatives,
        output_dir=tmp_path / "out",
        seed=3,
        link_mode="copy",
    )

    assert summary["negativeImages"] == 2
    assert summary["totalImages"] == 6


def test_prepare_crack_mask_dataset_rejects_unpaired_images(tmp_path: Path):
    images = tmp_path / "images"
    masks = tmp_path / "masks"
    _write_rgb(images / "orphan.jpg")
    masks.mkdir(parents=True)

    with pytest.raises(ValueError, match="掩膜"):
        prepare_crack_mask_dataset(images_dir=images, masks_dir=masks, output_dir=tmp_path / "out")
