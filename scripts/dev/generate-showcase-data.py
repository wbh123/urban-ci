#!/usr/bin/env python3
from __future__ import annotations

import hashlib
import json
import math
import os
import random
import re
import time
import urllib.parse
import urllib.request
import uuid
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

KEY = os.environ["AMAP_KEY"]
BASE = os.environ.get("AMAP_BASE", "https://restapi.amap.com").rstrip("/")
CITY = os.environ.get("SHOWCASE_CITY", "武汉")
SEED = int(os.environ.get("SHOWCASE_SEED", "20260810"))
TARGET_COMMUNITIES = int(os.environ.get("SHOWCASE_COMMUNITY_COUNT", "36"))
TARGET_BUILDINGS = int(os.environ.get("SHOWCASE_BUILDINGS_PER_COMMUNITY", "8"))
MAX_REAL_BUILDINGS = int(os.environ.get("SHOWCASE_MAX_REAL_BUILDINGS_PER_COMMUNITY", "10"))
MAX_POI_PAGES = int(os.environ.get("SHOWCASE_MAX_POI_PAGES", "3"))
SQL_FILE = Path(os.environ["SHOWCASE_SQL_FILE"])
META_FILE = Path(os.environ["SHOWCASE_META_FILE"])
DISTRICTS = [
    item.strip()
    for item in os.environ.get(
        "SHOWCASE_DISTRICTS",
        "江岸区,江汉区,硚口区,汉阳区,武昌区,青山区,洪山区",
    ).split(",")
    if item.strip()
]

rng = random.Random(SEED)
BUILDING_NAME_RE = re.compile(r"(?:\d{1,3}|[一二三四五六七八九十百]+)[号#]?(?:栋|幢|号楼|座)")
COMMUNITY_KEYWORDS = ("小区", "家园", "花园", "公寓", "社区", "新城", "苑")
ISSUE_TYPES = ("CRACK", "SURFACE_FALLING", "WATER_LEAKAGE", "DEFORMATION")
SEVERITIES = ("LOW", "MEDIUM", "MEDIUM", "HIGH")
STRUCTURES = ("BRICK_CONCRETE", "FRAME", "MASONRY", "FRAME_SHEAR")
PARTS = ("外立面", "楼梯间", "屋面", "首层公共区域", "地下空间", "阳台及外窗")


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
    children: list[dict[str, Any]] = field(default_factory=list)
    aoi_id: str = ""
    aoi_area: float | None = None
    formatted_address: str = ""
    township: str = ""


@dataclass
class BuildingRow:
    code: str
    name: str
    address: str
    real_poi_id: str | None
    lng: float | None
    lat: float | None
    construction_year: int
    structure_type: str
    floor_count: int
    building_area: float
    household_count: int
    resident_count: int
    archive_score: float
    has_elevator: bool
    has_illegal_modification: bool
    has_ground_floor_business: bool


def request(path: str, params: dict[str, Any], retries: int = 3) -> dict[str, Any]:
    query = urllib.parse.urlencode({**params, "key": KEY, "output": "JSON"})
    url = f"{BASE}{path}?{query}"
    last_error: Exception | None = None
    for attempt in range(retries):
        try:
            req = urllib.request.Request(
                url,
                headers={"User-Agent": "UrbanSafe-Showcase-Generator/2.0"},
            )
            with urllib.request.urlopen(req, timeout=12) as response:
                payload = json.load(response)
            if str(payload.get("status")) != "1":
                raise RuntimeError(
                    f"高德接口失败 path={path} info={payload.get('info')} infocode={payload.get('infocode')}"
                )
            time.sleep(0.035)
            return payload
        except Exception as exc:  # noqa: BLE001
            last_error = exc
            time.sleep(0.35 * (attempt + 1))
    raise RuntimeError(f"高德接口连续失败 path={path}: {last_error}")


def parse_location(value: Any) -> tuple[float, float] | None:
    text = str(value or "").strip()
    if "," not in text:
        return None
    try:
        lng, lat = map(float, text.split(",", 1))
    except ValueError:
        return None
    if not (70 <= lng <= 140 and 10 <= lat <= 60):
        return None
    return lng, lat


def haversine_m(a_lng: float, a_lat: float, b_lng: float, b_lat: float) -> float:
    radius = 6371000.0
    p1 = math.radians(a_lat)
    p2 = math.radians(b_lat)
    dp = math.radians(b_lat - a_lat)
    dl = math.radians(b_lng - a_lng)
    value = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * radius * math.asin(math.sqrt(value))


def is_residential(poi: dict[str, Any]) -> bool:
    name = str(poi.get("name") or "")
    poi_type = str(poi.get("type") or "")
    typecode = str(poi.get("typecode") or "")
    if not parse_location(poi.get("location")):
        return False
    residential_words = ("住宅", "小区", "家园", "花园", "公寓", "社区", "宿舍", "苑")
    return (
        any(word in poi_type for word in residential_words)
        or any(word in name for word in residential_words)
        or typecode.startswith("1203")
    )


def search_community_candidates() -> list[Poi]:
    buckets: dict[str, dict[str, Poi]] = defaultdict(dict)
    for district in DISTRICTS:
        for keyword in COMMUNITY_KEYWORDS:
            for page in range(1, MAX_POI_PAGES + 1):
                payload = request(
                    "/v5/place/text",
                    {
                        "keywords": keyword,
                        "types": "120000",
                        "region": district,
                        "city_limit": "true",
                        "show_fields": "children,navi",
                        "page_size": 25,
                        "page_num": page,
                    },
                )
                rows = payload.get("pois") or []
                for raw in rows:
                    if not isinstance(raw, dict) or not is_residential(raw):
                        continue
                    location = parse_location(raw.get("location"))
                    if not location:
                        continue
                    poi_id = str(raw.get("id") or "").strip()
                    if not poi_id:
                        continue
                    adname = str(raw.get("adname") or district).strip()
                    cityname = str(raw.get("cityname") or CITY).strip()
                    if CITY not in cityname and not str(raw.get("adcode") or "").startswith("4201"):
                        continue
                    lng, lat = location
                    buckets[district][poi_id] = Poi(
                        poi_id=poi_id,
                        name=str(raw.get("name") or "住宅小区").strip(),
                        lng=lng,
                        lat=lat,
                        address=str(raw.get("address") or "").strip(),
                        district=adname or district,
                        adcode=str(raw.get("adcode") or "").strip(),
                        poi_type=str(raw.get("type") or "").strip(),
                        typecode=str(raw.get("typecode") or "").strip(),
                        children=[item for item in (raw.get("children") or []) if isinstance(item, dict)],
                    )
                if len(rows) < 25:
                    break

    selected: list[Poi] = []
    ordered_buckets = {
        district: sorted(rows.values(), key=lambda item: (item.name, item.poi_id))
        for district, rows in buckets.items()
    }
    # 轮询各中心城区，避免所有数据集中在一个区。
    while len(selected) < TARGET_COMMUNITIES:
        progressed = False
        for district in DISTRICTS:
            rows = ordered_buckets.get(district) or []
            if not rows:
                continue
            index = len([item for item in selected if item.district == district])
            if index < len(rows):
                selected.append(rows[index])
                progressed = True
                if len(selected) >= TARGET_COMMUNITIES:
                    break
        if not progressed:
            break

    if len(selected) < max(8, min(TARGET_COMMUNITIES, 16)):
        raise RuntimeError(f"高德住宅 POI 数量不足：仅获得 {len(selected)} 个，可降低 SHOWCASE_COMMUNITY_COUNT")
    return selected[:TARGET_COMMUNITIES]


def enrich_aoi(poi: Poi) -> None:
    payload = request(
        "/v3/geocode/regeo",
        {
            "location": f"{poi.lng:.6f},{poi.lat:.6f}",
            "radius": 700,
            "extensions": "all",
            "homeorcorp": 1,
        },
    )
    regeocode = payload.get("regeocode") or {}
    poi.formatted_address = str(regeocode.get("formatted_address") or poi.address).strip()
    component = regeocode.get("addressComponent") or {}
    poi.township = str(component.get("township") or "").strip()
    aois = [item for item in (regeocode.get("aois") or []) if isinstance(item, dict)]
    if not aois:
        return

    def score(aoi: dict[str, Any]) -> tuple[int, float]:
        exact = int(str(aoi.get("id") or "") == poi.poi_id)
        same_name = int(str(aoi.get("name") or "").strip() == poi.name)
        distance = float(str(aoi.get("distance") or "999999") or 999999)
        return exact * 100 + same_name * 50, -distance

    chosen = max(aois, key=score)
    poi.aoi_id = str(chosen.get("id") or "").strip()
    try:
        poi.aoi_area = float(chosen.get("area")) if chosen.get("area") not in (None, "") else None
    except (TypeError, ValueError):
        poi.aoi_area = None


def community_core_name(name: str) -> str:
    result = re.sub(r"(?:住宅小区|小区|社区|花园|家园|公寓|新城|苑|城|里)$", "", name).strip()
    return result if len(result) >= 2 else name


def building_candidates(poi: Poi) -> list[dict[str, Any]]:
    candidates: dict[str, dict[str, Any]] = {}

    def add(raw: dict[str, Any], source: str) -> None:
        name = str(raw.get("name") or "").strip()
        location = parse_location(raw.get("location"))
        if not name or not location or not BUILDING_NAME_RE.search(name):
            return
        lng, lat = location
        distance = haversine_m(poi.lng, poi.lat, lng, lat)
        if distance > 850:
            return
        poi_id = str(raw.get("id") or hashlib.sha1(f"{name}:{lng}:{lat}".encode()).hexdigest()[:16])
        core = community_core_name(poi.name)
        if core not in name and distance > 320:
            return
        current = candidates.get(poi_id)
        item = {
            "id": poi_id,
            "name": name,
            "location": [lng, lat],
            "address": str(raw.get("address") or poi.formatted_address or poi.address).strip(),
            "distance": distance,
            "source": source,
        }
        if current is None or item["distance"] < current["distance"]:
            candidates[poi_id] = item

    for child in poi.children:
        add(child, "AMAP_CHILD_POI")

    for keyword in ("栋", "号楼"):
        payload = request(
            "/v5/place/around",
            {
                "location": f"{poi.lng:.6f},{poi.lat:.6f}",
                "radius": 850,
                "keywords": keyword,
                "types": "120000",
                "region": CITY,
                "show_fields": "children,navi",
                "page_size": 25,
                "page_num": 1,
            },
        )
        for raw in payload.get("pois") or []:
            if isinstance(raw, dict):
                add(raw, "AMAP_AROUND_POI")

    core = community_core_name(poi.name)
    result = list(candidates.values())
    result.sort(key=lambda item: (0 if core in item["name"] else 1, item["distance"], item["name"]))
    return result[:MAX_REAL_BUILDINGS]


def build_rows(poi: Poi, real_candidates: list[dict[str, Any]], community_index: int) -> list[BuildingRow]:
    total = max(TARGET_BUILDINGS, min(MAX_REAL_BUILDINGS, len(real_candidates)))
    total = max(total, len(real_candidates))
    rows: list[BuildingRow] = []
    for index in range(total):
        real = real_candidates[index] if index < len(real_candidates) else None
        construction_year = rng.randint(1978, 2018)
        floor_count = rng.randint(6, 18) if construction_year < 2008 else rng.randint(8, 33)
        households = max(24, int(floor_count * rng.uniform(5.5, 9.5)))
        residents = int(households * rng.uniform(2.0, 2.8))
        area = round(households * rng.uniform(72, 112), 2)
        archive_score = round(rng.uniform(56, 97), 2)
        illegal = rng.random() < (0.20 if construction_year < 1995 else 0.08)
        ground_business = rng.random() < 0.24
        has_elevator = construction_year >= 2005 or floor_count >= 9
        name = real["name"] if real else f"{poi.name} {index + 1}号楼"
        address = real["address"] if real else (poi.formatted_address or poi.address)
        lng = real["location"][0] if real else None
        lat = real["location"][1] if real else None
        rows.append(
            BuildingRow(
                code=f"S{community_index + 1:03d}-B{index + 1:03d}",
                name=name,
                address=address,
                real_poi_id=real["id"] if real else None,
                lng=lng,
                lat=lat,
                construction_year=construction_year,
                structure_type=rng.choice(STRUCTURES),
                floor_count=floor_count,
                building_area=area,
                household_count=households,
                resident_count=residents,
                archive_score=archive_score,
                has_elevator=has_elevator,
                has_illegal_modification=illegal,
                has_ground_floor_business=ground_business,
            )
        )
    return rows


def q(value: Any) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    return "'" + str(value).replace("'", "''") + "'"


def jq(value: Any) -> str:
    return q(json.dumps(value, ensure_ascii=False, separators=(",", ":"))) + "::jsonb"


def code_for_poi(poi_id: str) -> str:
    digest = hashlib.sha1(poi_id.encode("utf-8")).hexdigest()[:10].upper()
    return f"SHOWCASE-WH-{digest}"


def deterministic_uuid(namespace: str, key: str) -> str:
    return str(uuid.uuid5(uuid.NAMESPACE_URL, f"urban-safe:{namespace}:{key}"))


def interval_expr(days: int, hours: int = 0) -> str:
    sign = "-" if days >= 0 else "+"
    parts = []
    if abs(days):
        parts.append(f"{abs(days)} days")
    if abs(hours):
        parts.append(f"{abs(hours)} hours")
    text = " ".join(parts) or "0 days"
    return f"CURRENT_TIMESTAMP {sign} INTERVAL '{text}'"


def generate_sql(communities: list[Poi], building_map: dict[str, list[BuildingRow]]) -> str:
    sql: list[str] = [
        "BEGIN;",
        "-- 城市展示数据：真实高德 POI/AOI 元数据 + 合成业务属性；不生成伪造小区/楼栋 Polygon。",
    ]

    for index, poi in enumerate(communities):
        community_code = code_for_poi(poi.poi_id)
        buildings = building_map[poi.poi_id]
        household_count = sum(item.household_count for item in buildings)
        resident_count = sum(item.resident_count for item in buildings)
        avg_archive = round(sum(item.archive_score for item in buildings) / max(len(buildings), 1), 2)
        years = [item.construction_year for item in buildings]
        period = f"{min(years)}—{max(years)}" if years else "未知"
        metadata = {
            "showcaseGenerated": True,
            "source": "AMAP_POI",
            "amapPoiId": poi.poi_id,
            "amapAoiId": poi.aoi_id or None,
            "amapAoiAreaSquareMeter": poi.aoi_area,
            "spatialBoundaryAvailable": False,
            "boundaryPolicy": "OFFICIAL_LAYER_OR_VERIFIED_BOUNDARY_ONLY",
        }
        region = f"湖北省武汉市{poi.district}"
        address = poi.formatted_address or poi.address or region
        sql.append(
            f"""
INSERT INTO core.community (
  community_code, community_name, administrative_region, address,
  construction_period, building_count, household_count, resident_count,
  archive_completeness_score, status, extra_attributes, remark
) VALUES (
  {q(community_code)}, {q(poi.name)}, {q(region)}, {q(address)}, {q(period)},
  {len(buildings)}, {household_count}, {resident_count}, {avg_archive}, 'ACTIVE',
  {jq(metadata)}, '城市中心城区样例数据'
)
ON CONFLICT (community_code) WHERE deleted_at IS NULL DO UPDATE SET
  community_name=EXCLUDED.community_name,
  administrative_region=EXCLUDED.administrative_region,
  address=EXCLUDED.address,
  construction_period=EXCLUDED.construction_period,
  building_count=EXCLUDED.building_count,
  household_count=EXCLUDED.household_count,
  resident_count=EXCLUDED.resident_count,
  archive_completeness_score=EXCLUDED.archive_completeness_score,
  status='ACTIVE', extra_attributes=EXCLUDED.extra_attributes,
  remark=EXCLUDED.remark, updated_at=CURRENT_TIMESTAMP;

INSERT INTO geo.community_location (
  community_id, centroid, formatted_address, source_provider, source_coordinate_system,
  match_level, quality_score, metadata
)
SELECT id, ST_SetSRID(ST_MakePoint({poi.lng:.6f},{poi.lat:.6f}),4326), {q(address)},
       'AMAP','GCJ02','AMAP_RESIDENTIAL_POI',98,
       {jq(metadata)}
FROM core.community WHERE community_code={q(community_code)} AND deleted_at IS NULL
ON CONFLICT (community_id) WHERE deleted_at IS NULL DO UPDATE SET
  centroid=EXCLUDED.centroid, formatted_address=EXCLUDED.formatted_address,
  source_provider=EXCLUDED.source_provider, source_coordinate_system=EXCLUDED.source_coordinate_system,
  match_level=EXCLUDED.match_level, quality_score=EXCLUDED.quality_score,
  metadata=EXCLUDED.metadata, collected_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP;
"""
        )

        for building_index, building in enumerate(buildings):
            building_meta = {
                "showcaseGenerated": True,
                "businessAttributesSynthetic": True,
                "spatialSource": "AMAP_BUILDING_POI" if building.real_poi_id else "UNRESOLVED",
                "amapPoiId": building.real_poi_id,
                "spatialUnresolved": building.real_poi_id is None,
                "boundaryPolicy": "AMAP_BUILDINGS_VISUAL_OR_VERIFIED_BOUNDARY_ONLY",
            }
            sql.append(
                f"""
INSERT INTO core.building (
  community_id, building_code, building_name, address, construction_year,
  structure_type, floor_count, building_area, household_count, resident_count,
  elderly_count, child_count, has_elevator, has_illegal_modification,
  has_ground_floor_business, archive_completeness_score, status,
  extra_attributes, remark
)
SELECT c.id, {q(building.code)}, {q(building.name)}, {q(building.address)}, {building.construction_year},
       {q(building.structure_type)}, {building.floor_count}, {building.building_area},
       {building.household_count}, {building.resident_count},
       {max(0, int(building.resident_count * rng.uniform(0.10, 0.24)))},
       {max(0, int(building.resident_count * rng.uniform(0.08, 0.18)))},
       {q(building.has_elevator)}, {q(building.has_illegal_modification)},
       {q(building.has_ground_floor_business)}, {building.archive_score}, 'ACTIVE',
       {jq(building_meta)}, '城市样例楼栋档案'
FROM core.community c
WHERE c.community_code={q(community_code)} AND c.deleted_at IS NULL
ON CONFLICT (community_id, building_code) WHERE deleted_at IS NULL DO UPDATE SET
  building_name=EXCLUDED.building_name, address=EXCLUDED.address,
  construction_year=EXCLUDED.construction_year, structure_type=EXCLUDED.structure_type,
  floor_count=EXCLUDED.floor_count, building_area=EXCLUDED.building_area,
  household_count=EXCLUDED.household_count, resident_count=EXCLUDED.resident_count,
  elderly_count=EXCLUDED.elderly_count, child_count=EXCLUDED.child_count,
  has_elevator=EXCLUDED.has_elevator,
  has_illegal_modification=EXCLUDED.has_illegal_modification,
  has_ground_floor_business=EXCLUDED.has_ground_floor_business,
  archive_completeness_score=EXCLUDED.archive_completeness_score,
  status='ACTIVE', extra_attributes=EXCLUDED.extra_attributes,
  remark=EXCLUDED.remark, updated_at=CURRENT_TIMESTAMP;
"""
            )
            if building.lng is not None and building.lat is not None:
                sql.append(
                    f"""
INSERT INTO geo.building_location (
  building_id, centroid, formatted_address, source_provider, source_coordinate_system,
  match_level, quality_score, metadata
)
SELECT b.id, ST_SetSRID(ST_MakePoint({building.lng:.6f},{building.lat:.6f}),4326), {q(building.address)},
       'AMAP','GCJ02','AMAP_BUILDING_POI',96,{jq(building_meta)}
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
WHERE c.community_code={q(community_code)} AND b.building_code={q(building.code)} AND b.deleted_at IS NULL
ON CONFLICT (building_id) WHERE deleted_at IS NULL DO UPDATE SET
  centroid=EXCLUDED.centroid, formatted_address=EXCLUDED.formatted_address,
  source_provider=EXCLUDED.source_provider, source_coordinate_system=EXCLUDED.source_coordinate_system,
  match_level=EXCLUDED.match_level, quality_score=EXCLUDED.quality_score,
  metadata=EXCLUDED.metadata, collected_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP;
"""
                )

    # 展示模式下禁用已知合成边界，避免将开发矩形绘制为真实社区/楼栋边界。
    sql.append(
        """
UPDATE geo.community_boundary cb
SET status='REJECTED', verified_by=NULL, verified_at=NULL,
    remark='当前展示使用高德真实点位/官方地图图层；该合成边界不参与展示',
    metadata=COALESCE(cb.metadata,'{}'::jsonb) || '{"showcaseSuppressed":true}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
FROM core.community c
WHERE cb.community_id=c.id AND cb.deleted_at IS NULL
  AND COALESCE(cb.metadata->>'syntheticBoundary','false')='true'
  AND (c.community_code LIKE 'DEMO-COMMUNITY-%' OR c.community_code LIKE 'SHOWCASE-WH-%');

UPDATE geo.building_boundary bb
SET status='REJECTED', verified_by=NULL, verified_at=NULL,
    remark='当前展示使用高德真实点位/官方楼块图层；该合成边界不参与展示',
    metadata=COALESCE(bb.metadata,'{}'::jsonb) || '{"showcaseSuppressed":true}'::jsonb,
    updated_at=CURRENT_TIMESTAMP
FROM core.building b
JOIN core.community c ON c.id=b.community_id
WHERE bb.building_id=b.id AND bb.deleted_at IS NULL
  AND COALESCE(bb.metadata->>'syntheticBoundary','false')='true'
  AND (c.community_code LIKE 'DEMO-COMMUNITY-%' OR c.community_code LIKE 'SHOWCASE-WH-%');
"""
    )

    # 巡检、证据、居民上报：围绕真实小区和楼栋档案生成丰富业务场景。
    task_no = 0
    report_no = 0
    evidence_no = 0
    for community_index, poi in enumerate(communities):
        community_code = code_for_poi(poi.poi_id)
        for building_index, building in enumerate(building_map[poi.poi_id]):
            global_index = community_index * 100 + building_index
            # 大约 72% 的楼栋有巡检任务；部分楼栋存在多条历史任务。
            task_count = 0 if global_index % 7 in (0, 6) else (2 if global_index % 5 == 0 else 1)
            for local_task in range(task_count):
                task_no += 1
                task_code = f"SHOWCASE-TASK-{task_no:04d}"
                status = ("COMPLETED", "COMPLETED", "PENDING", "IN_PROGRESS", "COMPLETED", "CANCELLED")[
                    (global_index + local_task) % 6
                ]
                inspection_type = ("ROUTINE", "SPECIAL", "COMPREHENSIVE")[(global_index + local_task) % 3]
                part = PARTS[(global_index + local_task) % len(PARTS)]
                issue = ISSUE_TYPES[(global_index + local_task) % len(ISSUE_TYPES)]
                severity = SEVERITIES[(global_index + local_task) % len(SEVERITIES)]
                days = 2 + ((global_index * 3 + local_task * 7) % 55)
                planned = interval_expr(days)
                started = "NULL"
                completed = "NULL"
                cancelled = "NULL"
                if status in ("IN_PROGRESS", "COMPLETED"):
                    started = interval_expr(max(1, days - 1), 2)
                if status == "COMPLETED":
                    completed = interval_expr(max(1, days - 1), 0)
                if status == "CANCELLED":
                    cancelled = interval_expr(days)
                sql.append(
                    f"""
INSERT INTO core.inspection_task (
  task_code, building_id, inspection_type, planned_at, assigned_to, focus_parts,
  status, created_by, title, description, started_at, completed_at, cancelled_at, remark
)
SELECT {q(task_code)}, b.id, {q(inspection_type)}, {planned}, inspector.id,
       {jq([part])}, {q(status)}, manager.id,
       {q(building.name + ' ' + part + '巡检')},
       {q('围绕建筑外观、公共区域和维护情况开展现场核查。')},
       {started}, {completed}, {cancelled}, '城市样例巡检任务'
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
WHERE c.community_code={q(community_code)} AND b.building_code={q(building.code)} AND b.deleted_at IS NULL
ON CONFLICT (task_code) WHERE deleted_at IS NULL DO UPDATE SET
  building_id=EXCLUDED.building_id, inspection_type=EXCLUDED.inspection_type,
  planned_at=EXCLUDED.planned_at, assigned_to=EXCLUDED.assigned_to,
  focus_parts=EXCLUDED.focus_parts, status=EXCLUDED.status,
  created_by=EXCLUDED.created_by, title=EXCLUDED.title,
  description=EXCLUDED.description, started_at=EXCLUDED.started_at,
  completed_at=EXCLUDED.completed_at, cancelled_at=EXCLUDED.cancelled_at,
  remark=EXCLUDED.remark, updated_at=CURRENT_TIMESTAMP;
"""
                )
                if status in ("IN_PROGRESS", "COMPLETED"):
                    records_for_task = 2 if status == "COMPLETED" and global_index % 3 == 0 else 1
                    for record_index in range(records_for_task):
                        record_id = deterministic_uuid("inspection-record", f"{task_code}:{record_index}")
                        record_status = "COMPLETED" if status == "COMPLETED" else "DRAFT"
                        record_part = PARTS[(global_index + record_index + 1) % len(PARTS)]
                        record_issue = ISSUE_TYPES[(global_index + record_index) % len(ISSUE_TYPES)]
                        record_severity = SEVERITIES[(global_index + record_index) % len(SEVERITIES)]
                        summary = {
                            "LOW": "现场整体状况稳定，发现轻微表观问题，建议纳入常规维护。",
                            "MEDIUM": "发现局部开裂、渗漏或饰面异常，需要跟踪复查并安排维修。",
                            "HIGH": "发现较明显异常，已建议设置警示并提交专业复核。",
                        }[record_severity]
                        sql.append(
                            f"""
INSERT INTO core.inspection_record (
  id, inspection_task_id, building_id, inspector_id, inspection_part,
  inspected_at, submitted_at, status, summary, form_data, issue_type,
  severity, rectification_suggestion, extra_data, remark
)
SELECT {q(record_id)}::uuid, t.id, t.building_id, inspector.id, {q(record_part)},
       {interval_expr(max(1, days - 1), record_index + 1)},
       {interval_expr(max(1, days - 1), record_index + 2) if record_status == 'COMPLETED' else 'NULL'},
       {q(record_status)}, {q(summary)},
       {jq({'weather': ['SUNNY', 'CLOUDY', 'RAIN_AFTER'][global_index % 3], 'photoCount': 1 + global_index % 5})},
       {q(record_issue)}, {q(record_severity)},
       {q('根据风险程度安排维修、复查或专业检测。')},
       {jq({'showcaseGenerated': True, 'reviewRequired': record_severity == 'HIGH'})},
       '城市样例巡检记录'
FROM core.inspection_task t
JOIN core.user_account inspector ON inspector.username='demo_inspector' AND inspector.deleted_at IS NULL
WHERE t.task_code={q(task_code)} AND t.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  inspection_task_id=EXCLUDED.inspection_task_id, building_id=EXCLUDED.building_id,
  inspector_id=EXCLUDED.inspector_id, inspection_part=EXCLUDED.inspection_part,
  inspected_at=EXCLUDED.inspected_at, submitted_at=EXCLUDED.submitted_at,
  status=EXCLUDED.status, summary=EXCLUDED.summary, form_data=EXCLUDED.form_data,
  issue_type=EXCLUDED.issue_type, severity=EXCLUDED.severity,
  rectification_suggestion=EXCLUDED.rectification_suggestion,
  extra_data=EXCLUDED.extra_data, remark=EXCLUDED.remark,
  updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;
"""
                        )

            if global_index % 3 != 0:
                evidence_no += 1
                evidence_id = deterministic_uuid("building-evidence", f"{community_code}:{building.code}:{evidence_no}")
                evidence_type = (
                    "MAINTENANCE_RECORD",
                    "HISTORICAL_COMPLAINT",
                    "PROFESSIONAL_INSPECTION",
                    "GOVERNANCE_URGENCY",
                    "PUBLIC_VALUE",
                )[global_index % 5]
                reliability = "PROFESSIONAL_CONFIRMED" if evidence_type == "PROFESSIONAL_INSPECTION" else "OFFICIAL_RECORD"
                score = 25 + (global_index * 13) % 70
                sql.append(
                    f"""
INSERT INTO core.building_evidence (
  id, building_id, evidence_type, title, description, occurred_at,
  source, reliability_level, evidence_data, created_by
)
SELECT {q(evidence_id)}::uuid, b.id, {q(evidence_type)},
       {q('建筑安全治理档案补充记录')},
       {q('结合维护、投诉、专业检查和社区治理台账形成的历史证据。')},
       {interval_expr(20 + global_index % 320)}, '城市建筑安全治理台账', {q(reliability)},
       {jq({'showcaseGenerated': True, 'score': score})}, manager.id
FROM core.building b
JOIN core.community c ON c.id=b.community_id AND c.deleted_at IS NULL
JOIN core.user_account manager ON manager.username='demo_community' AND manager.deleted_at IS NULL
WHERE c.community_code={q(community_code)} AND b.building_code={q(building.code)} AND b.deleted_at IS NULL
ON CONFLICT (id) DO UPDATE SET
  building_id=EXCLUDED.building_id, evidence_type=EXCLUDED.evidence_type,
  title=EXCLUDED.title, description=EXCLUDED.description,
  occurred_at=EXCLUDED.occurred_at, source=EXCLUDED.source,
  reliability_level=EXCLUDED.reliability_level, evidence_data=EXCLUDED.evidence_data,
  created_by=EXCLUDED.created_by, updated_at=CURRENT_TIMESTAMP, deleted_at=NULL;
"""
                )

            if global_index % 4 == 1:
                report_no += 1
                report_code = f"SHOWCASE-REPORT-{report_no:04d}"
                report_type = ("WALL_CRACK", "WATER_LEAKAGE", "SURFACE_FALLING")[global_index % 3]
                urgency = "HIGH" if global_index % 8 == 1 else "NORMAL"
                status = ("SUBMITTED", "PROCESSING", "CLOSED")[global_index % 3]
                sql.append(
                    f"""
INSERT INTO core.resident_report (
  report_code, community_id, building_id, reporter_user_id, report_type,
  description, status, urgency, evidence, submitted_at
)
SELECT {q(report_code)}, c.id, b.id, reporter.id, {q(report_type)},
       {q('居民反映建筑公共部位存在异常，已进入社区核查流程。')},
       {q(status)}, {q(urgency)},
       {jq([{'type': 'TEXT', 'note': '居民现场描述与社区回访记录'}])},
       {interval_expr(1 + global_index % 45)}
FROM core.community c
JOIN core.building b ON b.community_id=c.id AND b.deleted_at IS NULL
JOIN core.user_account reporter ON reporter.username='demo_inspector' AND reporter.deleted_at IS NULL
WHERE c.community_code={q(community_code)} AND b.building_code={q(building.code)} AND c.deleted_at IS NULL
ON CONFLICT (report_code) WHERE deleted_at IS NULL DO UPDATE SET
  community_id=EXCLUDED.community_id, building_id=EXCLUDED.building_id,
  reporter_user_id=EXCLUDED.reporter_user_id, report_type=EXCLUDED.report_type,
  description=EXCLUDED.description, status=EXCLUDED.status,
  urgency=EXCLUDED.urgency, evidence=EXCLUDED.evidence,
  submitted_at=EXCLUDED.submitted_at, updated_at=CURRENT_TIMESTAMP;
"""
                )

    sql.append("COMMIT;")
    return "\n".join(sql)


def main() -> None:
    communities = search_community_candidates()
    building_map: dict[str, list[BuildingRow]] = {}
    real_building_count = 0
    for index, community in enumerate(communities):
        enrich_aoi(community)
        real_candidates = building_candidates(community)
        real_building_count += len(real_candidates)
        building_map[community.poi_id] = build_rows(community, real_candidates, index)
        print(
            f"[{index + 1:02d}/{len(communities):02d}] {community.district} {community.name}: "
            f"高德楼栋POI={len(real_candidates)} / 业务楼栋档案={len(building_map[community.poi_id])}",
            flush=True,
        )

    sql = generate_sql(communities, building_map)
    SQL_FILE.write_text(sql, encoding="utf-8")

    all_buildings = [item for rows in building_map.values() for item in rows]
    center_lng = sum(item.lng for item in communities) / len(communities)
    center_lat = sum(item.lat for item in communities) / len(communities)
    meta = {
        "city": CITY,
        "seed": SEED,
        "centerLongitude": round(center_lng, 6),
        "centerLatitude": round(center_lat, 6),
        "communityCount": len(communities),
        "buildingCount": len(all_buildings),
        "realBuildingPoiCount": real_building_count,
        "districts": sorted({item.district for item in communities}),
        "communities": [
            {
                "code": code_for_poi(item.poi_id),
                "name": item.name,
                "district": item.district,
                "lng": item.lng,
                "lat": item.lat,
                "amapPoiId": item.poi_id,
                "amapAoiId": item.aoi_id,
                "amapAoiAreaSquareMeter": item.aoi_area,
                "buildingCount": len(building_map[item.poi_id]),
                "realBuildingPoiCount": sum(1 for row in building_map[item.poi_id] if row.real_poi_id),
            }
            for item in communities
        ],
    }
    META_FILE.write_text(json.dumps(meta, ensure_ascii=False, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
