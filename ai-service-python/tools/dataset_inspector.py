"""已解包数据集目录结构探测工具。

本工具只做目录结构和文件类型统计，用于决定后续如何编写数据配对、
分组划分和标签统一脚本。它不读取业务图片内容，不生成训练结果。
"""

from __future__ import annotations

import argparse
import json
from dataclasses import asdict, dataclass
from pathlib import Path


# 常见图片扩展名。这里既包含原图，也包含掩膜图片。
IMAGE_EXTENSIONS = {".bmp", ".jpeg", ".jpg", ".png", ".tif", ".tiff", ".webp"}

# 目录名包含这些关键词时，目录中的图片会被视为掩膜候选。
MASK_DIRECTORY_KEYWORDS = (
    "annotation",
    "annotations",
    "bw",
    "label",
    "labels",
    "mask",
    "masks",
    "segmentation",
)


@dataclass(frozen=True)
class DatasetInspectionResult:
    """已解包数据集目录探测结果。"""

    schema_version: int
    root: Path
    directory_count: int
    image_file_count: int
    mask_candidate_count: int
    non_image_file_count: int
    image_directories: list[str]
    mask_candidate_directories: list[str]
    non_image_extensions: list[str]


def inspect_dataset_directory(root: Path) -> DatasetInspectionResult:
    """统计已解包数据集目录中的图片、掩膜候选和非图片文件。"""

    resolved_root = root.expanduser().resolve()
    if not resolved_root.is_dir():
        raise NotADirectoryError(f"数据集根目录不存在：{resolved_root}")

    directory_paths = [path for path in resolved_root.rglob("*") if path.is_dir()]
    image_directories: set[str] = set()
    mask_candidate_directories: set[str] = set()
    non_image_extensions: set[str] = set()
    image_file_count = 0
    mask_candidate_count = 0
    non_image_file_count = 0

    for file_path in sorted(path for path in resolved_root.rglob("*") if path.is_file()):
        suffix = file_path.suffix.lower()
        relative_parent = _relative_parent(resolved_root, file_path)
        if suffix in IMAGE_EXTENSIONS:
            image_file_count += 1
            image_directories.add(relative_parent)
            if _looks_like_mask_directory(file_path.parent):
                mask_candidate_count += 1
                mask_candidate_directories.add(relative_parent)
        else:
            non_image_file_count += 1
            non_image_extensions.add(suffix or "<no-extension>")

    return DatasetInspectionResult(
        schema_version=1,
        root=resolved_root,
        directory_count=len(directory_paths),
        image_file_count=image_file_count,
        mask_candidate_count=mask_candidate_count,
        non_image_file_count=non_image_file_count,
        image_directories=sorted(image_directories),
        mask_candidate_directories=sorted(mask_candidate_directories),
        non_image_extensions=sorted(non_image_extensions),
    )


def write_dataset_inspection(root: Path, output: Path) -> DatasetInspectionResult:
    """探测数据集目录并写出 JSON 报告。"""

    result = inspect_dataset_directory(root)
    output_path = output.expanduser().resolve()
    output_path.parent.mkdir(parents=True, exist_ok=True)
    payload = _to_json_payload(result)
    output_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return result


def _to_json_payload(result: DatasetInspectionResult) -> dict[str, object]:
    """将探测结果转换为稳定 JSON 字段名。"""

    payload = asdict(result)
    return {
        "schemaVersion": payload["schema_version"],
        "root": str(payload["root"]),
        "directoryCount": payload["directory_count"],
        "imageFileCount": payload["image_file_count"],
        "maskCandidateCount": payload["mask_candidate_count"],
        "nonImageFileCount": payload["non_image_file_count"],
        "imageDirectories": payload["image_directories"],
        "maskCandidateDirectories": payload["mask_candidate_directories"],
        "nonImageExtensions": payload["non_image_extensions"],
    }


def _relative_parent(root: Path, file_path: Path) -> str:
    """返回文件父目录相对数据集根目录的 POSIX 路径。"""

    parent = file_path.parent
    if parent == root:
        return "."
    return parent.relative_to(root).as_posix()


def _looks_like_mask_directory(directory: Path) -> bool:
    """根据目录路径关键词判断图片是否可能是像素掩膜。"""

    normalized_parts = {part.lower() for part in directory.parts}
    return any(keyword in normalized_parts for keyword in MASK_DIRECTORY_KEYWORDS)


def add_inspect_dataset_parser(subparsers: argparse._SubParsersAction[argparse.ArgumentParser]) -> None:
    """向模型流水线 CLI 注册 `inspect-dataset` 子命令。"""

    parser = subparsers.add_parser("inspect-dataset", help="探测已解包数据集目录结构")
    parser.add_argument("--root", type=Path, required=True, help="已解包数据集根目录")
    parser.add_argument("--output", type=Path, required=True, help="JSON 探测报告输出路径")


def command_inspect_dataset(args: argparse.Namespace) -> None:
    """执行 `inspect-dataset` CLI 子命令。"""

    result = write_dataset_inspection(args.root, args.output)
    print(json.dumps(_to_json_payload(result), ensure_ascii=False, indent=2))
