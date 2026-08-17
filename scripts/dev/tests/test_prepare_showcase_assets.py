#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

DEV_DIR = Path(__file__).resolve().parents[1]
MODULE_PATH = DEV_DIR / "prepare-showcase-assets.py"
spec = importlib.util.spec_from_file_location("prepare_showcase_assets", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError(f"无法加载巡检图片生成器：{MODULE_PATH}")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
generate_assets = module.generate_assets


class PrepareShowcaseAssetsTest(unittest.TestCase):
    def test_generates_deterministic_png_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp)
            manifest = generate_assets(out, variants_per_issue=2)
            self.assertEqual(8, len(manifest["assets"]))
            for item in manifest["assets"]:
                path = out / item["filename"]
                self.assertTrue(path.exists())
                data = path.read_bytes()
                self.assertTrue(data.startswith(b"\x89PNG\r\n\x1a\n"))
                self.assertEqual(hashlib.sha256(data).hexdigest(), item["sha256"])
                self.assertEqual(len(data), item["size"])
                self.assertEqual(640, item["width"])
                self.assertEqual(360, item["height"])

            saved = json.loads((out / "manifest.json").read_text(encoding="utf-8"))
            self.assertEqual(manifest, saved)


if __name__ == "__main__":
    unittest.main()
