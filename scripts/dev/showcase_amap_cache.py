#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import os
import tempfile
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

CACHE_SCHEMA_VERSION = 1
VALID_CACHE_MODES = {"prefer", "only", "refresh", "off"}
SECRET_OR_DERIVED_PARAMS = {"key", "output"}


class CacheMissError(RuntimeError):
    pass


def _normalized_params(params: dict[str, Any]) -> dict[str, Any]:
    return {
        str(key): params[key]
        for key in sorted(params)
        if str(key) not in SECRET_OR_DERIVED_PARAMS
    }


def canonical_request_key(path: str, params: dict[str, Any]) -> str:
    canonical = json.dumps(
        {"path": path, "params": _normalized_params(params)},
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


class AmapRequestCache:
    def __init__(self, path: Path, mode: str = "prefer") -> None:
        normalized_mode = mode.strip().lower()
        if normalized_mode not in VALID_CACHE_MODES:
            raise ValueError(
                f"不支持的高德缓存模式：{mode}；可选 {', '.join(sorted(VALID_CACHE_MODES))}"
            )
        self.path = path
        self.mode = normalized_mode
        self.entries: dict[str, dict[str, Any]] = {}
        self.hits = 0
        self.misses = 0
        self.writes = 0
        self._dirty = False
        if self.mode in {"prefer", "only"}:
            self._load()

    def _load(self) -> None:
        if not self.path.exists():
            return
        try:
            raw = json.loads(self.path.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise RuntimeError(f"无法读取高德缓存文件 {self.path}: {exc}") from exc
        if raw.get("schemaVersion") != CACHE_SCHEMA_VERSION:
            raise RuntimeError(
                f"高德缓存版本不兼容：{raw.get('schemaVersion')}，当前版本 {CACHE_SCHEMA_VERSION}"
            )
        entries = raw.get("entries")
        if not isinstance(entries, dict):
            raise RuntimeError(f"高德缓存文件格式错误：{self.path}")
        self.entries = entries

    def get(self, path: str, params: dict[str, Any]) -> dict[str, Any] | None:
        if self.mode in {"off", "refresh"}:
            self.misses += 1
            return None
        key = canonical_request_key(path, params)
        entry = self.entries.get(key)
        if entry is not None and isinstance(entry.get("payload"), dict):
            self.hits += 1
            return deepcopy(entry["payload"])
        self.misses += 1
        if self.mode == "only":
            normalized = json.dumps(_normalized_params(params), ensure_ascii=False, sort_keys=True)
            raise CacheMissError(f"高德缓存缺失 path={path} params={normalized}")
        return None

    def put(self, path: str, params: dict[str, Any], payload: dict[str, Any]) -> None:
        if self.mode in {"off", "only"}:
            return
        key = canonical_request_key(path, params)
        self.entries[key] = {
            "path": path,
            "params": _normalized_params(params),
            "cachedAt": datetime.now(timezone.utc).isoformat(),
            "payload": deepcopy(payload),
        }
        self.writes += 1
        self._dirty = True

    def flush(self) -> None:
        if not self._dirty or self.mode in {"off", "only"}:
            return
        self.path.parent.mkdir(parents=True, exist_ok=True)
        document = {
            "schemaVersion": CACHE_SCHEMA_VERSION,
            "updatedAt": datetime.now(timezone.utc).isoformat(),
            "entries": self.entries,
        }
        fd, temp_name = tempfile.mkstemp(
            prefix=f".{self.path.name}.",
            suffix=".tmp",
            dir=str(self.path.parent),
            text=True,
        )
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as handle:
                json.dump(document, handle, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                handle.write("\n")
                handle.flush()
                os.fsync(handle.fileno())
            os.replace(temp_name, self.path)
        except Exception:
            try:
                os.unlink(temp_name)
            except OSError:
                pass
            raise
        self._dirty = False
