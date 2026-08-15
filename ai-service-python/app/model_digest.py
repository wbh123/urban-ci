"""模型权重目录摘要计算。

download / approve / adapter 三处共用同一算法：对目录内全部模型运行文件按相对路径
排序，逐个累加相对路径与文件内容的 SHA-256，得到目录摘要；多目录摘要再组合。
这样启动 STRICT 校验与下载时写入 manifest 的摘要完全一致。

摘要范围只包含模型运行内容，排除下载元数据/缓存/临时文件：目录如 .cache/、
__pycache__/；文件如 *.lock、*.tmp、*.part、.DS_Store、Thumbs.db。
删除 .cache/huggingface 不改变摘要；修改 model.safetensors / config.json 等
真实模型文件必须改变摘要。
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path

IGNORED_DIRS = {".cache", "__pycache__"}
IGNORED_SUFFIXES = {".lock", ".tmp", ".part"}
IGNORED_FILES = {".DS_Store", "Thumbs.db"}


def dir_digest(directory: Path) -> tuple[str, int]:
    """返回 (目录摘要 sha256, 总字节数)。目录必须存在。"""

    directory = Path(directory)
    files: list[tuple[str, Path]] = []
    for root, dirs, names in os.walk(directory):
        # 原地裁剪 os.walk 的 dirs，避免递归进入缓存/字节码目录。
        dirs[:] = [d for d in dirs if d not in IGNORED_DIRS]
        for name in names:
            if name in IGNORED_FILES:
                continue
            if any(name.endswith(suffix) for suffix in IGNORED_SUFFIXES):
                continue
            path = Path(root) / name
            relative = str(path.relative_to(directory))
            files.append((relative, path))
    files.sort(key=lambda item: item[0])

    digest = hashlib.sha256()
    total_size = 0
    for relative, path in files:
        digest.update(relative.encode("utf-8"))
        digest.update(b"\x00")
        with path.open("rb") as handle:
            for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(chunk)
        total_size += path.stat().st_size
    return digest.hexdigest(), total_size


def combine_digests(*parts: str) -> str:
    """按固定顺序组合多个目录摘要。"""

    digest = hashlib.sha256()
    for part in parts:
        digest.update(part.encode("ascii"))
    return digest.hexdigest()
