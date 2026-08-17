#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

DEV_DIR = Path(__file__).resolve().parents[1]
MODULE_PATH = DEV_DIR / "generate-showcase-closure.py"
spec = importlib.util.spec_from_file_location("generate_showcase_closure", MODULE_PATH)
if spec is None or spec.loader is None:
    raise RuntimeError(f"无法加载闭环生成器：{MODULE_PATH}")
module = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = module
spec.loader.exec_module(module)
generate_sql = module.generate_sql


class GenerateShowcaseClosureTest(unittest.TestCase):
    def test_sql_contains_complete_per_building_chain(self) -> None:
        assets = []
        for index, (issue_type, slug) in enumerate(
            (
                ("CRACK", "crack"),
                ("WATER_LEAKAGE", "water-leakage"),
                ("SURFACE_FALLING", "surface-falling"),
                ("DEFORMATION", "deformation"),
            ),
            start=1,
        ):
            assets.append(
                {
                    "issueType": issue_type,
                    "variant": 1,
                    "filename": f"{slug}-01.png",
                    "objectKey": f"showcase/inspection/{slug}-01.png",
                    "sha256": format(index, "064x"),
                    "size": 1234 + index,
                    "width": 640,
                    "height": 360,
                    "contentType": "image/png",
                    "syntheticImage": True,
                }
            )
        manifest = {"assets": assets}
        sql = generate_sql(manifest, "urban-safe-assets", history_months=24, public_catalog={"entries": []})
        self.assertIn("generate_series(1, 3)", sql)
        self.assertIn("INSERT INTO core.inspection_task", sql)
        self.assertIn("INSERT INTO core.inspection_record", sql)
        self.assertIn("INSERT INTO core.building_evidence", sql)
        self.assertIn("INSERT INTO core.resident_report", sql)
        self.assertIn("INSERT INTO asset.file_asset", sql)
        self.assertIn("INSERT INTO asset.asset_binding", sql)
        self.assertIn("INSERT INTO ai.inference_task", sql)
        self.assertIn("INSERT INTO ai.inference_result", sql)
        self.assertIn("INSERT INTO ai.detection", sql)
        self.assertIn("INSERT INTO ai.inference_review", sql)
        self.assertIn("showcaseClosure", sql)
        self.assertIn("polygon", sql)
        self.assertIn("CURRENT_TIMESTAMP - make_interval(hours", sql)


if __name__ == "__main__":
    unittest.main()
