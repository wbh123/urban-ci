package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;
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
    void defaultsToDisabledAndDoesNotCallAmap() {
        assertThat(amap.isBoundaryCandidateEnabled()).isFalse();

        Map<String, Object> result = service.preview("示范小区", "湖南省株洲市示范路1号", "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("provider", "AMAP")
                .containsEntry("reasonCode", "DISABLED");
        verifyNoInteractions(gateway);
    }

    @Test
    void returnsGcj02PolygonCandidateFromPoiAndAoiWithoutPersisting() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[{"id":"B012345","name":"示范小区","address":"示范路1号"}]}
                """));
        when(gateway.fetchAoi("B012345")).thenReturn(json("""
                {"status":"0","info":"ok","aois":[{"id":"B012345","name":"示范小区","address":"示范路1号","polyline":"113.100000,27.800000_113.110000,27.800000_113.110000,27.810000"}]}
                """));

        Map<String, Object> result = service.preview("示范小区", "湖南省株洲市示范路1号", "株洲市");

        assertThat(result)
                .containsEntry("available", true)
                .containsEntry("provider", "AMAP")
                .containsEntry("coordinateSystem", "GCJ02")
                .containsEntry("sourceType", "AMAP_AOI")
                .containsEntry("sourceId", "B012345")
                .containsEntry("name", "示范小区");
        assertThat(result.get("geometry")).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> geometry = (Map<String, Object>) result.get("geometry");
        assertThat(geometry).containsEntry("type", "Polygon");
        assertThat(String.valueOf(geometry.get("coordinates"))).contains("113.1", "27.8", "113.11", "27.81");

        assertThat(Arrays.stream(CommunityBoundaryCandidateService.class.getDeclaredFields())
                .map(field -> field.getType().getSimpleName())
                .filter(name -> name.contains("Repository")))
                .as("候选边界服务不能依赖任何 Repository，避免预览时隐式落库")
                .isEmpty();
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
        verify(gateway, never()).fetchAoi(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void treatsMissingAoiPermissionAsNormalFallback() throws Exception {
        amap.setBoundaryCandidateEnabled(true);
        when(gateway.searchPoi("示范小区", "株洲市")).thenReturn(json("""
                {"status":"1","pois":[{"id":"B012345","name":"示范小区"}]}
                """));
        when(gateway.fetchAoi("B012345")).thenReturn(json("""
                {"status":"0","info":"NO_AOI_PERMISSION","aois":[]}
                """));

        Map<String, Object> result = service.preview("示范小区", null, "株洲市");

        assertThat(result)
                .containsEntry("available", false)
                .containsEntry("reasonCode", "AOI_UNAVAILABLE");
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
