package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.map.config.AmapProperties;

/** 高德发现网关必须只读调用官方 Web 服务端点，并从统一配置获得密钥与超时。 */
class AmapDiscoveryGatewayTest {

    private HttpServer server;
    private AtomicReference<URI> requestUri;
    private AmapDiscoveryGateway gateway;

    @BeforeEach
    void setUp() throws IOException {
        requestUri = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestUri.set(exchange.getRequestURI());
            byte[] body = "{\"status\":\"1\",\"info\":\"OK\"}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) {
                output.write(body);
            }
        });
        server.start();

        AmapProperties amap = new AmapProperties();
        amap.setWebServiceKey("test-key");
        amap.setWebServiceBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        amap.setConnectTimeoutMs(1000);
        amap.setReadTimeoutMs(1000);
        gateway = new AmapDiscoveryGateway(amap);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void geocodeUsesOfficialV3EndpointAndParameters() {
        JsonNode response = gateway.geocode("示范路1号", "株洲市");

        URI uri = requireRequestUri();
        assertThat(uri.getPath()).isEqualTo("/v3/geocode/geo");
        assertThat(query(uri))
                .containsEntry("key", "test-key")
                .containsEntry("address", "示范路1号")
                .containsEntry("city", "株洲市");
        assertThat(response.path("status").asText()).isEqualTo("1");
    }

    @Test
    void placeSearchUsesOfficialV5EndpointAndParameters() {
        JsonNode response = gateway.searchPlaces("示范小区", "株洲市", true, 8);

        URI uri = requireRequestUri();
        assertThat(uri.getPath()).isEqualTo("/v5/place/text");
        assertThat(query(uri))
                .containsEntry("key", "test-key")
                .containsEntry("keywords", "示范小区")
                .containsEntry("region", "株洲市")
                .containsEntry("city_limit", "true")
                .containsEntry("page_size", "8");
        assertThat(response.path("status").asText()).isEqualTo("1");
    }

    @Test
    void reverseGeocodeUsesOfficialV3EndpointAndParameters() {
        JsonNode response = gateway.reverseGeocode(113.12, 27.88);

        URI uri = requireRequestUri();
        assertThat(uri.getPath()).isEqualTo("/v3/geocode/regeo");
        assertThat(query(uri))
                .containsEntry("key", "test-key")
                .containsEntry("location", "113.12,27.88")
                .containsEntry("extensions", "all");
        assertThat(response.path("status").asText()).isEqualTo("1");
    }

    private URI requireRequestUri() {
        assertThat(requestUri.get()).as("网关应向配置的高德服务地址发出请求").isNotNull();
        return requestUri.get();
    }

    private Map<String, String> query(URI uri) {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = uri.getRawQuery();
        if (raw == null || raw.isBlank()) {
            return result;
        }
        Arrays.stream(raw.split("&"))
                .map(item -> item.split("=", 2))
                .forEach(parts -> result.put(
                        decode(parts[0]),
                        parts.length == 2 ? decode(parts[1]) : ""));
        return result;
    }

    private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
