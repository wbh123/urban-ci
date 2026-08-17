#!/usr/bin/env python3
from __future__ import annotations

import sys
import tempfile
import unittest
from dataclasses import dataclass
from pathlib import Path

DEV_DIR = Path(__file__).resolve().parents[1]
if str(DEV_DIR) not in sys.path:
    sys.path.insert(0, str(DEV_DIR))

from showcase_wuhan_search import search_community_candidates  # noqa: E402


@dataclass
class Poi:
    poi_id: str
    name: str
    lng: float
    lat: float
    address: str
    district: str
    adcode: str
    poi_type: str = ""
    typecode: str = ""
    children: list | None = None


class FakeGenerator:
    DISTRICTS = ["青山区"]
    TARGET_COMMUNITIES = 2
    MAX_POI_PAGES = 1
    CITY = "武汉"
    Poi = Poi

    def __init__(self) -> None:
        self.regions: list[str] = []

    @staticmethod
    def is_residential(raw: dict) -> bool:
        return True

    @staticmethod
    def parse_location(value: object):
        text = str(value or "")
        if "," not in text:
            return None
        lng, lat = text.split(",", 1)
        return float(lng), float(lat)

    def request(self, path: str, params: dict):
        self.regions.append(str(params["region"]))
        if params["region"] == "青山区":
            return {
                "pois": [
                    {
                        "id": "legacy-hongshan",
                        "name": "跨区住宅",
                        "location": "114.40,30.55",
                        "cityname": "武汉市",
                        "adname": "洪山区",
                        "adcode": "420111",
                        "address": "测试地址",
                    }
                ]
            }
        if params["region"] == "420107":
            return {
                "pois": [
                    {
                        "id": "qs-1",
                        "name": "青山一小区",
                        "location": "114.39,30.64",
                        "cityname": "武汉市",
                        "adname": "青山区",
                        "adcode": "420107",
                        "address": "青山区测试地址1",
                    },
                    {
                        "id": "qs-2",
                        "name": "青山二小区",
                        "location": "114.40,30.65",
                        "cityname": "武汉市",
                        "adname": "武汉化学工业区",
                        "adcode": "420107",
                        "address": "青山区测试地址2",
                    },
                ]
            }
        return {"pois": []}


class WuhanAdcodeFallbackTest(unittest.TestCase):
    def test_uses_adcode_only_after_legacy_district_query_cannot_fill_real_quota(self) -> None:
        generator = FakeGenerator()
        with tempfile.TemporaryDirectory() as tmp:
            catalog = Path(tmp) / "catalog.json"
            catalog.write_text('{"entries": []}', encoding="utf-8")
            selected = search_community_candidates(
                generator,
                catalog,
                reserve_per_district=0,
                keywords=("小区",),
            )

        self.assertEqual(["青山区", "420107"], generator.regions)
        self.assertEqual(2, len(selected))
        self.assertEqual({"青山区"}, {item.district for item in selected})
        self.assertEqual({"qs-1", "qs-2"}, {item.poi_id for item in selected})


if __name__ == "__main__":
    unittest.main()
