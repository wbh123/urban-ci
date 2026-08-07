"""模型流水线共享工具。"""
from __future__ import annotations
import hashlib, json, shutil
from datetime import datetime
from pathlib import Path
from typing import Any
import numpy as np

DEFAULT_HF_REPO = "samir-mohamed/concrete-crack-segmentation"
DEFAULT_HF_REVISION = "55b4933b417822f8dda632cca19e391406d0bc7e"
DEFAULT_HF_LICENSE = "MIT"
MODEL_FILENAME = "model.onnx"
MANIFEST_FILENAME = "manifest.json"

def _update_env_file(
    path: Path,
    values: dict[str, str],
    remove_keys: set[str] | None = None,
) -> None:
    """更新根目录 .env，并可移除已经废弃的运行时变量。"""

    if not path.is_file():
        raise FileNotFoundError(f"环境文件不存在：{path}")
    backup = path.with_name(f"{path.name}.backup-{datetime.now().strftime('%Y%m%d-%H%M%S')}")
    shutil.copy2(path, backup)
    pending = dict(values)
    removable = set(remove_keys or set())
    output: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#") or "=" not in line:
            output.append(line)
            continue
        key = line.split("=", maxsplit=1)[0].strip()
        if key in removable:
            continue
        output.append(f"{key}={pending.pop(key)}" if key in pending else line)
    if pending:
        output.extend(["", "# ---------- 本地真实模型 ----------"])
        output.extend(f"{key}={value}" for key, value in pending.items())
    path.write_text("\n".join(output) + "\n", encoding="utf-8")
    print(f"原环境文件备份：{backup}")

def _check_minimum(failures: list[str], name: str, metrics: dict[str, Any], minimum: float) -> None:
    value = float(metrics.get(name, -1.0))
    if value < minimum:
        failures.append(f"{name}={value:.4f} < {minimum:.4f}")

def _sigmoid(logits: np.ndarray) -> np.ndarray:
    clipped = np.clip(logits, -30.0, 30.0)
    return 1.0 / (1.0 + np.exp(-clipped))

def _float_range(start: float, stop: float, step: float) -> list[float]:
    if step <= 0 or start <= 0 or stop >= 1 or start > stop:
        raise ValueError("阈值搜索范围必须位于 0 和 1 之间，且 step 大于 0")
    values: list[float] = []
    current = start
    while current <= stop + 1e-9:
        values.append(round(current, 6))
        current += step
    return values

def _read_json(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as ex:
        raise ValueError(f"无法读取 JSON：{path}") from ex
    if not isinstance(payload, dict):
        raise ValueError(f"JSON 根节点必须是对象：{path}")
    return payload

def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def _looks_like_commit(value: str) -> bool:
    return len(value) == 40 and all(character in "0123456789abcdef" for character in value.lower())

def _content_type(path: Path) -> str:
    return {".jpg":"image/jpeg", ".jpeg":"image/jpeg", ".png":"image/png", ".webp":"image/webp"}.get(path.suffix.lower(), "application/octet-stream")
