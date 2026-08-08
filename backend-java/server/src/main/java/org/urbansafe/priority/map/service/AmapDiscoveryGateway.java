package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.urbansafe.priority.map.config.AmapProperties;

/**
 * 高德地图发现只读网关。
 *
 * <p>仅负责地图 Web 服务 HTTP 访问，不接触业务仓储；业务校验、Mock 降级和响应归一化由
 * {@link MapDiscoveryService} 负责。</p>
 */
@Component
public class AmapDiscoveryGateway {

    private static final String DEFAULT_BASE_URL = "https://restapi.amap.com";

    private final AmapProperties amap;
    private final RestClient client;

    public AmapDiscoveryGateway(AmapProperties amap) {
        this.amap = amap;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1, amap.getConnectTimeoutMs())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, amap.getReadTimeoutMs())));
        this.client = RestClient.builder()
                .requestFactory(requestFactory)
                .build();
    }

    public JsonNode geocode(String address, String city) {
        UriComponentsBuilder builder = uri("/v3/geocode/geo")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("address", address)
                .queryParamIfPresent("city", Optional.ofNullable(text(city)));
        return get(builder);
    }

    public JsonNode searchPlaces(String keyword, String region, boolean cityLimit, int pageSize) {
        UriComponentsBuilder builder = uri("/v5/place/text")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("keywords", keyword)
                .queryParamIfPresent("region", Optional.ofNullable(text(region)))
                .queryParam("city_limit", cityLimit)
                .queryParam("page_size", pageSize);
        return get(builder);
    }

    public JsonNode reverseGeocode(double longitude, double latitude) {
        UriComponentsBuilder builder = uri("/v3/geocode/regeo")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("location", longitude + "," + latitude)
                .queryParam("extensions", "all");
        return get(builder);
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(baseUrl()).path(path);
    }

    private JsonNode get(UriComponentsBuilder builder) {
        URI uri = builder.build().encode().toUri();
        return client.get().uri(uri).retrieve().body(JsonNode.class);
    }

    private String baseUrl() {
        String value = text(amap.getWebServiceBaseUrl());
        if (value == null) {
            return DEFAULT_BASE_URL;
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.isEmpty() ? DEFAULT_BASE_URL : value;
    }

    private String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
