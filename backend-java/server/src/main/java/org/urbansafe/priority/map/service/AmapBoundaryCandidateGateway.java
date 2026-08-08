package org.urbansafe.priority.map.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.urbansafe.priority.map.config.AmapProperties;

/**
 * 高德候选边界只读网关。
 *
 * <p>仅负责 POI/AOI HTTP 查询，不接触任何业务仓储，也不执行空间边界持久化。</p>
 */
@Component
public class AmapBoundaryCandidateGateway {

    private final AmapProperties amap;
    private final RestClient restClient;

    public AmapBoundaryCandidateGateway(AmapProperties amap) {
        this.amap = amap;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(Math.max(1, amap.getConnectTimeoutMs())));
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(1, amap.getReadTimeoutMs())));
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
    }

    public JsonNode searchPoi(String keyword, String city) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl() + "/v5/place/text")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("keywords", keyword)
                .queryParam("page_size", 5)
                .queryParam("show_fields", "business");
        if (StringUtils.hasText(city)) {
            builder.queryParam("region", city.trim());
            builder.queryParam("city_limit", true);
        }
        return get(builder);
    }

    public JsonNode fetchAoi(String poiId) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(baseUrl() + "/v5/aoi/polyline")
                .queryParam("key", amap.getWebServiceKey())
                .queryParam("id", poiId);
        return get(builder);
    }

    private JsonNode get(UriComponentsBuilder builder) {
        return restClient.get()
                .uri(builder.build(true).toUri())
                .retrieve()
                .body(JsonNode.class);
    }

    private String baseUrl() {
        String value = amap.getWebServiceBaseUrl();
        if (!StringUtils.hasText(value)) {
            return "https://restapi.amap.com";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
