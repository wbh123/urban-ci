#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import os
import sys
import time
from pathlib import Path
from types import ModuleType
from typing import Any

from showcase_amap_budget import AmapNetworkBudget
from showcase_amap_cache import AmapRequestCache
from showcase_wuhan_search import search_community_candidates as search_wuhan_community_candidates

HERE = Path(__file__).resolve().parent
BASE_GENERATOR = HERE / "generate-showcase-data.py"
CACHE_MODE = os.environ.get("SHOWCASE_AMAP_CACHE_MODE", "prefer")
CACHE_FILE = Path(
    os.environ.get(
        "SHOWCASE_AMAP_CACHE_FILE",
        str(HERE.parents[1] / "data" / "showcase-cache" / "amap-query-cache-v1.json"),
    )
)
PUBLIC_CATALOG_FILE = Path(
    os.environ.get(
        "SHOWCASE_WUHAN_PUBLIC_CATALOG_FILE",
        str(HERE.parents[1] / "data" / "showcase-sources" / "wuhan-old-community-catalog-v1.json"),
    )
)
NETWORK_BUDGET = int(os.environ.get("SHOWCASE_AMAP_MAX_NETWORK_REQUESTS", "0"))
CACHE_FLUSH_EVERY = max(1, int(os.environ.get("SHOWCASE_AMAP_CACHE_FLUSH_EVERY", "1")))
MAX_HTTP_ATTEMPTS = max(1, int(os.environ.get("SHOWCASE_AMAP_HTTP_ATTEMPTS", "2")))
WUHAN_RESERVE_PER_DISTRICT = max(0, int(os.environ.get("SHOWCASE_WUHAN_RESERVE_PER_DISTRICT", "12")))


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
    budget = AmapNetworkBudget(NETWORK_BUDGET)
    writes_since_flush = 0

    def cached_request(path: str, params: dict[str, Any], retries: int = 3) -> dict[str, Any]:
        nonlocal writes_since_flush
        cached = cache.get(path, params)
        if cached is not None:
            return cached

        # original_request 内部自带 retry。这里将其固定为单次 HTTP，再在外层逐次计入预算，
        # 避免“逻辑请求预算 520”实际因为三次重试放大成 1560 次 HTTP 调用。
        attempts = min(max(1, retries), MAX_HTTP_ATTEMPTS)
        last_error: Exception | None = None
        payload: dict[str, Any] | None = None
        for attempt in range(attempts):
            budget.consume(path)
            try:
                payload = original_request(path, params, retries=1)
                break
            except Exception as exc:  # noqa: BLE001
                last_error = exc
                if attempt + 1 >= attempts:
                    raise
                time.sleep(0.35 * (attempt + 1))
        if payload is None:
            raise RuntimeError(f"高德请求未获得结果 path={path}: {last_error}")

        cache.put(path, params, payload)
        writes_since_flush += 1
        # 默认每次真实网络请求成功后都落盘。生成速度可以慢一些，但失败重跑时尽量不浪费高德额度。
        if writes_since_flush >= CACHE_FLUSH_EVERY:
            cache.flush()
            writes_since_flush = 0
        return payload

    generator.request = cached_request
    if str(getattr(generator, "CITY", "")).strip() in {"武汉", "武汉市"}:
        generator.search_community_candidates = lambda: search_wuhan_community_candidates(
            generator,
            PUBLIC_CATALOG_FILE,
            reserve_per_district=WUHAN_RESERVE_PER_DISTRICT,
        )

    try:
        generator.main()
    finally:
        cache.flush()
        print(
            "高德缓存："
            f"mode={cache.mode} hits={cache.hits} misses={cache.misses} writes={cache.writes} "
            f"httpAttempts={budget.used} networkBudget={budget.limit or 'unlimited'} "
            f"file={cache.path}",
            flush=True,
        )


if __name__ == "__main__":
    main()
