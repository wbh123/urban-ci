"""已解包数据集目录结构探测测试。"""

from __future__ import annotations

import json

from tools.dataset_inspector import inspect_dataset_directory, write_dataset_inspection


def test_inspect_dataset_directory_counts_images_and_mask_candidates(tmp_path):
    """探测器应统计图片文件，并根据目录名识别掩膜候选。"""

    images_dir = tmp_path / "Dataset" / "images"
    masks_dir = tmp_path / "Dataset" / "masks"
    docs_dir = tmp_path / "Dataset" / "docs"
    images_dir.mkdir(parents=True)
    masks_dir.mkdir(parents=True)
    docs_dir.mkdir(parents=True)
    (images_dir / "crack_001.jpg").write_bytes(b"image")
    (images_dir / "crack_002.png").write_bytes(b"image")
    (masks_dir / "crack_001.png").write_bytes(b"mask")
    (masks_dir / "crack_002.png").write_bytes(b"mask")
    (docs_dir / "readme.txt").write_text("notes", encoding="utf-8")

    result = inspect_dataset_directory(tmp_path)

    assert result.root == tmp_path.resolve()
    assert result.image_file_count == 4
    assert result.mask_candidate_count == 2
    assert result.non_image_file_count == 1
    assert result.directory_count == 4
    assert "Dataset/images" in result.image_directories
    assert "Dataset/masks" in result.mask_candidate_directories


def test_inspect_dataset_directory_treats_bw_directory_as_mask_candidate(tmp_path):
    """黑白掩膜目录 BW 应被识别为掩膜候选，兼容 Mendeley 裂缝分割数据。"""

    rgb_dir = tmp_path / "rgb"
    bw_dir = tmp_path / "BW"
    rgb_dir.mkdir()
    bw_dir.mkdir()
    (rgb_dir / "001.jpg").write_bytes(b"image")
    (bw_dir / "001.png").write_bytes(b"mask")

    result = inspect_dataset_directory(tmp_path)

    assert result.image_file_count == 2
    assert result.mask_candidate_count == 1
    assert result.mask_candidate_directories == ["BW"]


def test_write_dataset_inspection_outputs_json_report(tmp_path):
    """探测报告应写成稳定 JSON，供后续数据划分脚本读取。"""

    images_dir = tmp_path / "images"
    images_dir.mkdir()
    (images_dir / "a.jpg").write_bytes(b"image")
    output_path = tmp_path / "inspection.json"

    write_dataset_inspection(tmp_path, output_path)

    payload = json.loads(output_path.read_text(encoding="utf-8"))
    assert payload["schemaVersion"] == 1
    assert payload["imageFileCount"] == 1
    assert payload["root"] == str(tmp_path.resolve())
