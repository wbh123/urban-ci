"""UrbanSafe 本地真实模型命令行入口。"""
from __future__ import annotations

import argparse
from pathlib import Path

from .dataset_inspector import add_inspect_dataset_parser, command_inspect_dataset
from .pipeline_common import (
    DEFAULT_HF_REPO,
    DEFAULT_HF_REVISION,
    MANIFEST_FILENAME,
    MODEL_FILENAME,
    _float_range,
    _sha256,
    _update_env_file,
)
from .pipeline_evaluation import _calculate_metrics, _command_evaluate, _command_promote
from .pipeline_model import (
    _command_download_hf,
    _command_download_kaggle,
    _command_export_hf,
    _command_package,
    _read_hf_source_metadata,
    _write_candidate_manifest,
)
from .pipeline_runtime import _command_install, _command_verify
from .resource_registry import add_register_resource_parser, command_register_resource


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="UrbanSafe 本地真实模型流水线")
    subparsers = parser.add_subparsers(dest="command", required=True)

    download_hf = subparsers.add_parser(
        "download-hf", help="从 Hugging Face 固定提交下载开放 U-Net 权重"
    )
    download_hf.add_argument("--repo-id", default=DEFAULT_HF_REPO)
    download_hf.add_argument("--revision", default=DEFAULT_HF_REVISION)
    download_hf.add_argument("--output", type=Path, required=True)
    download_hf.add_argument("--token")

    export_hf = subparsers.add_parser(
        "export-hf", help="把指定 Hugging Face U-Net 权重转换为项目 ONNX 模型包"
    )
    export_hf.add_argument("--source-dir", type=Path, required=True)
    export_hf.add_argument("--output", type=Path, required=True)
    export_hf.add_argument("--model-id", default="AI-CRACK-HF-UNET-001")
    export_hf.add_argument("--model-name", default="Concrete Crack U-Net")
    export_hf.add_argument("--version", default="1.0.0")
    export_hf.add_argument("--input-size", type=int, default=256)
    export_hf.add_argument("--opset", type=int, default=17)
    export_hf.add_argument(
        "--output-activation", choices=["LOGITS", "PROBABILITY"], default="LOGITS"
    )
    export_hf.add_argument(
        "--foreground-polarity",
        choices=["HIGH_PROBABILITY", "LOW_PROBABILITY"],
        default="LOW_PROBABILITY",
        help="官方模型背景输出较高，默认用低概率区域表示裂缝",
    )
    export_hf.add_argument(
        "--interpolation",
        choices=["BILINEAR", "BICUBIC", "LANCZOS"],
        default="LANCZOS",
        help="官方 inference.py 使用 LANCZOS",
    )

    download_kaggle = subparsers.add_parser(
        "download-kaggle", help="使用官方 Kaggle CLI 下载开放数据集，仅作为候选数据来源"
    )
    download_kaggle.add_argument("--dataset", required=True)
    download_kaggle.add_argument("--output", type=Path, required=True)
    download_kaggle.add_argument("--unzip", action="store_true")
    download_kaggle.add_argument("--license", dest="license_name", required=True)

    package = subparsers.add_parser(
        "package", help="把自主训练导出的 ONNX 文件封装为 CANDIDATE 模型包"
    )
    package.add_argument("--onnx", type=Path, required=True)
    package.add_argument("--output", type=Path, required=True)
    package.add_argument("--model-id", required=True)
    package.add_argument("--model-name", required=True)
    package.add_argument("--version", required=True)
    package.add_argument("--source-type", required=True)
    package.add_argument("--source-repository", required=True)
    package.add_argument("--source-revision", required=True)
    package.add_argument("--source-license", required=True)
    package.add_argument("--license", dest="model_license", required=True)
    package.add_argument("--input-size", type=int, default=640)
    package.add_argument("--mask-threshold", type=float, default=0.5)
    package.add_argument("--min-component-pixels", type=int, default=16)
    package.add_argument(
        "--output-activation", choices=["LOGITS", "PROBABILITY"], default="LOGITS"
    )
    package.add_argument(
        "--foreground-polarity",
        choices=["HIGH_PROBABILITY", "LOW_PROBABILITY"],
        default="HIGH_PROBABILITY",
    )
    package.add_argument(
        "--interpolation",
        choices=["BILINEAR", "BICUBIC", "LANCZOS"],
        default="BILINEAR",
    )

    evaluate = subparsers.add_parser("evaluate", help="在独立划分上评估 ONNX 模型")
    evaluate.add_argument("--package", type=Path, required=True)
    evaluate.add_argument("--split", type=Path, required=True)
    evaluate.add_argument("--output", type=Path, required=True)
    evaluate.add_argument(
        "--mask-polarity",
        choices=["auto", "white-crack", "black-crack"],
        default="auto",
    )
    evaluate.add_argument("--threshold", type=float, default=0.5)
    evaluate.add_argument("--search-threshold", action="store_true")
    evaluate.add_argument("--threshold-min", type=float, default=0.01)
    evaluate.add_argument("--threshold-max", type=float, default=0.90)
    evaluate.add_argument("--threshold-step", type=float, default=0.01)

    promote = subparsers.add_parser(
        "promote", help="根据独立测试指标把 CANDIDATE 提升为 APPROVED"
    )
    promote.add_argument("--package", type=Path, required=True)
    promote.add_argument("--evaluation", type=Path, required=True)
    promote.add_argument("--approved-by", required=True)
    promote.add_argument("--approved-at")
    promote.add_argument("--minimum-pixel-f1", type=float, default=0.75)
    promote.add_argument("--minimum-iou", type=float, default=0.60)
    promote.add_argument("--minimum-image-recall", type=float, default=0.90)
    promote.add_argument("--maximum-false-positive-image-rate", type=float, default=0.30)

    install = subparsers.add_parser("install", help="安装已批准模型包并更新项目根目录 .env")
    install.add_argument("--package", type=Path, required=True)
    install.add_argument("--model-root", type=Path, required=True)
    install.add_argument("--env-file", type=Path, required=True)
    install.add_argument("--replace", action="store_true")

    verify = subparsers.add_parser(
        "verify", help="检查 FastAPI 当前模型，并可执行一次 REAL 图片推理"
    )
    verify.add_argument("--ai-base-url", default="http://127.0.0.1:8001")
    verify.add_argument("--image", type=Path)
    verify.add_argument("--expected-model-id")
    verify.add_argument("--timeout-seconds", type=float, default=30.0)
    add_inspect_dataset_parser(subparsers)
    add_register_resource_parser(subparsers)
    return parser


def main() -> None:
    args = build_parser().parse_args()
    handler = globals().get(
        f"_command_{args.command.replace('-', '_')}"
    ) or globals().get(f"command_{args.command.replace('-', '_')}")
    if handler is None:
        raise AssertionError(args.command)
    handler(args)


if __name__ == "__main__":
    main()
