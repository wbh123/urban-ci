"""下载精度优先零样本视觉模型并生成独立 CANDIDATE 包。

比赛精度优先配置：Grounding DINO Base + SAM 2.1 Hiera Base+。

- 业务模型编号保持 AI-VISION-LOCAL-001；内部版本升级到 1.1.0；
- 固定 revision：以 Hugging Face 40 位 commit SHA 为准，禁止 main；
- 中国大陆网络优先顺序：ModelScope → huggingface_hub(hf-mirror) → 固定 revision 直接 HTTP；
- 直接 HTTP 仅下载 Transformers 实际运行所需文件，不重复下载 pytorch_model.bin / SAM2 .pt；
- safetensors 使用官方固定 revision 的已验证 SHA-256 做本地身份校验；
- 下载只生成 CANDIDATE manifest 与 runtime-catalog.candidate.json；
- 不覆盖当前 runtime-catalog.json，因此已批准 1.0.0 Tiny 仍可继续运行和回滚；
- 只有 benchmark + 人工批准后才切换 active runtime catalog。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from app.model_digest import combine_digests, dir_digest

MODEL_ID = "AI-VISION-LOCAL-001"
MODEL_NAME = "UrbanSafe Grounding DINO Base + SAM2.1 Base+ 零样本建筑表观病害"
MODEL_VERSION = "1.1.0"
TASK = "ZERO_SHOT_VISUAL_DEFECT"
# 保留既有 adapter 名称作为运行时协议标识；实现本身通过 manifest 动态加载仓库。
ADAPTER = "grounded-sam2-tiny-v1"
LICENSE = "Apache-2.0"
STATUS = "CANDIDATE"

DETECTOR_REPOSITORY = "IDEA-Research/grounding-dino-base"
SEGMENTER_REPOSITORY = "facebook/sam2.1-hiera-base-plus"
DETECTOR_REVISION = "12bdfa3120f3e7ec7b434d90674b3396eccf88eb"
SEGMENTER_REVISION = "b7320756a13354e7530a63935656d35b2f91a290"

# 固定 revision 下 Transformers 运行时实际需要的文件。
# Grounding DINO 不下载重复的 pytorch_model.bin；SAM2 不下载重复的 .pt 权重。
DIRECT_FILE_SETS: dict[str, tuple[str, ...]] = {
    DETECTOR_REPOSITORY: (
        "config.json",
        "model.safetensors",
        "preprocessor_config.json",
        "special_tokens_map.json",
        "tokenizer.json",
        "tokenizer_config.json",
        "vocab.txt",
    ),
    SEGMENTER_REPOSITORY: (
        "config.json",
        "model.safetensors",
        "preprocessor_config.json",
        "processor_config.json",
        "video_preprocessor_config.json",
    ),
}

# Hugging Face 固定 revision 官方文件页公开的 safetensors SHA-256。
# 网络 API 不可用时仍可离线完成身份校验。
PINNED_WEIGHT_SHA256: dict[tuple[str, str], str] = {
    (DETECTOR_REPOSITORY, DETECTOR_REVISION):
        "5548f844c928c4b6f411fa8cbcc2bfa8dbbba437cb1d513975519f93c2a9ed21",
    (SEGMENTER_REPOSITORY, SEGMENTER_REVISION):
        "2012733a0de5d03efd1bba550a2847c4551be9ef2e0d497c83074df66189f780",
}

CLASSES = [
    {"code": "CRACK", "name": "疑似裂缝", "prompts": ["wall crack", "concrete crack", "surface crack"]},
    {"code": "SPALLING", "name": "疑似剥落", "prompts": ["concrete spalling", "damaged concrete surface"]},
    {"code": "EXPOSED_REBAR", "name": "疑似露筋", "prompts": ["exposed rebar"]},
    {"code": "CORROSION", "name": "疑似锈蚀", "prompts": ["rebar corrosion", "rust on concrete"]},
    {"code": "WATER_STAIN", "name": "疑似水渍", "prompts": ["water stain", "moisture stain"]},
    {"code": "SURFACE_DAMAGE", "name": "疑似表面损伤", "prompts": ["damaged concrete", "wall surface damage"]},
]

INPUT = {"maxLongSide": 1280, "boxThreshold": 0.25, "textThreshold": 0.25, "maxDetections": 10}
PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _download_model_scope(model_id: str, revision: str, destination: Path) -> None:
    from modelscope import snapshot_download

    snapshot_download(model_id=model_id, revision=revision, local_dir=str(destination))


def _download_huggingface(model_id: str, revision: str, destination: Path) -> None:
    from huggingface_hub import snapshot_download

    snapshot_download(repo_id=model_id, revision=revision, local_dir=str(destination))


def _direct_endpoints() -> tuple[str, ...]:
    configured = os.environ.get("HF_ENDPOINT", "https://hf-mirror.com").rstrip("/")
    endpoints: list[str] = []
    for value in (configured, "https://hf-mirror.com", "https://huggingface.co"):
        normalized = value.rstrip("/")
        if normalized and normalized not in endpoints:
            endpoints.append(normalized)
    return tuple(endpoints)


def _curl_download(url: str, destination: Path) -> None:
    curl = shutil.which("curl")
    if not curl:
        raise RuntimeError("直接下载回退需要 curl；请先安装 curl")

    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_name(destination.name + ".part")
    command = [
        curl,
        "--location",
        "--fail",
        "--retry", "5",
        "--retry-delay", "2",
        "--retry-all-errors",
        "--connect-timeout", "15",
        "--continue-at", "-",
        "--output", str(partial),
        url,
    ]
    result = subprocess.run(command, check=False)
    if result.returncode != 0:
        # 某些镜像不支持 Range；清理断点文件后允许调用方切下一个 endpoint。
        partial.unlink(missing_ok=True)
        raise RuntimeError(f"curl 下载失败（exit={result.returncode}）")
    if not partial.is_file() or partial.stat().st_size <= 0:
        partial.unlink(missing_ok=True)
        raise RuntimeError("直接下载得到空文件")
    partial.replace(destination)


def _download_direct(model_id: str, revision: str, destination: Path) -> None:
    files = DIRECT_FILE_SETS.get(model_id)
    if not files:
        raise RuntimeError(f"模型 {model_id} 未登记直接下载文件集合")

    expected_weight_sha = PINNED_WEIGHT_SHA256.get((model_id, revision))
    for index, filename in enumerate(files, start=1):
        target = destination / filename
        if target.is_file() and target.stat().st_size > 0:
            if filename != "model.safetensors" or expected_weight_sha is None:
                print(f"    [{index}/{len(files)}] 已存在，复用：{filename}")
                continue
            if _file_sha256(target) == expected_weight_sha:
                print(f"    [{index}/{len(files)}] 权重 SHA 已匹配，复用：{filename}")
                continue
            print(f"    [{index}/{len(files)}] 权重文件不完整或 SHA 不符，重新下载：{filename}")
            target.unlink()

        last_error: Exception | None = None
        for endpoint in _direct_endpoints():
            url = f"{endpoint}/{model_id}/resolve/{revision}/{filename}?download=true"
            try:
                print(f"    [{index}/{len(files)}] direct {endpoint}：{filename}")
                _curl_download(url, target)
                last_error = None
                break
            except Exception as ex:
                last_error = ex
                print(f"      失败：{ex}")
        if last_error is not None:
            raise RuntimeError(f"直接下载文件失败：{filename}：{last_error}") from last_error

    weight_file = destination / "model.safetensors"
    if expected_weight_sha is not None:
        actual = _file_sha256(weight_file)
        if actual != expected_weight_sha:
            raise RuntimeError(
                f"直接下载权重 SHA 校验失败：{model_id} expected={expected_weight_sha} actual={actual}"
            )


def _download(model_id: str, revision: str, destination: Path, force_source: str | None) -> str:
    source_order = ["modelscope", "huggingface", "direct"] if force_source is None else [force_source]
    last_error: Exception | None = None
    for source in source_order:
        destination.mkdir(parents=True, exist_ok=True)
        try:
            if source == "modelscope":
                _download_model_scope(model_id, revision, destination)
            elif source == "huggingface":
                _download_huggingface(model_id, revision, destination)
            else:
                _download_direct(model_id, revision, destination)
            runtime_files = [path for path in destination.iterdir() if path.name != ".cache"]
            if not runtime_files:
                raise RuntimeError("下载目录为空")
            return source
        except Exception as ex:
            last_error = ex
            print(f"  [{source}] {model_id}@{revision[:12]}… 下载失败：{ex}")
    raise RuntimeError(f"模型 {model_id} 下载失败：{last_error}") from last_error


def _verify_identity(repository: str, revision: str, weight_file: Path) -> bool | None:
    expected_pinned = PINNED_WEIGHT_SHA256.get((repository, revision))
    if expected_pinned is not None:
        if not weight_file.is_file():
            return False
        actual = _file_sha256(weight_file)
        ok = actual == expected_pinned
        print(
            f"  固定 revision 权重 SHA：{'PASS' if ok else 'FAIL'} "
            f"{repository}@{revision[:12]}…"
        )
        return ok

    try:
        from huggingface_hub import HfApi

        info = HfApi().model_info(repository, revision=revision, files_metadata=True)
    except Exception as ex:
        print(f"  身份校验无法获取 HF 期望摘要：{ex}")
        return None
    expected = None
    for sibling in info.siblings:
        if sibling.rfilename == weight_file.name:
            expected = getattr(sibling.lfs, "sha256", None)
    if not expected:
        return None
    return _file_sha256(weight_file) == expected


def main() -> int:
    parser = argparse.ArgumentParser(description="下载精度优先零样本视觉模型并生成独立 CANDIDATE 包")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--detector", default=DETECTOR_REPOSITORY)
    parser.add_argument("--segmenter", default=SEGMENTER_REPOSITORY)
    parser.add_argument("--detector-revision", default=DETECTOR_REVISION)
    parser.add_argument("--segmenter-revision", default=SEGMENTER_REVISION)
    parser.add_argument("--source", default=None, choices=["modelscope", "huggingface", "direct"])
    parser.add_argument("--skip-download", action="store_true")
    args = parser.parse_args()

    model_root = _resolve(args.model_root)
    model_root.mkdir(parents=True, exist_ok=True)
    os.environ.setdefault("HF_ENDPOINT", "https://hf-mirror.com")
    os.environ.setdefault("HF_HUB_DISABLE_XET", "1")
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")

    package_dir = model_root / MODEL_ID / MODEL_VERSION
    detector_dir = package_dir / "detector"
    segmenter_dir = package_dir / "segmenter"

    if args.skip_download:
        if not detector_dir.is_dir() or not segmenter_dir.is_dir():
            print("--skip-download 需要 1.1.0 权重目录已存在。", file=sys.stderr)
            return 2
        detector_source = segmenter_source = "local"
    else:
        print(f"[1/2] 下载检测模型 {args.detector}@{args.detector_revision[:12]}…")
        detector_source = _download(args.detector, args.detector_revision, detector_dir, args.source)
        print(f"[2/2] 下载分割模型 {args.segmenter}@{args.segmenter_revision[:12]}…")
        segmenter_source = _download(args.segmenter, args.segmenter_revision, segmenter_dir, args.source)

    detector_sha256, detector_size = dir_digest(detector_dir)
    segmenter_sha256, segmenter_size = dir_digest(segmenter_dir)
    detector_identity = _verify_identity(args.detector, args.detector_revision, detector_dir / "model.safetensors")
    segmenter_identity = _verify_identity(args.segmenter, args.segmenter_revision, segmenter_dir / "model.safetensors")
    if detector_identity is False or segmenter_identity is False:
        raise RuntimeError("模型身份校验失败：本地权重与固定 revision 官方 SHA 不一致")
    identity_verified = bool(detector_identity and segmenter_identity)

    weight_sha256 = combine_digests(detector_sha256, segmenter_sha256)
    size_bytes = detector_size + segmenter_size
    manifest = {
        "_note": "精度优先候选模型；批准前不会替换当前 active runtime catalog。",
        "schemaVersion": 1,
        "modelId": MODEL_ID,
        "modelName": MODEL_NAME,
        "version": MODEL_VERSION,
        "status": STATUS,
        "identityVerified": identity_verified,
        "task": TASK,
        "adapter": ADAPTER,
        "source": {
            "type": "ZERO_SHOT_OPEN_WEIGHTS",
            "repository": f"detector:{detector_source};segmenter:{segmenter_source}",
            "revision": f"{args.detector_revision}/{args.segmenter_revision}",
            "license": LICENSE,
        },
        "classes": CLASSES,
        "checkpoint": {
            "detectorRepository": args.detector,
            "segmenterRepository": args.segmenter,
            "detectorRevision": args.detector_revision,
            "segmenterRevision": args.segmenter_revision,
            "detectorDir": str(detector_dir.relative_to(model_root)),
            "segmenterDir": str(segmenter_dir.relative_to(model_root)),
            "sha256": weight_sha256,
            "sizeBytes": size_bytes,
        },
        "input": INPUT,
        "license": LICENSE,
        "approvedBy": "",
        "approvedAt": "",
    }
    manifest_path = package_dir / "manifest.json"
    manifest_path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")

    candidate_catalog = {
        "schemaVersion": 1,
        "runtime": "CUDA_ONLY",
        "defaultModelId": MODEL_ID,
        "models": [{
            "modelId": MODEL_ID,
            "version": MODEL_VERSION,
            "manifestPath": f"{MODEL_ID}/{MODEL_VERSION}/manifest.json",
            "enabled": False,
        }],
    }
    candidate_path = model_root / "runtime-catalog.candidate.json"
    candidate_path.write_text(json.dumps(candidate_catalog, ensure_ascii=False, indent=2), encoding="utf-8")

    print("\n候选模型准备完成：")
    print(f"  manifest：{manifest_path}")
    print(f"  candidate catalog：{candidate_path}")
    print("  active runtime-catalog.json：未修改（现有 Tiny 运行时继续可用）")
    print(f"  detector [{detector_source}] sha={detector_sha256[:12]}…")
    print(f"  segmenter [{segmenter_source}] sha={segmenter_sha256[:12]}…")
    print(f"  identityVerified={identity_verified}，总大小={size_bytes / 1024 / 1024:.1f} MiB")
    print("  下一步：对 1.1.0 执行 RTX3060 benchmark，确认精度/显存后再人工批准切换。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
