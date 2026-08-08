package org.urbansafe.priority.map.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
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
        return Map.of();
    }

    public List<Map<String, Object>> searchPlaces(
            String keyword, String region, boolean cityLimit, int pageSize) {
        return List.of();
    }

    public Map<String, Object> reverseGeocode(double longitude, double latitude) {
        return Map.of();
    }
}
