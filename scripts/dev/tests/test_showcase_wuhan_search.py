#!/usr/bin/env python3
from __future__ import annotations

import sys
import tempfile
import unittest
from dataclasses import dataclass
from pathlib import Path
from types import SimpleNamespace

DEV_DIR = Path(__file__).resolve().parents[1]
if str(DEV_DIR) not in sys.path:
    sys.path.insert(0, str(DEV_DIR))

from showcase_wuhan_search import search_community_candidates, select_balanced_candidates  # noqa: E402


@dataclass
class Candidate:
    poi_id: str
    name: str
    district: str


class WuhanSelectionTest(unittest.TestCase):
    def test_balances_target_across_districts(self) -> None:
        districts = ["江岸区", "江汉区", "硚口区", "汉阳区", "武昌区", "青山区", "洪山区"]
        buckets = {
            district: [Candidate(f"{district}-{i}", f"{district}小区{i:02d}", district) for i in range(30)]
            for district in districts
        }
        selected = select_balanced_candidates(buckets, 100, districts, set())
        self.assertEqual(100, len(selected))
        counts = {district: sum(1 for item in selected if item.district == district) for district in districts}
        self.assertEqual([15, 15, 14, 14, 14, 14, 14], [counts[d] for d in districts])

    def test_prefers_public_catalog_matches_within_each_district(self) -> None:
        districts = ["武昌区", "洪山区"]
        buckets = {
            "武昌区": [Candidate("a", "普通花园", "武昌区"), Candidate("b", "东亭花园", "武昌区")],
            "洪山区": [Candidate("c", "普通小区", "洪山区"), Candidate("d", "东方红花园", "洪山区")],
        }
        selected = select_balanced_candidates(buckets, 2, districts, {"东亭花园", "东方红花园"})
        self.assertEqual({"东亭花园", "东方红花园"}, {item.name for item in selected})

    def test_uses_leftovers_when_one_district_cannot_fill_quota(self) -> None:
        districts = ["甲区", "乙区"]
        buckets = {
            "甲区": [Candidate("a", "甲小区", "甲区")],
            "乙区": [Candidate(f"b{i}", f"乙小区{i}", "乙区") for i in range(10)],
        }
        selected = select_balanced_candidates(buckets, 6, districts, set())
        self.assertEqual(6, len(selected))
        self.assertEqual(1, sum(1 for item in selected if item.district == "甲区"))
        self.assertEqual(5, sum(1 for item in selected if item.district == "乙区"))

    def test_search_does_not_count_cross_district_results_toward_requested_quota(self) -> None:
        class FakeGenerator:
            DISTRICTS = ["甲区", "乙区"]
            TARGET_COMMUNITIES = 4
            MAX_POI_PAGES = 1
            CITY = "武汉"
            Poi = SimpleNamespace

            @staticmethod
            def is_residential(raw: dict[str, object]) -> bool:
                return True

            @staticmethod
            def parse_location(value: object) -> tuple[float, float] | None:
                text = str(value or "")
                if "," not in text:
                    return None
                lng, lat = text.split(",", 1)
                return float(lng), float(lat)

            @staticmethod
            def request(path: str, params: dict[str, object]) -> dict[str, object]:
                region = str(params["region"])
                # 模拟高德“甲区”检索返回相邻“乙区”POI。旧逻辑会把这些结果错误计入甲区配额。
                actual_district = "乙区"
                prefix = "leak" if region == "甲区" else "real"
                return {
                    "pois": [
                        {
                            "id": f"{prefix}-{index}",
                            "name": f"乙区住宅{index}",
                            "location": f"114.{index:02d},30.{index:02d}",
                            "address": "武汉市乙区测试地址",
                            "adname": actual_district,
                            "adcode": "420106",
                            "cityname": "武汉市",
                            "type": "商务住宅;住宅区",
                            "typecode": "120300",
                            "children": [],
                        }
                        for index in range(4)
                    ]
                }

        with tempfile.TemporaryDirectory() as tmpdir:
            missing_catalog = Path(tmpdir) / "missing.json"
            with self.assertRaisesRegex(RuntimeError, "甲区.*0/2"):
                search_community_candidates(
                    FakeGenerator,
                    missing_catalog,
                    reserve_per_district=0,
                    keywords=("小区",),
                )


if __name__ == "__main__":
    unittest.main()
