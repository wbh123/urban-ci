#!/usr/bin/env python3
from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
import sys

DEV_DIR = Path(__file__).resolve().parents[1]
if str(DEV_DIR) not in sys.path:
    sys.path.insert(0, str(DEV_DIR))

from showcase_amap_cache import AmapRequestCache, CacheMissError  # noqa: E402


class AmapRequestCacheTest(unittest.TestCase):
    def test_prefer_reuses_cached_request_and_strips_secret_params(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "cache.json"
            cache = AmapRequestCache(path, "prefer")
            params = {"keywords": "小区", "region": "武昌区", "key": "SECRET", "output": "JSON"}
            payload = {"status": "1", "pois": [{"id": "A"}]}
            cache.put("/v5/place/text", params, payload)
            cache.flush()

            raw = path.read_text(encoding="utf-8")
            self.assertNotIn("SECRET", raw)
            saved = json.loads(raw)
            entry = next(iter(saved["entries"].values()))
            self.assertNotIn("key", entry["params"])
            self.assertNotIn("output", entry["params"])

            reloaded = AmapRequestCache(path, "prefer")
            self.assertEqual(payload, reloaded.get("/v5/place/text", params))
            self.assertEqual(1, reloaded.hits)

    def test_prefer_returns_none_for_partial_cache_miss(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache = AmapRequestCache(Path(tmp) / "cache.json", "prefer")
            cache.put("/v5/place/text", {"keywords": "小区"}, {"status": "1"})
            cache.flush()
            self.assertIsNone(cache.get("/v3/geocode/regeo", {"location": "114,30"}))
            self.assertEqual(1, cache.misses)

    def test_only_requires_every_request_to_exist(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            cache = AmapRequestCache(Path(tmp) / "cache.json", "only")
            with self.assertRaises(CacheMissError):
                cache.get("/v5/place/around", {"location": "114,30"})

    def test_refresh_ignores_existing_entry_and_overwrites_it(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "cache.json"
            initial = AmapRequestCache(path, "prefer")
            initial.put("/v5/place/text", {"keywords": "小区"}, {"value": "old"})
            initial.flush()

            refresh = AmapRequestCache(path, "refresh")
            self.assertIsNone(refresh.get("/v5/place/text", {"keywords": "小区"}))
            refresh.put("/v5/place/text", {"keywords": "小区"}, {"value": "new"})
            refresh.flush()

            verify = AmapRequestCache(path, "prefer")
            self.assertEqual({"value": "new"}, verify.get("/v5/place/text", {"keywords": "小区"}))

    def test_off_neither_reads_nor_writes_cache(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "cache.json"
            cache = AmapRequestCache(path, "off")
            self.assertIsNone(cache.get("/v5/place/text", {"keywords": "小区"}))
            cache.put("/v5/place/text", {"keywords": "小区"}, {"value": "ignored"})
            cache.flush()
            self.assertFalse(path.exists())


if __name__ == "__main__":
    unittest.main()
