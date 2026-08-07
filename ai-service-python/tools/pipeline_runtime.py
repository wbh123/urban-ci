"""已批准模型安装、CUDA-only 运行时目录维护与业务联调。"""

from __future__ import annotations

import argparse
import json
import shutil
import time
from pathlib import Path

from .pipeline_common import (
    MANIFEST_FILENAME,
    _content_type,
    _read_json,
    _sha256,
    _update_env_file,
)
from .pipeline_model import _validate_package_with_runtime


RUNTIME_CATALOG_FILENAME = "runtime-catalog.json"


def _command_install(args: argparse.Namespace) -> None:
    package_dir = args.package.expanduser().resolve()
    payload = _read_json(package_dir / MANIFEST_FILENAME)
    if str(payload.get("status")).upper() != "APPROVED":
        raise RuntimeError("只有 APPROVED 模型包可以安装")
    _validate_package_with_runtime(package_dir)

    model_root = args.model_root.expanduser().resolve()
    target = model_root / str(payload["modelId"]) / str(payload["version"])
    if target.exists():
        if not args.replace:
            raise FileExistsError(f"目标模型目录已存在：{target}")
        shutil.rmtree(target)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copytree(package_dir, target)
    _validate_package_with_runtime(target)

    relative_manifest = target.relative_to(model_root) / MANIFEST_FILENAME
    catalog_path = _update_runtime_catalog(model_root, payload, relative_manifest)
    env_file = args.env_file.expanduser().resolve()
    _update_env_file(
        env_file,
        {
            "URBAN_SAFE_AI_MODEL_ROOT": str(model_root),
            "URBAN_SAFE_AI_MODEL_CATALOG_PATH": catalog_path.name,
            "URBAN_SAFE_AI_CUDA_DEVICE_ID": "0",
            "URBAN_SAFE_AI_DEFAULT_MODE": "REAL",
        },
        remove_keys={
            "URBAN_SAFE_AI_REAL_MODEL_STATUS",
            "URBAN_SAFE_AI_REAL_MODEL_MANIFEST_PATH",
            "URBAN_SAFE_AI_ONNX_EXECUTION_PROVIDERS",
            "URBAN_SAFE_AI_ONNX_REQUIRE_GPU",
        },
    )
    print(f"模型已安装：{target}")
    print(f"运行时目录已更新：{catalog_path}")
    print(f"配置已更新：{env_file}")
    print("请使用 CUDA-only 启动脚本重启 FastAPI 和 Spring Boot，然后执行 verify。")


def _update_runtime_catalog(
    model_root: Path,
    manifest_payload: dict,
    relative_manifest: Path,
) -> Path:
    """原子更新显式模型目录，并把本次安装模型设为默认真实模型。"""

    model_root.mkdir(parents=True, exist_ok=True)
    catalog_path = model_root / RUNTIME_CATALOG_FILENAME
    existing_models: list[dict] = []
    if catalog_path.is_file():
        current = _read_json(catalog_path)
        if current.get("schemaVersion") != 1 or current.get("runtime") != "CUDA_ONLY":
            raise RuntimeError("现有运行时目录不是受支持的 CUDA_ONLY schemaVersion=1")
        models = current.get("models")
        if not isinstance(models, list):
            raise RuntimeError("现有运行时目录 models 不是数组")
        existing_models = [item for item in models if isinstance(item, dict)]

    model_id = str(manifest_payload["modelId"])
    version = str(manifest_payload["version"])
    next_entry = {
        "modelId": model_id,
        "version": version,
        "manifestPath": relative_manifest.as_posix(),
        "enabled": True,
    }
    next_models = [
        item for item in existing_models if str(item.get("modelId")) != model_id
    ]
    next_models.append(next_entry)
    next_models.sort(key=lambda item: (str(item.get("modelId")), str(item.get("version"))))

    body = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": model_id,
        "models": next_models,
    }
    temporary = catalog_path.with_suffix(".json.tmp")
    temporary.write_text(
        json.dumps(body, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    temporary.replace(catalog_path)
    return catalog_path


def _command_verify(args: argparse.Namespace) -> None:
    try:
        import httpx
    except ImportError as ex:
        raise RuntimeError("verify 需要安装 httpx") from ex

    base_url = args.ai_base_url.rstrip("/")
    with httpx.Client(timeout=args.timeout_seconds) as client:
        ready_response = client.get(f"{base_url}/internal/api/v1/ai/ready")
        ready_response.raise_for_status()
        readiness = ready_response.json()
        if readiness.get("status") != "READY" or readiness.get("runtime") != "CUDA_ONLY":
            raise RuntimeError(f"CUDA-only 运行时未就绪：{readiness}")

        model_path = "/internal/api/v1/ai/models/current"
        if args.expected_model_id:
            model_path = f"/internal/api/v1/ai/models/{args.expected_model_id}"
        model_response = client.get(f"{base_url}{model_path}")
        model_response.raise_for_status()
        model_info = model_response.json()
        if (
            model_info.get("mode") != "REAL"
            or model_info.get("status") != "APPROVED"
            or model_info.get("ready") is not True
            or model_info.get("executionProvider") != "CUDAExecutionProvider"
        ):
            raise RuntimeError(f"当前模型不是 CUDA 上已批准且就绪的 REAL 模型：{model_info}")
        if args.expected_model_id and model_info.get("modelId") != args.expected_model_id:
            raise RuntimeError(
                f"模型编号不一致：{model_info.get('modelId')} != {args.expected_model_id}"
            )
        print(json.dumps(readiness, ensure_ascii=False, indent=2))
        print(json.dumps(model_info, ensure_ascii=False, indent=2))

        if args.image:
            image_path = args.image.expanduser().resolve()
            request_id = f"local-real-smoke-{int(time.time())}"
            metadata = {
                "requestId": request_id,
                "mode": "REAL",
                "assetId": "LOCAL-SMOKE-ASSET",
                "filename": image_path.name,
                "contentType": _content_type(image_path),
                "sha256": _sha256(image_path),
                "requestedModelId": model_info["modelId"],
            }
            with image_path.open("rb") as image_file:
                response = client.post(
                    f"{base_url}/internal/api/v1/ai/inferences",
                    files={
                        "file": (
                            image_path.name,
                            image_file,
                            metadata["contentType"],
                        )
                    },
                    data={"metadata": json.dumps(metadata)},
                )
            response.raise_for_status()
            body = response.json()
            if (
                body.get("mode") != "REAL"
                or body.get("model", {}).get("modelId") != model_info["modelId"]
            ):
                raise RuntimeError(f"REAL 推理响应身份不一致：{body}")
            print(json.dumps(body, ensure_ascii=False, indent=2))
