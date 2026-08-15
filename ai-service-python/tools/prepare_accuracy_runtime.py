"""把已验证的 ACCURACY 辅助权重安装为受治理的 CANDIDATE Profile。"""
from __future__ import annotations

import argparse
import json
import os
import shutil
import sys
from pathlib import Path

from app.accuracy_profile import (
    ACCURACY_PIPELINE_VERSION,
    ACCURACY_PROFILE_ID,
    ACCURACY_PROFILE_VERSION,
)
from app.accuracy_runtime import _file_digest
from app.model_digest import dir_digest
from tools.download_accuracy_models import accuracy_model_paths, check_model_dir

PROJECT_ROOT = Path(__file__).resolve().parents[2]


def _resolve_project(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def _hardlink_tree(source: Path, target: Path) -> None:
    if target.exists():
        ok, missing = check_model_dir(target)
        if not ok:
            raise RuntimeError("目标模型目录已存在但不完整：" + ", ".join(missing))
        return
    target.mkdir(parents=True, exist_ok=False)
    try:
        for root, dirs, files in os.walk(source):
            dirs[:] = [item for item in dirs if item not in {".cache", "__pycache__"}]
            current = Path(root)
            relative = current.relative_to(source)
            destination = target / relative
            destination.mkdir(parents=True, exist_ok=True)
            for name in files:
                if name.endswith((".lock", ".tmp", ".part")):
                    continue
                os.link(current / name, destination / name)
    except Exception:
        shutil.rmtree(target, ignore_errors=True)
        raise


def _benchmark_gate(path: Path) -> dict:
    payload = json.loads(path.read_text(encoding="utf-8"))
    summary = payload.get("accuracySummary") or {}
    if int(payload.get("imageCount", 0)) < 26:
        raise RuntimeError("最终 ACCURACY benchmark 图片数不足 26")
    if int(payload.get("qwenMaxSide", 0)) != 1024:
        raise RuntimeError("最终 ACCURACY benchmark 必须使用 qwenMaxSide=1024")
    if int(summary.get("failures", -1)) != 0:
        raise RuntimeError("最终 ACCURACY benchmark 存在失败")
    if int(summary.get("noDetectionImages", -1)) != 0:
        raise RuntimeError("最终 ACCURACY benchmark 存在无检测图片")
    if int(summary.get("nearFullCracks", -1)) != 0:
        raise RuntimeError("最终 ACCURACY benchmark 存在近整幅裂缝")
    class_counts = summary.get("classCounts") or {}
    if int(class_counts.get("SURFACE_DAMAGE", 0)) != 0:
        raise RuntimeError("最终 ACCURACY benchmark 仍包含 SURFACE_DAMAGE 正式候选")
    return payload


def main() -> int:
    parser = argparse.ArgumentParser(description="准备 ACCURACY 正式运行时 CANDIDATE")
    parser.add_argument(
        "--source-model-root",
        default="data/model-cache",
        help="实验阶段已下载且完成 benchmark 的模型缓存根目录",
    )
    parser.add_argument(
        "--model-root",
        default="data/ai-service/models",
        help="FastAPI 正式 CUDA 运行时模型根目录",
    )
    parser.add_argument(
        "--benchmark",
        default="data/model-benchmarks/vision-accuracy-comparison.json",
    )
    args = parser.parse_args()

    source_root = _resolve_project(args.source_model_root)
    model_root = _resolve_project(args.model_root)
    benchmark_source = _resolve_project(args.benchmark)
    if not benchmark_source.is_file():
        print("benchmark 不存在：" + str(benchmark_source), file=sys.stderr)
        return 2
    try:
        _benchmark_gate(benchmark_source)
        sources = accuracy_model_paths(source_root)
        for label, path in (("Qwen3-VL", sources.qwen), ("Florence-2", sources.florence)):
            ok, missing = check_model_dir(path)
            if not ok:
                raise RuntimeError(label + " 权重不完整：" + ", ".join(missing))

        profile_dir = model_root / ACCURACY_PROFILE_ID / ACCURACY_PROFILE_VERSION
        profile_path = profile_dir / "profile.json"
        if profile_path.is_file():
            existing = json.loads(profile_path.read_text(encoding="utf-8"))
            if str(existing.get("status", "")).upper() == "APPROVED":
                raise RuntimeError("已存在 APPROVED Profile，禁止覆盖")
        profile_dir.mkdir(parents=True, exist_ok=True)
        qwen_target = profile_dir / "qwen"
        florence_target = profile_dir / "florence"
        _hardlink_tree(sources.qwen, qwen_target)
        _hardlink_tree(sources.florence, florence_target)
        qwen_sha, _ = dir_digest(qwen_target)
        florence_sha, _ = dir_digest(florence_target)

        benchmark_target = profile_dir / "benchmark.json"
        shutil.copy2(benchmark_source, benchmark_target)
        benchmark_sha = _file_digest(benchmark_target)
        payload = {
            "schemaVersion": 1,
            "profileId": ACCURACY_PROFILE_ID,
            "version": ACCURACY_PROFILE_VERSION,
            "status": "CANDIDATE",
            "pipelineVersion": ACCURACY_PIPELINE_VERSION,
            "baseModel": {"modelId": "AI-VISION-LOCAL-001", "version": "1.1.0"},
            "qwen": {
                "repo": "Qwen/Qwen3-VL-2B-Instruct",
                "license": "Apache-2.0",
                "path": f"{ACCURACY_PROFILE_ID}/{ACCURACY_PROFILE_VERSION}/qwen",
                "sha256": qwen_sha,
            },
            "florence": {
                "repo": "florence-community/Florence-2-large-ft",
                "license": "MIT",
                "path": f"{ACCURACY_PROFILE_ID}/{ACCURACY_PROFILE_VERSION}/florence",
                "sha256": florence_sha,
            },
            "inference": {"qwenMaxSide": 1024, "qwenMaxNewTokens": 128},
            "benchmark": {
                "detailsPath": f"{ACCURACY_PROFILE_ID}/{ACCURACY_PROFILE_VERSION}/benchmark.json",
                "sha256": benchmark_sha,
            },
        }
        profile_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    except Exception as ex:
        print("准备 ACCURACY CANDIDATE 失败：" + str(ex), file=sys.stderr)
        return 2

    print("[PASS] ACCURACY CANDIDATE 已安装到正式模型目录：" + str(profile_path))
    print("源缓存：" + str(source_root))
    print("下一步必须由人工运行 approve_accuracy_runtime.py 完成 APPROVED。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
