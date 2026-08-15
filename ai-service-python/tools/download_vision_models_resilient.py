"""中国大陆网络韧性视觉模型下载入口。

复用 ``tools.download_vision_models`` 的模型清单、固定 revision、候选目录和准入流程，
但收紧下载成功判定：无论 ModelScope、huggingface_hub 还是 direct，只有运行文件齐全且
``model.safetensors`` 通过固定 SHA-256 后才算该来源成功。

direct 下载还会在每个 endpoint 下载完成后立即校验；镜像 HTTP 200 但内容错误时删除
错误文件并自动尝试下一个 endpoint，避免残留目录或错误镜像造成“假成功”。
"""

from __future__ import annotations

import json
from pathlib import Path

from tools import download_vision_models as base


def _validate_runtime_file(path: Path, filename: str) -> None:
    """拦截常见的镜像错误页/截断配置文件。权重身份由固定 SHA 单独校验。"""

    if not path.is_file() or path.stat().st_size <= 0:
        raise RuntimeError(f"下载文件为空：{filename}")

    if filename.endswith(".json"):
        try:
            json.loads(path.read_text(encoding="utf-8"))
        except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
            raise RuntimeError(f"下载的 JSON 文件无效：{filename}") from ex
    elif filename == "vocab.txt":
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError) as ex:
            raise RuntimeError("下载的 vocab.txt 不是合法 UTF-8 文本") from ex
        if "<html" in text[:512].lower() or len(text.splitlines()) < 100:
            raise RuntimeError("下载的 vocab.txt 疑似镜像错误页或内容不完整")


def _validate_package(model_id: str, revision: str, destination: Path) -> None:
    """一个来源只有在运行文件齐全且固定权重 SHA 正确时才允许返回成功。"""

    files = base.DIRECT_FILE_SETS.get(model_id)
    if not files:
        raise RuntimeError(f"模型 {model_id} 未登记运行文件集合")

    missing: list[str] = []
    for filename in files:
        path = destination / filename
        if not path.is_file() or path.stat().st_size <= 0:
            missing.append(filename)
            continue
        _validate_runtime_file(path, filename)
    if missing:
        raise RuntimeError(f"运行文件不完整，缺少：{', '.join(missing)}")

    expected = base.PINNED_WEIGHT_SHA256.get((model_id, revision))
    if expected is not None:
        weight = destination / "model.safetensors"
        actual = base._file_sha256(weight)
        if actual != expected:
            raise RuntimeError(
                "固定 revision 权重 SHA 不符："
                f"expected={expected} actual={actual} size={weight.stat().st_size}"
            )


def _download_direct(model_id: str, revision: str, destination: Path) -> None:
    files = base.DIRECT_FILE_SETS.get(model_id)
    if not files:
        raise RuntimeError(f"模型 {model_id} 未登记直接下载文件集合")
    destination.mkdir(parents=True, exist_ok=True)

    expected_weight_sha = base.PINNED_WEIGHT_SHA256.get((model_id, revision))
    for index, filename in enumerate(files, start=1):
        target = destination / filename

        # 仅复用已经通过本地完整性检查的文件。
        if target.is_file() and target.stat().st_size > 0:
            try:
                _validate_runtime_file(target, filename)
                if filename == "model.safetensors" and expected_weight_sha is not None:
                    actual = base._file_sha256(target)
                    if actual != expected_weight_sha:
                        raise RuntimeError(
                            f"已有权重 SHA 不符 expected={expected_weight_sha} actual={actual}"
                        )
                print(f"    [{index}/{len(files)}] 已校验并复用：{filename}")
                continue
            except Exception as ex:
                print(f"    [{index}/{len(files)}] 已有文件不可复用：{filename}（{ex}）")
                target.unlink(missing_ok=True)

        last_error: Exception | None = None
        succeeded = False
        for endpoint in base._direct_endpoints():
            url = f"{endpoint}/{model_id}/resolve/{revision}/{filename}?download=true"
            try:
                print(f"    [{index}/{len(files)}] direct {endpoint}：{filename}")
                base._curl_download(url, target)
                _validate_runtime_file(target, filename)

                if filename == "model.safetensors" and expected_weight_sha is not None:
                    actual = base._file_sha256(target)
                    if actual != expected_weight_sha:
                        size = target.stat().st_size
                        raise RuntimeError(
                            "权重 SHA 不符，继续尝试下一个源："
                            f"expected={expected_weight_sha} actual={actual} size={size}"
                        )
                    print(f"      权重 SHA PASS：{actual[:12]}…")

                succeeded = True
                last_error = None
                break
            except Exception as ex:
                last_error = ex
                print(f"      失败：{ex}")
                target.unlink(missing_ok=True)
                target.with_name(target.name + ".part").unlink(missing_ok=True)

        if not succeeded:
            raise RuntimeError(f"直接下载文件失败：{filename}：{last_error}") from last_error

    _validate_package(model_id, revision, destination)


def _download(model_id: str, revision: str, destination: Path, force_source: str | None) -> str:
    """对每个来源执行完整包校验，防止旧残留文件让空下载被误判为成功。"""

    source_order = ["modelscope", "huggingface", "direct"] if force_source is None else [force_source]
    last_error: Exception | None = None
    for source in source_order:
        destination.mkdir(parents=True, exist_ok=True)
        try:
            if source == "modelscope":
                base._download_model_scope(model_id, revision, destination)
            elif source == "huggingface":
                base._download_huggingface(model_id, revision, destination)
            elif source == "direct":
                _download_direct(model_id, revision, destination)
            else:
                raise RuntimeError(f"不支持的下载来源：{source}")

            _validate_package(model_id, revision, destination)
            print(f"  [{source}] 运行文件与固定 SHA 校验通过")
            return source
        except Exception as ex:
            last_error = ex
            print(f"  [{source}] {model_id}@{revision[:12]}… 未通过完整包校验：{ex}")

    raise RuntimeError(f"模型 {model_id} 下载失败：{last_error}") from last_error


def main() -> int:
    # 替换 base.main() 运行时使用的两个模块级函数，其余 Manifest、identityVerified、
    # CANDIDATE catalog 生成逻辑全部复用原实现。
    base._download_direct = _download_direct
    base._download = _download
    return base.main()


if __name__ == "__main__":
    raise SystemExit(main())
