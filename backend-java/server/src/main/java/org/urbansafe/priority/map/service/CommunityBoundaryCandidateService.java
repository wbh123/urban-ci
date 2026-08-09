package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.urbansafe.priority.map.config.MapProperties;

/**
 * 小区高德候选边界服务。
 *
 * <p>该服务只返回候选几何，不依赖 Repository，不执行落库；正式保存继续由 spatial 子系统完成。</p>
 */
@Service
public class CommunityBoundaryCandidateService {

    private final MapProperties map;
    private final AmapBoundaryCandidateGateway gateway;

    public CommunityBoundaryCandidateService(MapProperties map, AmapBoundaryCandidateGateway gateway) {
        this.map = map;
        this.gateway = gateway;
    }

    public Map<String, Object> preview(String communityName, String address, String region) {
        if (!map.isEnabled() || !map.getAmap().isBoundaryCandidateEnabled()) {
            return unavailable("DISABLED", "高德候选边界功能当前未启用，可继续使用手工绘制或 GeoJSON 导入。");
        }
        if (blank(map.getAmap().getWebServiceKey())) {
            return unavailable("NOT_CONFIGURED", "高德候选边界已启用，但 Web Service 密钥尚未配置。");
        }
        if (blank(communityName)) {
            return unavailable("NO_RESULT", "缺少可用于检索的小区名称。");
        }

        try {
            JsonNode poiResponse = gateway.searchPoi(communityName.trim(), text(region));
            JsonNode poi = selectPoi(poiResponse, communityName.trim());
            if (poi == null) {
                return unavailable("NO_RESULT", "高德未找到可用于边界查询的匹配小区。");
            }
            String poiId = text(poi.path("id").asText(null));
            if (poiId == null) {
                return unavailable("NO_RESULT", "高德地点候选缺少可用于区域查询的标识。");
            }

            JsonNode aoiResponse = gateway.fetchAoi(poiId);
            JsonNode aoi = firstAoi(aoiResponse);
            if (aoi == null) {
                return unavailable("AOI_UNAVAILABLE", "高德未返回可用的区域边界，可继续手工绘制或导入 GeoJSON。");
            }
            String polyline = text(aoi.path("polyline").asText(null));
            if (polyline == null) {
                return unavailable("AOI_UNAVAILABLE", "高德地点存在，但当前账号或数据未提供可用 AOI 边界。");
            }

            Map<String, Object> geometry = parseGeometry(polyline);
            if (geometry == null) {
                return unavailable("INVALID_GEOMETRY", "高德返回的候选边界无法转换为有效 GeoJSON，请改用人工绘制或导入。");
            }

            Map<String, Object> result = base(true, null,
                    "已取得高德候选边界，请人工核对后再采用；采用后仍需通过正式空间边界保存与确认流程。");
            result.put("coordinateSystem", "GCJ02");
            result.put("sourceType", "AMAP_AOI");
            result.put("sourceId", poiId);
            result.put("name", firstText(aoi.path("name"), poi.path("name"), communityName));
            result.put("address", firstText(aoi.path("address"), poi.path("address"), address));
            result.put("geometry", geometry);
            return result;
        } catch (RestClientException ex) {
            return unavailable("UPSTREAM_UNAVAILABLE", "高德候选边界服务暂时不可用，可继续使用人工绘制或 GeoJSON 导入。");
        } catch (RuntimeException ex) {
            return unavailable("UPSTREAM_UNAVAILABLE", "高德候选边界响应处理失败，可继续使用人工绘制或 GeoJSON 导入。");
        }
    }

    private JsonNode selectPoi(JsonNode response, String communityName) {
        JsonNode pois = response == null ? null : response.path("pois");
        if (pois == null || !pois.isArray() || pois.isEmpty()) return null;
        for (JsonNode poi : pois) {
            if (communityName.equalsIgnoreCase(text(poi.path("name").asText(null)))) return poi;
        }
        return pois.get(0);
    }

    private JsonNode firstAoi(JsonNode response) {
        if (response == null) return null;
        JsonNode aois = response.path("aois");
        if (aois.isArray()) return aois.isEmpty() ? null : aois.get(0);
        if (aois.isObject() && !aois.isEmpty()) return aois;
        return null;
    }

    private Map<String, Object> parseGeometry(String polyline) {
        String[] polygonSources = polyline.split("\\|");
        List<List<List<List<Double>>>> polygons = new ArrayList<>();
        for (String polygonSource : polygonSources) {
            List<List<Double>> ring = parseRing(polygonSource);
            if (ring != null) polygons.add(List.of(ring));
        }
        if (polygons.isEmpty()) return null;

        Map<String, Object> geometry = new LinkedHashMap<>();
        if (polygons.size() == 1) {
            geometry.put("type", "Polygon");
            geometry.put("coordinates", polygons.get(0));
        } else {
            geometry.put("type", "MultiPolygon");
            geometry.put("coordinates", polygons);
        }
        return geometry;
    }

    private List<List<Double>> parseRing(String source) {
        if (blank(source)) return null;
        List<List<Double>> ring = new ArrayList<>();
        for (String rawPoint : source.trim().split("[_;]")) {
            String[] parts = rawPoint.trim().split(",");
            if (parts.length != 2) return null;
            try {
                double longitude = Double.parseDouble(parts[0].trim());
                double latitude = Double.parseDouble(parts[1].trim());
                if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                        || longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                    return null;
                }
                ring.add(List.of(longitude, latitude));
            } catch (NumberFormatException ex) {
                return null;
            }
        }
        if (ring.size() < 3) return null;
        if (!ring.get(0).equals(ring.get(ring.size() - 1))) ring.add(new ArrayList<>(ring.get(0)));
        return ring.size() >= 4 ? ring : null;
    }

    private Map<String, Object> unavailable(String reasonCode, String message) {
        return base(false, reasonCode, message);
    }

    private Map<String, Object> base(boolean available, String reasonCode, String message) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("available", available);
        result.put("provider", "AMAP");
        if (reasonCode != null) result.put("reasonCode", reasonCode);
        result.put("message", message);
        return result;
    }

    private String firstText(JsonNode first, JsonNode second, String fallback) {
        String firstValue = first == null ? null : text(first.asText(null));
        if (firstValue != null) return firstValue;
        String secondValue = second == null ? null : text(second.asText(null));
        return secondValue != null ? secondValue : text(fallback);
    }

    private String text(String value) {
        return blank(value) ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
