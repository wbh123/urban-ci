from __future__ import annotations

import importlib.util
import json
import tempfile
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
MODULE_PATH = ROOT / "scripts" / "dev" / "showcase_amap_cache.py"

spec = importlib.util.spec_from_file_location("showcase_amap_cache_under_test", MODULE_PATH)
assert spec is not None and spec.loader is not None
cache_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(cache_module)
AmapRequestCache = cache_module.AmapRequestCache
CacheMissError = cache_module.CacheMissError


def test_prefer_supports_partial_and_full_cache_without_persisting_key() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        cache_file = Path(tmp) / "amap-cache.json"
        cache = AmapRequestCache(cache_file, "prefer")
        request = {"keywords": "小区", "region": "武昌区", "key": "SECRET", "output": "JSON"}
        payload = {"status": "1", "pois": [{"id": "A"}]}
        cache.put("/v5/place/text", request, payload)
        cache.flush()

        assert "SECRET" not in cache_file.read_text(encoding="utf-8")
        document = json.loads(cache_file.read_text(encoding="utf-8"))
        entry = next(iter(document["entries"].values()))
        assert "key" not in entry["params"]
        assert "output" not in entry["params"]

        reused = AmapRequestCache(cache_file, "prefer")
        assert reused.get("/v5/place/text", request) == payload
        assert reused.get("/v3/geocode/regeo", {"location": "114,30"}) is None
        assert reused.hits == 1
        assert reused.misses == 1


def test_only_fails_when_any_request_is_not_cached() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        cache = AmapRequestCache(Path(tmp) / "amap-cache.json", "only")
        with pytest.raises(CacheMissError):
            cache.get("/v5/place/around", {"location": "114,30"})


def test_refresh_replaces_old_payload_and_off_never_writes() -> None:
    with tempfile.TemporaryDirectory() as tmp:
        cache_file = Path(tmp) / "amap-cache.json"
        initial = AmapRequestCache(cache_file, "prefer")
        initial.put("/v5/place/text", {"keywords": "小区"}, {"value": "old"})
        initial.flush()

        refresh = AmapRequestCache(cache_file, "refresh")
        assert refresh.get("/v5/place/text", {"keywords": "小区"}) is None
        refresh.put("/v5/place/text", {"keywords": "小区"}, {"value": "new"})
        refresh.flush()
        assert AmapRequestCache(cache_file, "prefer").get(
            "/v5/place/text", {"keywords": "小区"}
        ) == {"value": "new"}

        off_file = Path(tmp) / "off.json"
        off = AmapRequestCache(off_file, "off")
        off.put("/v5/place/text", {"keywords": "小区"}, {"ignored": True})
        off.flush()
        assert not off_file.exists()
