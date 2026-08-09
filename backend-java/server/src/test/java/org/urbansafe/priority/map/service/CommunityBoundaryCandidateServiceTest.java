package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

@ExtendWith(MockitoExtension.class)
class CommunityBoundaryCandidateServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AmapBoundaryCandidateGateway gateway;

    private AmapProperties amap;
    private MapProperties map;
    private CommunityBoundaryCandidateService service;

    @BeforeEach
    void setUp() {
        amap = new AmapProperties();
        map = new MapProperties();
        map.setEnabled(true);
        map.setAmap(amap);
        amap.setWebServiceKey("test-key");
        service = new CommunityBoundaryCandidateService(map, gateway);
    }

    @Test
    void defaultsToDisabledWithoutCallingAmap() {
        Map<String, Object> result = service.preview("示范小区", "示范路1号", "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("provider", "AMAP")
                .containsEntry("reasonCode", "DISABLED");
        verifyNoInteractions(gateway);
    }

    @Test
    void distinguishesEnabledButMissingWebServiceKey() {
        amap.setBoundaryCandidateEnabled(true);
        amap.setWebServiceKey("  ");

        Map<String, Object> result = service.preview("示范小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "NOT_CONFIGURED");
        verifyNoInteractions(gateway);
    }

    @Test
    void prefersExactPoiNameAndReturnsClosedGcj02Polygon() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[
                  {"id":"OTHER","name":"示范小区东门","address":"示范路2号"},
                  {"id":"B012345","name":"示范小区","address":"示范路1号"}
                ]}
                """));
        when(gateway.fetchAoi("B012345")).thenReturn(json("""
                {"status":"1","aois":[{"id":"B012345","name":"示范小区","address":"示范路1号","polyline":"113.100000,27.800000_113.110000,27.800000_113.110000,27.810000"}]}
                """));

        Map<String, Object> result = service.preview("示范小区", "示范路1号", "株洲市");

        assertThat(result)
                .containsEntry("available", true)
                .containsEntry("provider", "AMAP")
                .containsEntry("coordinateSystem", "GCJ02")
                .containsEntry("sourceType", "AMAP_AOI")
                .containsEntry("sourceId", "B012345")
                .containsEntry("name", "示范小区");
        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
        assertThat(geometry).containsEntry("type", "Polygon");
        assertThat(String.valueOf(geometry.get("coordinates")))
                .contains("113.1", "27.8", "113.11", "27.81");
        verify(gateway).fetchAoi("B012345");

        assertThat(Arrays.stream(CommunityBoundaryCandidateService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .filter(name -> name.contains("Repository")))
                .as("候选边界预览不得依赖 Repository 或隐式落库")
                .isEmpty();
    }

    @Test
    void supportsMultipleAoiRingsAsMultiPolygon() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("双区小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[{"id":"M1","name":"双区小区"}]}
                """));
        when(gateway.fetchAoi("M1")).thenReturn(json("""
                {"status":"1","aois":[{"id":"M1","name":"双区小区","polyline":"113.10,27.80_113.11,27.80_113.11,27.81|113.20,27.90_113.21,27.90_113.21,27.91"}]}
                """));

        Map<String, Object> result = service.preview("双区小区", null, "株洲市");

        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
        assertThat(result).containsEntry("available", true);
        assertThat(geometry).containsEntry("type", "MultiPolygon");
    }

    @Test
    void returnsNoResultWithoutCallingAoiWhenPoiSearchIsEmpty() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("不存在的小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[]}
                """));

        Map<String, Object> result = service.preview("不存在的小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "NO_RESULT");
        verify(gateway, never()).fetchAoi(anyString());
    }

    @Test
    void treatsMissingAoiPermissionAsNormalFallback() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[{"id":"B012345","name":"示范小区"}]}
                """));
        when(gateway.fetchAoi("B012345")).thenReturn(json("""
                {"status":"0","info":"NO_AOI_PERMISSION"}
                """));

        Map<String, Object> result = service.preview("示范小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "AOI_UNAVAILABLE");
    }

    @Test
    void separatesInvalidGeometryFromAoiAvailability() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[{"id":"B012345","name":"示范小区"}]}
                """));
        when(gateway.fetchAoi("B012345")).thenReturn(json("""
                {"status":"1","aois":[{"id":"B012345","name":"示范小区","polyline":"999,999_bad-point"}]}
                """));

        Map<String, Object> result = service.preview("示范小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "INVALID_GEOMETRY");
    }

    @Test
    void treatsTimeoutAsNormalFallbackInsteadOfThrowing() {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市"))
                .thenThrow(new ResourceAccessException("read timed out"));

        Map<String, Object> result = service.preview("示范小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "UPSTREAM_UNAVAILABLE");
    }

    private JsonNode json(String source) throws Exception {
        return objectMapper.readTree(source);
    }
}
