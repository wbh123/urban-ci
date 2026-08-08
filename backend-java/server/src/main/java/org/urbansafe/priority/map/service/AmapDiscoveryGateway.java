package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.map.config.AmapProperties;

/**
 * 高德地图发现只读网关。
 *
 * <p>仅负责地图 Web 服务 HTTP 访问，不接触业务仓储；具体调用行为由后续测试驱动补齐。</p>
 */
@Component
public class AmapDiscoveryGateway {

    private final AmapProperties amap;

    public AmapDiscoveryGateway(AmapProperties amap) {
        this.amap = amap;
    }

    public JsonNode geocode(String address, String city) {
        return null;
    }

    public JsonNode searchPlaces(String keyword, String region, boolean cityLimit, int pageSize) {
        return null;
    }

    public JsonNode reverseGeocode(double longitude, double latitude) {
        return null;
    }
}
