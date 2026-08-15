#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import os
import sys
from pathlib import Path
from types import ModuleType
from typing import Any

from showcase_amap_cache import AmapRequestCache

HERE = Path(__file__).resolve().parent
BASE_GENERATOR = HERE / "generate-showcase-data.py"
CACHE_MODE = os.environ.get("SHOWCASE_AMAP_CACHE_MODE", "prefer")
CACHE_FILE = Path(
    os.environ.get(
        "SHOWCASE_AMAP_CACHE_FILE",
        str(HERE.parents[1] / "data" / "showcase-cache" / "amap-query-cache-v1.json"),
    )
)


def load_generator() -> ModuleType:
    spec = importlib.util.spec_from_file_location("urban_safe_showcase_generator", BASE_GENERATOR)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"无法加载基础展示数据生成器：{BASE_GENERATOR}")
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def main() -> None:
    generator = load_generator()
    original_request = generator.request
    cache = AmapRequestCache(CACHE_FILE, CACHE_MODE)

    def cached_request(path: str, params: dict[str, Any], retries: int = 3) -> dict[str, Any]:
        cached = cache.get(path, params)
        if cached is not None:
            return cached
        payload = original_request(path, params, retries)
        cache.put(path, params, payload)
        cache.flush()
        return payload

    generator.request = cached_request
    try:
        generator.main()
    finally:
        cache.flush()
        print(
            "高德缓存："
            f"mode={cache.mode} hits={cache.hits} misses={cache.misses} writes={cache.writes} "
            f"file={cache.path}",
            flush=True,
        )


if __name__ == "__main__":
    main()
