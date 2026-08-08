package org.urbansafe.priority.map.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 可视化建档的地图发现服务。
 *
 * <p>当前骨架先锁定服务边界；地点搜索、正向/逆向地理编码行为由后续测试驱动补齐。</p>
 */
@Service
public class MapDiscoveryService {

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
