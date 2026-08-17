#!/usr/bin/env python3
from __future__ import annotations

import json
from pathlib import Path
from typing import Any, Iterable

# “小区”先拿稳定的住宅主样本；“宿舍”作为第二关键词专门补充老单位住宅。
# competition 默认每区预留 12 个候选，因此通常会进入第二关键词，但不会逐个公开小区精确查询。
DEFAULT_KEYWORDS = ("小区", "宿舍", "花园", "家园", "公寓", "社区", "苑")

# 武汉七个中心城区行政区划代码。高德地点搜索 2.0 的 region 在区级场景优先使用 adcode；
# 旧版中文区名请求继续保留用于命中已有缓存，仅在真实配额不足时定向补查 adcode。
WUHAN_DISTRICT_ADCODES = {
    "江岸区": "420102",
    "江汉区": "420103",
    "硚口区": "420104",
    "汉阳区": "420105",
    "武昌区": "420106",
    "青山区": "420107",
    "洪山区": "420111",
}


def normalize_name(value: str) -> str:
    text = "".join(str(value or "").split())
    for suffix in ("住宅小区", "小区"):
        if text.endswith(suffix) and len(text) > len(suffix) + 1:
            text = text[: -len(suffix)]
            break
    return text


def load_public_catalog(path: Path) -> tuple[set[str], dict[str, list[dict[str, Any]]]]:
    if not path.exists():
        return set(), {}
    raw = json.loads(path.read_text(encoding="utf-8"))
    entries = raw.get("entries") or []
    preferred: set[str] = set()
    by_name: dict[str, list[dict[str, Any]]] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        name = str(entry.get("name") or "").strip()
        if not name:
            continue
        key = normalize_name(name)
        preferred.add(key)
        by_name.setdefault(key, []).append(entry)
    return preferred, by_name


def _sort_key(item: Any, preferred_names: set[str]) -> tuple[int, str, str]:
    normalized = normalize_name(getattr(item, "name", ""))
    return (
        0 if normalized in preferred_names else 1,
        normalized,
        str(getattr(item, "poi_id", "")),
    )


def select_balanced_candidates(
    buckets: dict[str, list[Any]],
    target: int,
    districts: list[str],
    preferred_names: set[str],
) -> list[Any]:
    if target <= 0 or not districts:
        return []
    base = target // len(districts)
    remainder = target % len(districts)
    quotas = {
        district: base + (1 if index < remainder else 0)
        for index, district in enumerate(districts)
    }
    selected: list[Any] = []
    used_ids: set[str] = set()
    leftovers: list[Any] = []
    for district in districts:
        rows = sorted(buckets.get(district, []), key=lambda item: _sort_key(item, preferred_names))
        quota = quotas[district]
        for item in rows[:quota]:
            poi_id = str(getattr(item, "poi_id", ""))
            if poi_id and poi_id not in used_ids:
                selected.append(item)
                used_ids.add(poi_id)
        leftovers.extend(rows[quota:])

    if len(selected) < target:
        for item in sorted(leftovers, key=lambda row: _sort_key(row, preferred_names)):
            poi_id = str(getattr(item, "poi_id", ""))
            if not poi_id or poi_id in used_ids:
                continue
            selected.append(item)
            used_ids.add(poi_id)
            if len(selected) >= target:
                break
    return selected[:target]


def quota_map(target: int, districts: list[str]) -> dict[str, int]:
    if not districts:
        return {}
    base = target // len(districts)
    remainder = target % len(districts)
    return {
        district: base + (1 if index < remainder else 0)
        for index, district in enumerate(districts)
    }


def resolve_actual_district(raw: dict[str, Any], districts: list[str]) -> str | None:
    """Resolve a POI to one competition district, preferring the returned adcode.

    Functional-zone names may differ from the municipal district name, while the
    POI adcode still identifies the underlying administrative district. Using
    adcode first keeps the stored district consistent with the real administrative
    division and avoids dropping valid Qingshan POIs labelled as a functional zone.
    """

    adcode = str(raw.get("adcode") or "").strip()
    for district in districts:
        expected = WUHAN_DISTRICT_ADCODES.get(district)
        if expected and adcode == expected:
            return district

    adname = str(raw.get("adname") or "").strip()
    if adname in districts:
        return adname
    return None


def search_community_candidates(
    generator: Any,
    catalog_path: Path,
    reserve_per_district: int = 12,
    keywords: Iterable[str] = DEFAULT_KEYWORDS,
) -> list[Any]:
    """Search Wuhan residential POIs while minimizing AMap calls.

    First reuse legacy district-name requests so existing local cache remains
    valuable. POIs are always bucketed by their returned real administrative
    district (adcode first, adname second). If any target district still cannot
    meet its quota, only that district is queried again with its official adcode.
    This keeps API usage low while making district coverage deterministic.
    """

    preferred_names, _ = load_public_catalog(catalog_path)
    districts = list(generator.DISTRICTS)
    target = int(generator.TARGET_COMMUNITIES)
    quotas = quota_map(target, districts)
    buckets: dict[str, dict[str, Any]] = {district: {} for district in districts}
    cross_district_routed = 0
    skipped_unknown_district = 0
    adcode_fallback_districts: list[str] = []

    def ingest_rows(rows: list[Any], requested_district: str) -> None:
        nonlocal cross_district_routed, skipped_unknown_district
        for raw in rows:
            if not isinstance(raw, dict) or not generator.is_residential(raw):
                continue
            location = generator.parse_location(raw.get("location"))
            if not location:
                continue
            poi_id = str(raw.get("id") or "").strip()
            if not poi_id:
                continue
            cityname = str(raw.get("cityname") or generator.CITY).strip()
            adcode = str(raw.get("adcode") or "").strip()
            if generator.CITY not in cityname and not adcode.startswith("4201"):
                continue

            actual_district = resolve_actual_district(raw, districts)
            if actual_district is None:
                skipped_unknown_district += 1
                continue
            if actual_district != requested_district:
                cross_district_routed += 1

            lng, lat = location
            buckets[actual_district][poi_id] = generator.Poi(
                poi_id=poi_id,
                name=str(raw.get("name") or "住宅小区").strip(),
                lng=lng,
                lat=lat,
                address=str(raw.get("address") or "").strip(),
                district=actual_district,
                adcode=adcode,
                poi_type=str(raw.get("type") or "").strip(),
                typecode=str(raw.get("typecode") or "").strip(),
                children=[item for item in (raw.get("children") or []) if isinstance(item, dict)],
            )

    # 第一阶段：继续使用中文区名请求，最大化复用已经积累的本地高德缓存。
    for requested_district in districts:
        desired = quotas[requested_district] + max(0, reserve_per_district)
        for keyword in keywords:
            if len(buckets[requested_district]) >= desired:
                break
            for page in range(1, int(generator.MAX_POI_PAGES) + 1):
                if len(buckets[requested_district]) >= desired:
                    break
                payload = generator.request(
                    "/v5/place/text",
                    {
                        "keywords": keyword,
                        "types": "120000",
                        "region": requested_district,
                        "city_limit": "true",
                        "show_fields": "children,navi",
                        "page_size": 25,
                        "page_num": page,
                    },
                )
                rows = payload.get("pois") or []
                ingest_rows(rows, requested_district)
                if len(rows) < 25:
                    break

    # 第二阶段：只对真实行政区配额仍不足的城区使用 adcode 定向补查。
    # 每区最多多取 3 个候选作为本地选择余量，通常第一页即可结束。
    for requested_district in districts:
        if len(buckets[requested_district]) >= quotas[requested_district]:
            continue
        district_adcode = WUHAN_DISTRICT_ADCODES.get(requested_district)
        if not district_adcode:
            continue
        adcode_fallback_districts.append(requested_district)
        desired = quotas[requested_district] + min(3, max(0, reserve_per_district))
        for keyword in keywords:
            if len(buckets[requested_district]) >= desired:
                break
            for page in range(1, int(generator.MAX_POI_PAGES) + 1):
                if len(buckets[requested_district]) >= desired:
                    break
                payload = generator.request(
                    "/v5/place/text",
                    {
                        "keywords": keyword,
                        "types": "120000",
                        "region": district_adcode,
                        "city_limit": "true",
                        "show_fields": "children,navi",
                        "page_size": 25,
                        "page_num": page,
                    },
                )
                rows = payload.get("pois") or []
                ingest_rows(rows, requested_district)
                if len(rows) < 25:
                    break

    flat_buckets: dict[str, list[Any]] = {
        district: list(rows.values()) for district, rows in buckets.items()
    }

    # 比赛档必须真正覆盖七个城区。不能用其他城区的剩余 POI 把总量凑到 100 后再等最终闸门失败。
    deficits = {
        district: (len(flat_buckets[district]), quotas[district])
        for district in districts
        if len(flat_buckets[district]) < quotas[district]
    }
    if deficits:
        deficit_text = ", ".join(
            f"{district}={actual}/{required}"
            for district, (actual, required) in deficits.items()
        )
        counts = ", ".join(f"{d}={len(flat_buckets[d])}" for d in districts)
        fallback_text = ",".join(adcode_fallback_districts) or "无"
        raise RuntimeError(
            "武汉中心城区真实行政区候选不足："
            f"{deficit_text}。全部候选：{counts}。"
            f"已执行 adcode 定向补查：{fallback_text}。"
            "可将 SHOWCASE_MAX_POI_PAGES 提高 1 后使用 cache-mode=prefer 重试；"
            "已有请求继续命中缓存，仅缺失查询会访问高德。"
        )

    selected = select_balanced_candidates(flat_buckets, target, districts, preferred_names)
    if len(selected) < target:
        counts = ", ".join(f"{d}={len(flat_buckets[d])}" for d in districts)
        raise RuntimeError(
            f"武汉住宅 POI 数量不足：目标 {target}，仅获得 {len(selected)}。各区候选：{counts}。"
            "可将 SHOWCASE_MAX_POI_PAGES 从当前值提高 1 后重试；已命中的请求会直接读取缓存。"
        )

    # 最后再按归一化后的真实行政区自检一次，防止以后选择逻辑回归造成行政区丢失。
    selected_counts = {
        district: sum(1 for item in selected if str(getattr(item, "district", "")) == district)
        for district in districts
    }
    selected_deficits = {
        district: (selected_counts[district], quotas[district])
        for district in districts
        if selected_counts[district] < quotas[district]
    }
    if selected_deficits:
        detail = ", ".join(
            f"{district}={actual}/{required}"
            for district, (actual, required) in selected_deficits.items()
        )
        raise RuntimeError(f"武汉候选最终行政区配额校验失败：{detail}")

    public_count = sum(1 for item in selected if normalize_name(item.name) in preferred_names)
    distribution = ", ".join(f"{d}={selected_counts[d]}" for d in districts)
    fallback_text = ",".join(adcode_fallback_districts) or "无"
    print(
        f"武汉候选选择完成：target={target} selected={len(selected)} "
        f"publicCatalogMatched={public_count} reservePerDistrict={reserve_per_district} "
        f"crossDistrictRouted={cross_district_routed} skippedUnknownDistrict={skipped_unknown_district} "
        f"adcodeFallback=[{fallback_text}] districts=[{distribution}]",
        flush=True,
    )
    return selected
