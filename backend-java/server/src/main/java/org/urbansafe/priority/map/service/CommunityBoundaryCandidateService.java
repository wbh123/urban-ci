package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.urbansafe.priority.common.exception.BusinessException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

/**
 * 小区高德候选边界服务。
 *
 * <p>候选结果只用于人工预览。该服务有意不依赖任何 Repository，真正落库仍必须走
 * R2 的版本化空间边界写接口。</p>
 */
@Service
public class CommunityBoundaryCandidateService {

    private final MapProperties map;
    private final AmapProperties amap;
    private final AmapBoundaryCandidateGateway gateway;

    public CommunityBoundaryCandidateService(
            MapProperties map,
            AmapBoundaryCandidateGateway gateway
    ) {
        this.map = map;
        this.amap = map.getAmap();
        this.gateway = gateway;
    }

    public Map<String, Object> preview(String communityName, String address, String city) {
        if (!map.isEnabled()
                || !amap.isBoundaryCandidateEnabled()
                || !StringUtils.hasText(amap.getWebServiceKey())) {
            return unavailable("DISABLED", "高德候选边界能力未启用，可继续手工绘制或导入 GeoJSON。");
        }

        String keyword = StringUtils.hasText(communityName) ? communityName.trim() : trimToNull(address);
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "MAP_BOUNDARY_CANDIDATE_QUERY_REQUIRED",
                    "小区名称或地址不能为空"
            );
        }

        try {
            JsonNode poiResponse = gateway.searchPoi(keyword, trimToNull(city));
            JsonNode poi = selectPoi(poiResponse, trimToNull(communityName));
            if (poi == null || !StringUtils.hasText(poi.path("id").asText())) {
                return unavailable("NO_RESULT", "高德未找到可用于边界查询的小区，可继续手工绘制或导入 GeoJSON。");
            }

            String poiId = poi.path("id").asText().trim();
            JsonNode aoiResponse = gateway.fetchAoi(poiId);
            JsonNode aoi = firstArrayItem(aoiResponse, "aois");
            String polyline = aoi == null ? null : trimToNull(aoi.path("polyline").asText(null));
            if (!StringUtils.hasText(polyline)) {
                return unavailable("AOI_UNAVAILABLE", "高德 AOI 边界不可用或当前 Key 无权限，可继续手工绘制或导入 GeoJSON。");
            }

            Map<String, Object> geometry = parseGeometry(polyline);
            if (geometry == null) {
                return unavailable("AOI_UNAVAILABLE", "高德返回的 AOI 边界无法解析，可继续手工绘制或导入 GeoJSON。");
            }

            LinkedHashMap<String, Object> result = baseResult(true);
            result.put("coordinateSystem", "GCJ02");
            result.put("sourceType", "AMAP_AOI");
            result.put("sourceId", poiId);
            result.put("name", firstText(aoi, "name", poi.path("name").asText(null), communityName));
            result.put("address", firstText(aoi, "address", poi.path("address").asText(null), address));
            result.put("geometry", geometry);
            result.put("message", "候选边界仅用于预览，保存前请人工确认。");
            return result;
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            return unavailable("UPSTREAM_UNAVAILABLE", "高德候选边界服务暂不可用，可继续手工绘制或导入 GeoJSON。");
        } catch (RuntimeException exception) {
            return unavailable("AOI_UNAVAILABLE", "高德候选边界解析失败，可继续手工绘制或导入 GeoJSON。");
        }
    }

    private JsonNode selectPoi(JsonNode response, String communityName) {
        JsonNode pois = response == null ? null : response.path("pois");
        if (pois == null || !pois.isArray() || pois.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(communityName)) {
            for (JsonNode poi : pois) {
                if (communityName.equalsIgnoreCase(poi.path("name").asText("").trim())) {
                    return poi;
                }
            }
        }
        return pois.get(0);
    }

    private JsonNode firstArrayItem(JsonNode response, String field) {
        JsonNode values = response == null ? null : response.path(field);
        return values != null && values.isArray() && !values.isEmpty() ? values.get(0) : null;
    }

    private Map<String, Object> parseGeometry(String polyline) {
        String[] polygonTexts = polyline.split("\\|");
        List<List<List<List<Double>>>> polygons = new ArrayList<>();
        for (String polygonText : polygonTexts) {
            List<List<Double>> ring = parseRing(polygonText);
            if (ring.size() >= 4) {
                polygons.add(List.of(ring));
            }
        }
        if (polygons.isEmpty()) {
            return null;
        }

        LinkedHashMap<String, Object> geometry = new LinkedHashMap<>();
        if (polygons.size() == 1) {
            geometry.put("type", "Polygon");
            geometry.put("coordinates", polygons.get(0));
        } else {
            geometry.put("type", "MultiPolygon");
            geometry.put("coordinates", polygons);
        }
        return geometry;
    }

    private List<List<Double>> parseRing(String polygonText) {
        List<List<Double>> ring = new ArrayList<>();
        if (!StringUtils.hasText(polygonText)) {
            return ring;
        }
        String[] points = polygonText.trim().split("[_;]");
        for (String point : points) {
            String[] parts = point.trim().split(",");
            if (parts.length < 2) {
                return List.of();
            }
            try {
                double longitude = Double.parseDouble(parts[0].trim());
                double latitude = Double.parseDouble(parts[1].trim());
                if (!Double.isFinite(longitude)
                        || !Double.isFinite(latitude)
                        || longitude < -180 || longitude > 180
                        || latitude < -90 || latitude > 90) {
                    return List.of();
                }
                ring.add(List.of(longitude, latitude));
            } catch (NumberFormatException exception) {
                return List.of();
            }
        }
        if (ring.size() < 3) {
            return List.of();
        }
        if (!ring.get(0).equals(ring.get(ring.size() - 1))) {
            ring.add(new ArrayList<>(ring.get(0)));
        }
        return ring;
    }

    private Map<String, Object> unavailable(String reasonCode, String message) {
        LinkedHashMap<String, Object> result = baseResult(false);
        result.put("reasonCode", reasonCode);
        result.put("message", message);
        return result;
    }

    private LinkedHashMap<String, Object> baseResult(boolean available) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("available", available);
        result.put("provider", "AMAP");
        return result;
    }

    private String firstText(JsonNode node, String field, String fallback, String secondFallback) {
        String value = node == null ? null : trimToNull(node.path(field).asText(null));
        if (value != null) {
            return value;
        }
        value = trimToNull(fallback);
        return value != null ? value : trimToNull(secondFallback);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
