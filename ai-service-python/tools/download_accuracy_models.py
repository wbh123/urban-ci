"""下载/检查 ACCURACY 实验所需 Qwen3-VL 与 Florence-2 本地权重。

中国大陆默认策略：
- Qwen3-VL：ModelScope 国内站 -> HF-Mirror -> Hugging Face 官方；
- Florence-2 Large FT 原生转换版：HF-Mirror -> Hugging Face 官方。

所有下载都写入独立 local_dir，支持中断后继续复用已下载文件。
"""
from __future__ import annotations

import argparse
import inspect
import os
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

PROJECT_ROOT = Path(__file__).resolve().parents[2]

QWEN_HF_REPO = "Qwen/Qwen3-VL-2B-Instruct"
FLORENCE_HF_REPO = "florence-community/Florence-2-large-ft"
QWEN_MS_REPO = "Qwen/Qwen3-VL-2B-Instruct"
HF_MIRROR_ENDPOINT = "https://hf-mirror.com"
HF_OFFICIAL_ENDPOINT = "https://huggingface.co"


@dataclass(frozen=True)
class AccuracyModelPaths:
    qwen: Path
    florence: Path


@dataclass(frozen=True)
class DownloadSpec:
    label: str
    hf_repo: str
    modelscope_repo: str | None
    local_dir: Path


def _resolve(value: str | Path) -> Path:
    path = Path(value).expanduser()
    return path.resolve() if path.is_absolute() else (PROJECT_ROOT / path).resolve()


def accuracy_model_paths(model_root: str | Path) -> AccuracyModelPaths:
    root = Path(model_root)
    base = root / "AI-VISION-ACCURACY"
    return AccuracyModelPaths(
        qwen=base / "qwen3-vl-2b-instruct",
        # 与旧 microsoft/Florence-2-large 隔离，避免旧 remote-code 配置被误判为当前模型。
        florence=base / "florence-2-large-ft-native",
    )


def check_model_dir(path: Path) -> tuple[bool, list[str]]:
    missing: list[str] = []
    if not (path / "config.json").is_file():
        missing.append("config.json")
    weights = list(path.glob("*.safetensors")) + list(path.glob("*.bin"))
    if not weights:
        missing.append("model weight (*.safetensors/*.bin)")
    return not missing, missing


def source_order(source: str, allow_fallback: bool = True) -> list[str]:
    """返回通用下载源顺序；具体模型可跳过不支持的源。"""
    source = source.strip().lower()
    if source == "auto":
        return ["modelscope", "hf-mirror", "huggingface"]
    if source not in {"modelscope", "hf-mirror", "huggingface"}:
        raise ValueError(f"不支持的下载源：{source}")
    if not allow_fallback:
        return [source]
    fallbacks = ["modelscope", "hf-mirror", "huggingface"]
    return [source] + [item for item in fallbacks if item != source]


def _retry(action: Callable[[], None], label: str, attempts: int) -> None:
    last_error: Exception | None = None
    for attempt in range(1, max(1, attempts) + 1):
        try:
            action()
            return
        except KeyboardInterrupt:
            raise
        except Exception as ex:
            last_error = ex
            print(
                f"[WARN] {label} 第 {attempt}/{attempts} 次失败：{type(ex).__name__}: {ex}",
                file=sys.stderr,
                flush=True,
            )
            if attempt < attempts:
                delay = min(3 * attempt, 12)
                print(f"[INFO] {delay}s 后重试，可 Ctrl+C 中断；已下载文件不会主动删除。", flush=True)
                time.sleep(delay)
    assert last_error is not None
    raise last_error


def _configure_modelscope(parallel: int) -> None:
    os.environ.setdefault("MODELSCOPE_PREFER_AI_SITE", "false")
    os.environ.setdefault("MODELSCOPE_API_TIMEOUT", "120")
    os.environ.setdefault("MODELSCOPE_API_CONNECT_TIMEOUT", "20")
    os.environ.setdefault("MODELSCOPE_API_MAX_RETRIES", "8")
    os.environ.setdefault("MODELSCOPE_DOWNLOAD_MAX_RETRIES", "8")
    os.environ.setdefault("MODELSCOPE_DOWNLOAD_PARALLELS", str(max(1, parallel)))
    os.environ.setdefault("MODELSCOPE_DOWNLOAD_PARALLEL_WORKERS", str(max(1, parallel)))
    os.environ.setdefault("MODELSCOPE_PARALLEL_DOWNLOAD_THRESHOLD_MB", "64")
    os.environ.setdefault("MODELSCOPE_DOWNLOAD_PARALLEL_THRESHOLD_MB", "64")


def _download_modelscope(repo_id: str, local_dir: Path, attempts: int, parallel: int) -> None:
    _configure_modelscope(parallel)
    try:
        from modelscope import snapshot_download
    except ImportError as ex:
        raise RuntimeError("未安装 modelscope，无法使用国内 ModelScope 下载源") from ex

    kwargs = {"model_id": repo_id, "local_dir": str(local_dir)}
    if "max_workers" in inspect.signature(snapshot_download).parameters:
        kwargs["max_workers"] = max(1, parallel)

    _retry(
        lambda: snapshot_download(**kwargs),
        f"ModelScope {repo_id}",
        attempts,
    )


def _configure_huggingface(endpoint: str, disable_xet: bool) -> None:
    os.environ["HF_ENDPOINT"] = endpoint
    os.environ["HF_HUB_DOWNLOAD_TIMEOUT"] = "120"
    os.environ["HF_HUB_ETAG_TIMEOUT"] = "30"
    os.environ["HF_HUB_VERBOSITY"] = "info"
    os.environ["HF_HUB_DISABLE_PROGRESS_BARS"] = "0"
    os.environ["HF_HUB_DISABLE_XET"] = "1" if disable_xet else "0"
    if not disable_xet:
        os.environ.setdefault("HF_XET_HIGH_PERFORMANCE", "1")


def _download_huggingface(
    repo_id: str,
    local_dir: Path,
    attempts: int,
    endpoint: str,
    disable_xet: bool,
) -> None:
    _configure_huggingface(endpoint, disable_xet)
    try:
        from huggingface_hub import snapshot_download
    except ImportError as ex:
        raise RuntimeError("未安装 huggingface_hub") from ex

    kwargs = {"repo_id": repo_id, "local_dir": str(local_dir)}
    if "endpoint" in inspect.signature(snapshot_download).parameters:
        kwargs["endpoint"] = endpoint

    _retry(
        lambda: snapshot_download(**kwargs),
        f"Hugging Face {endpoint} {repo_id}",
        attempts,
    )


def _download_from_source(spec: DownloadSpec, source: str, attempts: int, parallel: int) -> None:
    spec.local_dir.mkdir(parents=True, exist_ok=True)
    if source == "modelscope":
        if not spec.modelscope_repo:
            raise RuntimeError(f"{spec.label} 当前不使用 ModelScope 源；请使用 hf-mirror 或 huggingface")
        _download_modelscope(spec.modelscope_repo, spec.local_dir, attempts, parallel)
        return
    if source == "hf-mirror":
        _download_huggingface(
            spec.hf_repo,
            spec.local_dir,
            attempts,
            endpoint=HF_MIRROR_ENDPOINT,
            disable_xet=True,
        )
        return
    if source == "huggingface":
        _download_huggingface(
            spec.hf_repo,
            spec.local_dir,
            attempts,
            endpoint=HF_OFFICIAL_ENDPOINT,
            disable_xet=False,
        )
        return
    raise ValueError(f"不支持的下载源：{source}")


def _candidate_sources(spec: DownloadSpec, source: str, allow_fallback: bool) -> list[str]:
    ordered = source_order(source, allow_fallback)
    if spec.modelscope_repo is None:
        ordered = [item for item in ordered if item != "modelscope"]
    if not ordered:
        raise RuntimeError(f"{spec.label} 没有可用下载源；请使用 --source hf-mirror 或 huggingface")
    return ordered


def _download_with_fallback(
    spec: DownloadSpec,
    source: str,
    attempts: int,
    parallel: int,
    allow_fallback: bool,
) -> str:
    existing_ok, _ = check_model_dir(spec.local_dir)
    if existing_ok:
        print(f"[SKIP] {spec.label} 已完整存在：{spec.local_dir}", flush=True)
        return "existing"

    errors: list[str] = []
    for candidate_source in _candidate_sources(spec, source, allow_fallback):
        print(
            f"\n[DOWNLOAD] {spec.label} | source={candidate_source} | -> {spec.local_dir}",
            flush=True,
        )
        try:
            _download_from_source(spec, candidate_source, attempts, parallel)
            ok, missing = check_model_dir(spec.local_dir)
            if not ok:
                raise RuntimeError(f"下载结束但文件不完整：{', '.join(missing)}")
            print(f"[PASS] {spec.label} 下载完成，source={candidate_source}", flush=True)
            return candidate_source
        except KeyboardInterrupt:
            print("\n[STOP] 用户中断下载；保留当前缓存，可稍后继续。", file=sys.stderr)
            raise
        except Exception as ex:
            errors.append(f"{candidate_source}: {type(ex).__name__}: {ex}")
            print(f"[WARN] {spec.label} 使用 {candidate_source} 失败，准备尝试下一下载源。", file=sys.stderr)
    raise RuntimeError(f"{spec.label} 所有下载源均失败：" + " | ".join(errors))


def _specs(paths: AccuracyModelPaths, only: str) -> list[DownloadSpec]:
    items = [
        DownloadSpec("Qwen3-VL", QWEN_HF_REPO, QWEN_MS_REPO, paths.qwen),
        # 原 Microsoft 仓库仍依赖旧 remote code，与当前 Transformers 原生 Florence-2 Processor 不兼容。
        # ACCURACY 改用官方权重的 Transformers 原生转换/下游任务微调版本。
        DownloadSpec("Florence-2 Large FT", FLORENCE_HF_REPO, None, paths.florence),
    ]
    if only == "all":
        return items
    return [item for item in items if item.label.lower().startswith(only)]


def main() -> int:
    parser = argparse.ArgumentParser(description="下载/检查 ACCURACY 本地视觉模型")
    parser.add_argument("--model-root", default="data/model-cache")
    parser.add_argument("--check-only", action="store_true")
    parser.add_argument(
        "--source",
        choices=["auto", "modelscope", "hf-mirror", "huggingface"],
        default="auto",
        help="下载源；auto 对 Qwen 优先 ModelScope，对 Florence 原生版优先 HF-Mirror",
    )
    parser.add_argument(
        "--only",
        choices=["all", "qwen", "florence"],
        default="all",
        help="仅下载指定模型",
    )
    parser.add_argument("--attempts", type=int, default=3, help="每个下载源内部重试次数")
    parser.add_argument("--parallel", type=int, default=4, help="ModelScope 并行下载数")
    parser.add_argument(
        "--no-fallback",
        action="store_true",
        help="指定下载源失败后不自动切换其他源",
    )
    args = parser.parse_args()

    root = _resolve(args.model_root)
    paths = accuracy_model_paths(root)
    selected = _specs(paths, args.only)

    if not args.check_only:
        for spec in selected:
            try:
                _download_with_fallback(
                    spec,
                    source=args.source,
                    attempts=max(1, args.attempts),
                    parallel=max(1, args.parallel),
                    allow_fallback=not args.no_fallback,
                )
            except KeyboardInterrupt:
                return 130
            except Exception as ex:
                print(f"[FAIL] {spec.label}: {ex}", file=sys.stderr)
                return 2

    failed = False
    for spec in selected:
        ok, missing = check_model_dir(spec.local_dir)
        if ok:
            print(f"[PASS] {spec.label}: {spec.local_dir}")
        else:
            failed = True
            print(
                f"[FAIL] {spec.label}: {spec.local_dir} 缺少 {', '.join(missing)}",
                file=sys.stderr,
            )
    return 2 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
