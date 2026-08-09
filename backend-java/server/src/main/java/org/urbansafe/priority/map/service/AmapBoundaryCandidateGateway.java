package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.urbansafe.priority.map.config.AmapProperties;

/**
 * 高德候选边界只读网关。
 *
 * <p>只负责 POI 与 AOI Web Service 请求，不访问业务仓储，也不执行任何正式边界写入。</p>
 */
@Component
public class AmapBoundaryCandidateGateway {

    private static final String DEFAULT_BASE_URL = "https://restapi.amap.com";

    private final AmapProperties amap;
    private final RestClient client;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AmapBoundaryCandidateGateway(AmapProperties amap) {
        this.amap = amap;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1, amap.getConnectTimeoutMs())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, amap.getReadTimeoutMs())));
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    public JsonNode searchPoi(String keyword, String region) {
        UriComponentsBuilder builder = uri("/v5/place/text")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("keywords", keyword)
                .queryParamIfPresent("region", Optional.ofNullable(text(region)))
                .queryParam("page_size", 5)
                .queryParam("show_fields", "business");
        return get(builder);
    }

    public JsonNode fetchAoi(String poiId) {
        UriComponentsBuilder builder = uri("/v5/aoi/polyline")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("id", poiId);
        return get(builder);
    }

    private UriComponentsBuilder uri(String path) {
        return UriComponentsBuilder.fromUriString(baseUrl()).path(path);
    }

    private JsonNode get(UriComponentsBuilder builder) {
        URI uri = builder.build().encode().toUri();
        String body = client.get().uri(uri).retrieve().body(String.class);
        if (body == null || body.isBlank()) return null;
        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("高德候选边界服务返回了无法解析的 JSON", ex);
        }
    }

    private String baseUrl() {
        String value = text(amap.getWebServiceBaseUrl());
        if (value == null) return DEFAULT_BASE_URL;
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value.isEmpty() ? DEFAULT_BASE_URL : value;
    }

    private String text(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
