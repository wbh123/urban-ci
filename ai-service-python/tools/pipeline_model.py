"""开放模型下载、转换与候选包创建。"""
from __future__ import annotations

import argparse
import json
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np

from .pipeline_common import (
    DEFAULT_HF_LICENSE,
    MANIFEST_FILENAME,
    MODEL_FILENAME,
    _looks_like_commit,
    _read_json,
    _sha256,
)

CUDA_EXECUTION_PROVIDER = "CUDAExecutionProvider"
CPU_EXECUTION_PROVIDER = "CPUExecutionProvider"
DISABLE_CPU_FALLBACK_KEY = "session.disable_cpu_ep_fallback"
RECORD_EP_GRAPH_ASSIGNMENT_KEY = "session.record_ep_graph_assignment_info"


def _command_download_hf(args: argparse.Namespace) -> None:
    try:
        from huggingface_hub import HfApi, snapshot_download
    except ImportError as ex:
        raise RuntimeError("请先安装 requirements-training.txt 中的 huggingface-hub") from ex
    output = args.output.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    info = HfApi(token=args.token).model_info(
        args.repo_id, revision=args.revision, files_metadata=True
    )
    resolved_revision = info.sha
    if _looks_like_commit(args.revision) and args.revision != resolved_revision:
        raise RuntimeError(
            f"Hugging Face 返回提交 {resolved_revision}，与请求 {args.revision} 不一致"
        )
    license_name = str(getattr(info.card_data, "license", "") or "").strip().lower()
    if license_name != "mit":
        raise RuntimeError(f"候选模型许可证不是预期的 MIT：{license_name or 'missing'}")
    snapshot_path = snapshot_download(
        repo_id=args.repo_id,
        revision=resolved_revision,
        local_dir=output,
        token=args.token,
        allow_patterns=[
            "unet_model_weights.pth",
            "inference.py",
            "config.json",
            "README.md",
            "MODEL_CARD.md",
            "requirements.txt",
        ],
    )
    weights = output / "unet_model_weights.pth"
    if not weights.is_file():
        raise RuntimeError("Hugging Face 快照缺少 unet_model_weights.pth")
    metadata = {
        "schemaVersion": 1,
        "provider": "HUGGING_FACE",
        "repository": args.repo_id,
        "requestedRevision": args.revision,
        "resolvedRevision": resolved_revision,
        "license": DEFAULT_HF_LICENSE,
        "downloadedAt": datetime.now(timezone.utc).isoformat(),
        "weightFile": weights.name,
        "weightSha256": _sha256(weights),
        "snapshotPath": str(Path(snapshot_path).resolve()),
    }
    (output / "source.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(metadata, ensure_ascii=False, indent=2))


def _command_export_hf(args: argparse.Namespace) -> None:
    try:
        import torch
    except ImportError as ex:
        raise RuntimeError("转换 Hugging Face 权重需要安装 PyTorch") from ex
    from modeling import ImprovedUNet, UNetConfig, normalize_state_dict

    source_dir = args.source_dir.expanduser().resolve()
    source_metadata = _read_hf_source_metadata(source_dir)
    weights_path = source_dir / "unet_model_weights.pth"
    expected = str(source_metadata.get("weightSha256") or "")
    if not weights_path.is_file() or _sha256(weights_path) != expected:
        raise RuntimeError("Hugging Face 权重不存在或 SHA-256 与 source.json 不一致")
    model = ImprovedUNet(UNetConfig(depth=4, start_filters=64))
    payload = torch.load(weights_path, map_location="cpu", weights_only=True)
    model.load_state_dict(normalize_state_dict(payload), strict=True)
    model.eval()
    output_dir = args.output.expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    onnx_path = output_dir / MODEL_FILENAME
    dummy = torch.zeros((1, 3, args.input_size, args.input_size), dtype=torch.float32)
    with torch.no_grad():
        torch.onnx.export(
            model,
            dummy,
            onnx_path,
            input_names=["images"],
            output_names=["mask_logits"],
            opset_version=args.opset,
            do_constant_folding=True,
            dynamic_axes=None,
            dynamo=False,
        )
    _verify_onnx_contract(onnx_path, input_size=args.input_size)
    _write_candidate_manifest(
        output_dir=output_dir,
        model_id=args.model_id,
        model_name=args.model_name,
        version=args.version,
        source_type="HUGGING_FACE",
        source_repository=str(source_metadata["repository"]),
        source_revision=str(source_metadata["resolvedRevision"]),
        source_license=str(source_metadata["license"]),
        model_license="MIT",
        input_size=args.input_size,
        mask_threshold=0.5,
        min_component_pixels=max(4, args.input_size // 16),
        output_activation=args.output_activation,
        foreground_polarity=args.foreground_polarity,
        interpolation=args.interpolation,
    )
    shutil.copy2(source_dir / "source.json", output_dir / "source.json")
    print(f"已生成 Hugging Face 候选模型包：{output_dir}")


def _read_hf_source_metadata(source_dir: Path) -> dict[str, str]:
    payload = _read_json(source_dir / "source.json")
    if "repository" in payload and "resolvedRevision" in payload and "weightSha256" in payload:
        return {
            "repository": str(payload["repository"]),
            "resolvedRevision": str(payload["resolvedRevision"]),
            "license": str(payload.get("license") or DEFAULT_HF_LICENSE),
            "weightSha256": str(payload["weightSha256"]),
        }
    if str(payload.get("provider")).upper() == "HUGGING_FACE" and "resourceId" in payload:
        files = payload.get("files")
        if not isinstance(files, list):
            raise RuntimeError("Hugging Face 本地登记清单缺少 files")
        weight_sha256 = ""
        for item in files:
            if isinstance(item, dict) and item.get("path") == "unet_model_weights.pth":
                weight_sha256 = str(item.get("sha256") or "")
                break
        if not weight_sha256:
            raise RuntimeError("Hugging Face 本地登记清单缺少 unet_model_weights.pth 摘要")
        return {
            "repository": str(payload["resourceId"]),
            "resolvedRevision": str(
                payload.get("resolvedRevision") or payload.get("requestedRevision") or ""
            ),
            "license": str(payload.get("license") or DEFAULT_HF_LICENSE),
            "weightSha256": weight_sha256,
        }
    raise RuntimeError("不支持的 Hugging Face 来源清单格式")


def _command_download_kaggle(args: argparse.Namespace) -> None:
    output = args.output.expanduser().resolve()
    output.mkdir(parents=True, exist_ok=True)
    command = [sys.executable, "-m", "kaggle", "datasets", "download", "-d", args.dataset, "-p", str(output)]
    if args.unzip:
        command.append("--unzip")
    subprocess.run(command, check=True)
    metadata = {
        "schemaVersion": 1,
        "provider": "KAGGLE",
        "dataset": args.dataset,
        "declaredLicense": args.license_name,
        "downloadedAt": datetime.now(timezone.utc).isoformat(),
        "warning": "Kaggle 仅作为发现与下载入口；正式训练前必须回溯原始论文、原始数据仓库和许可证。",
    }
    (output / "source.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(metadata, ensure_ascii=False, indent=2))


def _command_package(args: argparse.Namespace) -> None:
    onnx_path = args.onnx.expanduser().resolve()
    if not onnx_path.is_file():
        raise FileNotFoundError(onnx_path)
    _verify_onnx_contract(onnx_path, input_size=args.input_size)
    output_dir = args.output.expanduser().resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    target = output_dir / MODEL_FILENAME
    if onnx_path != target:
        shutil.copy2(onnx_path, target)
    _write_candidate_manifest(
        output_dir=output_dir,
        model_id=args.model_id,
        model_name=args.model_name,
        version=args.version,
        source_type=args.source_type,
        source_repository=args.source_repository,
        source_revision=args.source_revision,
        source_license=args.source_license,
        model_license=args.model_license,
        input_size=args.input_size,
        mask_threshold=args.mask_threshold,
        min_component_pixels=args.min_component_pixels,
        output_activation=args.output_activation,
        foreground_polarity=args.foreground_polarity,
        interpolation=args.interpolation,
    )
    print(f"已生成自主训练候选模型包：{output_dir}")


def _write_candidate_manifest(
    *,
    output_dir: Path,
    model_id: str,
    model_name: str,
    version: str,
    source_type: str,
    source_repository: str,
    source_revision: str,
    source_license: str,
    model_license: str,
    input_size: int,
    mask_threshold: float,
    min_component_pixels: int,
    output_activation: str = "LOGITS",
    foreground_polarity: str = "HIGH_PROBABILITY",
    interpolation: str = "BILINEAR",
) -> Path:
    normalized_activation = output_activation.strip().upper()
    if normalized_activation not in {"LOGITS", "PROBABILITY"}:
        raise ValueError("output_activation 仅支持 LOGITS 或 PROBABILITY")
    normalized_polarity = foreground_polarity.strip().upper()
    if normalized_polarity not in {"HIGH_PROBABILITY", "LOW_PROBABILITY"}:
        raise ValueError(
            "foreground_polarity 仅支持 HIGH_PROBABILITY 或 LOW_PROBABILITY"
        )
    normalized_interpolation = interpolation.strip().upper()
    if normalized_interpolation not in {"BILINEAR", "BICUBIC", "LANCZOS"}:
        raise ValueError("interpolation 仅支持 BILINEAR、BICUBIC 或 LANCZOS")
    onnx_path = output_dir / MODEL_FILENAME
    payload = {
        "schemaVersion": 1,
        "modelId": model_id,
        "modelName": model_name,
        "version": version,
        "status": "CANDIDATE",
        "task": "CRACK_SEGMENTATION",
        "adapter": "onnx-crack-segmentation-v1",
        "weightFile": MODEL_FILENAME,
        "weightSha256": _sha256(onnx_path),
        "source": {
            "type": source_type,
            "repository": source_repository,
            "revision": source_revision,
            "license": source_license,
        },
        "classes": [{"code": "CRACK", "name": "裂缝"}],
        "input": {
            "width": input_size,
            "height": input_size,
            "mean": [0.485, 0.456, 0.406],
            "std": [0.229, 0.224, 0.225],
            "interpolation": normalized_interpolation,
        },
        "outputActivation": normalized_activation,
        "foregroundPolarity": normalized_polarity,
        "thresholds": {
            "mask": mask_threshold,
            "minComponentPixels": min_component_pixels,
        },
        "metrics": {
            "dataset": "NOT_EVALUATED",
            "pixelF1": 0.0,
            "iou": 0.0,
            "imageRecall": 0.0,
        },
        "license": model_license,
        "approvedBy": "",
        "approvedAt": "",
    }
    path = output_dir / MANIFEST_FILENAME
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    return path


def _validate_package_with_runtime(package_dir: Path) -> None:
    project_root = Path(__file__).resolve().parents[1]
    if str(project_root) not in sys.path:
        sys.path.insert(0, str(project_root))
    from app.model_manifest import load_model_manifest

    manifest = load_model_manifest(package_dir / MANIFEST_FILENAME, package_dir)
    _verify_onnx_contract(manifest.weight_path, input_size=manifest.input.width)


def _verify_onnx_contract(path: Path, *, input_size: int) -> None:
    session = _create_onnx_session(path)
    inputs = session.get_inputs()
    outputs = session.get_outputs()
    if [item.name for item in inputs] != ["images"]:
        raise RuntimeError("ONNX 输入必须且只能命名为 images")
    if [item.name for item in outputs] != ["mask_logits"]:
        raise RuntimeError("ONNX 输出必须且只能命名为 mask_logits")
    result = session.run(
        ["mask_logits"],
        {"images": np.zeros((1, 3, input_size, input_size), dtype=np.float32)},
    )[0]
    if result.shape != (1, 1, input_size, input_size):
        raise RuntimeError(f"ONNX 输出形状不符合固定契约：{result.shape}")


def _create_onnx_session(path: Path):
    try:
        import onnxruntime as ort
    except ImportError as ex:
        raise RuntimeError("真实模型准备和运行需要安装 onnxruntime-gpu") from ex
    if CUDA_EXECUTION_PROVIDER not in ort.get_available_providers():
        raise RuntimeError("ONNX Runtime 未提供 CUDAExecutionProvider")
    options = ort.SessionOptions()
    options.add_session_config_entry(DISABLE_CPU_FALLBACK_KEY, "1")
    options.add_session_config_entry(RECORD_EP_GRAPH_ASSIGNMENT_KEY, "1")
    session = ort.InferenceSession(
        str(path.expanduser().resolve()),
        sess_options=options,
        providers=[CUDA_EXECUTION_PROVIDER],
    )
    disable_fallback = getattr(session, "disable_fallback", None)
    if not callable(disable_fallback):
        raise RuntimeError("ONNX Runtime 不支持禁用执行后端回退")
    disable_fallback()
    providers = session.get_providers()
    if not providers or providers[0] != CUDA_EXECUTION_PROVIDER:
        raise RuntimeError("ONNX 模型未运行在 CUDAExecutionProvider")
    _validate_cuda_graph_assignment(session)
    return session


def _validate_cuda_graph_assignment(session) -> None:
    """检查 ORT 实际执行图分配，防止模型准备阶段接受 CPU 子图。"""

    get_assignment_info = getattr(session, "get_provider_graph_assignment_info", None)
    if not callable(get_assignment_info):
        raise RuntimeError("ONNX Runtime 不支持执行图分配校验")
    assignments = list(get_assignment_info())
    if not assignments:
        raise RuntimeError("ONNX Runtime 未返回执行图分配信息")
    for assignment in assignments:
        ep_name = getattr(assignment, "ep_name", None)
        if ep_name == CPU_EXECUTION_PROVIDER:
            raise RuntimeError("ONNX 模型图分配仍包含 CPUExecutionProvider")
        if ep_name != CUDA_EXECUTION_PROVIDER:
            raise RuntimeError(f"ONNX 模型图分配包含非 CUDA 执行提供器：{ep_name}")
