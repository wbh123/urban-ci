package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

/**
 * 可视化建档的地图发现服务。
 *
 * <p>负责 Mock/Real 模式选择与结果归一化；第三方 HTTP 访问统一交给 {@link AmapDiscoveryGateway}。</p>
 */
@Service
public class MapDiscoveryService {

    private final MapProperties map;
    private final AmapProperties amap;
    private final AmapDiscoveryGateway gateway;

    public MapDiscoveryService(
            MapProperties map,
            AmapProperties amap,
            AmapDiscoveryGateway gateway) {
        this.map = map;
        this.amap = amap;
        this.gateway = gateway;
    }

    public Map<String, Object> geocode(String address, String city) {
        String normalizedAddress = requiredText(
                address, "MAP_ADDRESS_REQUIRED", "地址不能为空");
        String normalizedCity = text(city);
        if (!liveDiscoveryEnabled()) {
            int hash = Math.abs(normalizedAddress.hashCode());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("formattedAddress", normalizedAddress);
            result.put("longitude",
                    map.getDefaultCenterLongitude() + ((hash % 1000) - 500) / 100000.0);
            result.put("latitude",
                    map.getDefaultCenterLatitude() + (((hash / 1000) % 1000) - 500) / 100000.0);
            result.put("provider", "MOCK");
            result.put("matchLevel", "MOCK_PREVIEW");
            result.put("mock", true);
            return result;
        }

        JsonNode body = gateway.geocode(normalizedAddress, normalizedCity);
        assertUpstreamSuccess(body);
        JsonNode item = first(body.path("geocodes"));
        double[] point = parseLocation(item == null ? null : text(item.path("location")));
        if (item == null || point == null) {
            throw new InvalidRequestException("MAP_GEOCODE_NO_RESULT", "未找到可用坐标");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formattedAddress", fallback(text(item.path("formatted_address")), normalizedAddress));
        result.put("longitude", point[0]);
        result.put("latitude", point[1]);
        result.put("provider", "AMAP");
        result.put("matchLevel", fallback(text(item.path("level")), "UNKNOWN"));
        result.put("mock", false);
        return result;
    }

    public List<Map<String, Object>> searchPlaces(
            String keyword, String region, boolean cityLimit, int pageSize) {
        String normalizedKeyword = requiredText(
                keyword, "MAP_PLACE_KEYWORD_REQUIRED", "地点搜索关键字不能为空");
        if (pageSize < 1 || pageSize > 10) {
            throw new InvalidRequestException(
                    "MAP_PLACE_PAGE_SIZE_INVALID", "地点搜索每页数量必须在 1 到 10 之间");
        }
        String normalizedRegion = text(region);
        if (!liveDiscoveryEnabled()) {
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("providerObjectId", null);
            candidate.put("name", normalizedKeyword);
            candidate.put("formattedAddress",
                    normalizedRegion == null ? normalizedKeyword : normalizedRegion + normalizedKeyword);
            candidate.put("province", null);
            candidate.put("city", normalizedRegion);
            candidate.put("district", null);
            candidate.put("adcode", null);
            candidate.put("citycode", null);
            candidate.put("longitude", map.getDefaultCenterLongitude());
            candidate.put("latitude", map.getDefaultCenterLatitude());
            candidate.put("provider", "MOCK");
            candidate.put("coordinateSystem", "UNKNOWN");
            candidate.put("mock", true);
            return List.of(candidate);
        }

        JsonNode body = gateway.searchPlaces(
                normalizedKeyword, normalizedRegion, cityLimit, pageSize);
        assertUpstreamSuccess(body);
        JsonNode pois = body.path("pois");
        if (!pois.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode poi : pois) {
            double[] point = parseLocation(text(poi.path("location")));
            String name = text(poi.path("name"));
            if (point == null || name == null) {
                continue;
            }
            String province = text(poi.path("pname"));
            String city = text(poi.path("cityname"));
            String district = text(poi.path("adname"));
            String address = text(poi.path("address"));
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("providerObjectId", text(poi.path("id")));
            candidate.put("name", name);
            candidate.put("formattedAddress", joinAddress(province, city, district, address));
            candidate.put("province", province);
            candidate.put("city", city);
            candidate.put("district", district);
            candidate.put("adcode", text(poi.path("adcode")));
            candidate.put("citycode", text(poi.path("citycode")));
            candidate.put("longitude", point[0]);
            candidate.put("latitude", point[1]);
            candidate.put("provider", "AMAP");
            candidate.put("coordinateSystem", "GCJ02");
            candidate.put("mock", false);
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    public Map<String, Object> reverseGeocode(double longitude, double latitude) {
        validateCoordinates(longitude, latitude);
        if (!liveDiscoveryEnabled()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("formattedAddress", null);
            result.put("province", null);
            result.put("city", null);
            result.put("district", null);
            result.put("adcode", null);
            result.put("citycode", null);
            result.put("longitude", longitude);
            result.put("latitude", latitude);
            result.put("provider", "MOCK");
            result.put("coordinateSystem", "UNKNOWN");
            result.put("nearestPoiId", null);
            result.put("nearestPoiName", null);
            result.put("mock", true);
            return result;
        }

        JsonNode body = gateway.reverseGeocode(longitude, latitude);
        assertUpstreamSuccess(body);
        JsonNode regeocode = body.path("regeocode");
        JsonNode component = regeocode.path("addressComponent");
        JsonNode nearestPoi = first(regeocode.path("pois"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("formattedAddress", text(regeocode.path("formatted_address")));
        result.put("province", text(component.path("province")));
        result.put("city", text(component.path("city")));
        result.put("district", text(component.path("district")));
        result.put("adcode", text(component.path("adcode")));
        result.put("citycode", text(component.path("citycode")));
        result.put("longitude", longitude);
        result.put("latitude", latitude);
        result.put("provider", "AMAP");
        result.put("coordinateSystem", "GCJ02");
        result.put("nearestPoiId", nearestPoi == null ? null : text(nearestPoi.path("id")));
        result.put("nearestPoiName", nearestPoi == null ? null : text(nearestPoi.path("name")));
        result.put("mock", false);
        return result;
    }

    private boolean liveDiscoveryEnabled() {
        return map.isEnabled() && text(amap.getWebServiceKey()) != null;
    }

    private void assertUpstreamSuccess(JsonNode body) {
        if (body == null || !"1".equals(text(body.path("status")))) {
            throw new InvalidRequestException(
                    "MAP_UPSTREAM_ERROR", "地图服务暂时不可用，请稍后重试或改用手工录入");
        }
    }

    private void validateCoordinates(double longitude, double latitude) {
        if (!Double.isFinite(longitude) || !Double.isFinite(latitude)
                || longitude < -180 || longitude > 180
                || latitude < -90 || latitude > 90) {
            throw new InvalidRequestException("MAP_COORDINATE_INVALID", "经纬度超出有效范围");
        }
    }

    private String requiredText(String value, String code, String message) {
        String normalized = text(value);
        if (normalized == null) {
            throw new InvalidRequestException(code, message);
        }
        return normalized;
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof JsonNode node) {
            if (node.isMissingNode() || node.isNull()) {
                return null;
            }
            if (node.isTextual() || node.isNumber() || node.isBoolean()) {
                return text(node.asText());
            }
            if (node.isArray()) {
                for (JsonNode item : node) {
                    String candidate = text(item);
                    if (candidate != null) {
                        return candidate;
                    }
                }
                return null;
            }
            return null;
        }
        String normalized = String.valueOf(value).trim();
        return normalized.isEmpty() || "[]".equals(normalized) ? null : normalized;
    }

    private JsonNode first(JsonNode value) {
        return value != null && value.isArray() && !value.isEmpty() ? value.get(0) : null;
    }

    private double[] parseLocation(String location) {
        if (location == null) {
            return null;
        }
        String[] parts = location.split(",", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            double longitude = Double.parseDouble(parts[0].trim());
            double latitude = Double.parseDouble(parts[1].trim());
            if (!Double.isFinite(longitude) || !Double.isFinite(latitude)) {
                return null;
            }
            return new double[]{longitude, latitude};
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String joinAddress(String... parts) {
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            String normalized = text(part);
            if (normalized == null) {
                continue;
            }
            if (builder.toString().endsWith(normalized)) {
                continue;
            }
            builder.append(normalized);
        }
        return builder.length() == 0 ? null : builder.toString();
    }

    private String fallback(String value, String fallback) {
        return value == null ? fallback : value;
    }
}
