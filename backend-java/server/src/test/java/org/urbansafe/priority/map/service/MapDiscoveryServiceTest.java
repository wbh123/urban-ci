package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

/** 可视化建档的地图发现能力必须由独立服务与只读高德网关承载。 */
class MapDiscoveryServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesStandaloneDiscoveryServiceContract() throws Exception {
        Class<?> serviceType = load("org.urbansafe.priority.map.service.MapDiscoveryService");

        Method geocode = serviceType.getMethod("geocode", String.class, String.class);
        Method searchPlaces = serviceType.getMethod(
                "searchPlaces", String.class, String.class, boolean.class, int.class);
        Method reverseGeocode = serviceType.getMethod("reverseGeocode", double.class, double.class);

        assertThat(geocode.getReturnType()).isEqualTo(Map.class);
        assertThat(searchPlaces.getReturnType()).isEqualTo(List.class);
        assertThat(reverseGeocode.getReturnType()).isEqualTo(Map.class);
    }

    @Test
    void discoveryServiceDependsOnDedicatedReadOnlyAmapGateway() throws Exception {
        Class<?> gatewayType = load("org.urbansafe.priority.map.service.AmapDiscoveryGateway");
        Class<?> serviceType = load("org.urbansafe.priority.map.service.MapDiscoveryService");

        Method geocode = gatewayType.getMethod("geocode", String.class, String.class);
        Method searchPlaces = gatewayType.getMethod(
                "searchPlaces", String.class, String.class, boolean.class, int.class);
        Method reverseGeocode = gatewayType.getMethod("reverseGeocode", double.class, double.class);
        Constructor<?> constructor = serviceType.getConstructor(
                MapProperties.class, AmapProperties.class, gatewayType);

        assertThat(geocode.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(searchPlaces.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(reverseGeocode.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(constructor).isNotNull();
    }

    @Test
    void blankPlaceKeywordIsRejectedBeforeCallingAmap() {
        Fixture fixture = fixture(false, "");

        assertThatThrownBy(() -> fixture.service().searchPlaces("  ", "株洲市", true, 8))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_PLACE_KEYWORD_REQUIRED"));
        verify(fixture.gateway(), never()).searchPlaces("  ", "株洲市", true, 8);
    }

    @Test
    void placeSearchPageSizeOutsideContractIsRejected() {
        Fixture fixture = fixture(false, "");

        assertThatThrownBy(() -> fixture.service().searchPlaces("示范小区", "株洲市", true, 11))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_PLACE_PAGE_SIZE_INVALID"));
    }

    @Test
    void mockPlaceSearchReturnsDeterministicCandidateWithoutCallingAmap() {
        Fixture fixture = fixture(false, "");

        List<Map<String, Object>> result = fixture.service()
                .searchPlaces("示范小区", "株洲市", true, 8);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .containsEntry("name", "示范小区")
                .containsEntry("formattedAddress", "株洲市示范小区")
                .containsEntry("provider", "MOCK")
                .containsEntry("coordinateSystem", "UNKNOWN")
                .containsEntry("mock", true)
                .containsEntry("longitude", 113.13396)
                .containsEntry("latitude", 27.82767);
        verify(fixture.gateway(), never()).searchPlaces("示范小区", "株洲市", true, 8);
    }

    @Test
    void mockReverseGeocodingKeepsClickedCoordinatesAndDoesNotInventPoi() {
        Fixture fixture = fixture(false, "");

        Map<String, Object> result = fixture.service().reverseGeocode(113.12, 27.88);

        assertThat(result)
                .containsEntry("longitude", 113.12)
                .containsEntry("latitude", 27.88)
                .containsEntry("provider", "MOCK")
                .containsEntry("coordinateSystem", "UNKNOWN")
                .containsEntry("mock", true)
                .containsKeys("nearestPoiId", "nearestPoiName");
        assertThat(result.get("nearestPoiId")).isNull();
        assertThat(result.get("nearestPoiName")).isNull();
        verify(fixture.gateway(), never()).reverseGeocode(113.12, 27.88);
    }

    @Test
    void realPlaceSearchNormalizesAmapPoiCandidate() throws Exception {
        Fixture fixture = fixture(true, "test-key");
        JsonNode upstream = objectMapper.readTree("""
                {
                  "status":"1",
                  "info":"OK",
                  "pois":[{
                    "id":"B001",
                    "name":"示范小区",
                    "location":"113.123456,27.876543",
                    "pname":"湖南省",
                    "cityname":"株洲市",
                    "adname":"天元区",
                    "address":"示范路1号",
                    "adcode":"430211",
                    "citycode":"0733"
                  }]
                }
                """);
        when(fixture.gateway().searchPlaces("示范小区", "株洲市", true, 8)).thenReturn(upstream);

        List<Map<String, Object>> result = fixture.service()
                .searchPlaces("示范小区", "株洲市", true, 8);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst())
                .containsEntry("providerObjectId", "B001")
                .containsEntry("name", "示范小区")
                .containsEntry("formattedAddress", "湖南省株洲市天元区示范路1号")
                .containsEntry("province", "湖南省")
                .containsEntry("city", "株洲市")
                .containsEntry("district", "天元区")
                .containsEntry("adcode", "430211")
                .containsEntry("citycode", "0733")
                .containsEntry("longitude", 113.123456)
                .containsEntry("latitude", 27.876543)
                .containsEntry("provider", "AMAP")
                .containsEntry("coordinateSystem", "GCJ02")
                .containsEntry("mock", false);
    }

    @Test
    void realReverseGeocodingReturnsAddressAndNearestPoiWhenPresent() throws Exception {
        Fixture fixture = fixture(true, "test-key");
        JsonNode upstream = objectMapper.readTree("""
                {
                  "status":"1",
                  "regeocode":{
                    "formatted_address":"湖南省株洲市天元区示范路1号",
                    "addressComponent":{
                      "province":"湖南省",
                      "city":"株洲市",
                      "district":"天元区",
                      "adcode":"430211",
                      "citycode":"0733"
                    },
                    "pois":[{"id":"B001","name":"示范小区"}]
                  }
                }
                """);
        when(fixture.gateway().reverseGeocode(113.12, 27.88)).thenReturn(upstream);

        Map<String, Object> result = fixture.service().reverseGeocode(113.12, 27.88);

        assertThat(result)
                .containsEntry("formattedAddress", "湖南省株洲市天元区示范路1号")
                .containsEntry("province", "湖南省")
                .containsEntry("city", "株洲市")
                .containsEntry("district", "天元区")
                .containsEntry("adcode", "430211")
                .containsEntry("citycode", "0733")
                .containsEntry("longitude", 113.12)
                .containsEntry("latitude", 27.88)
                .containsEntry("provider", "AMAP")
                .containsEntry("coordinateSystem", "GCJ02")
                .containsEntry("nearestPoiId", "B001")
                .containsEntry("nearestPoiName", "示范小区")
                .containsEntry("mock", false);
    }

    @Test
    void realReverseGeocodingWithoutPoiStillReturnsUsableAddressCandidate() throws Exception {
        Fixture fixture = fixture(true, "test-key");
        JsonNode upstream = objectMapper.readTree("""
                {
                  "status":"1",
                  "regeocode":{
                    "formatted_address":"湖南省株洲市天元区示范路1号",
                    "addressComponent":{
                      "province":"湖南省",
                      "city":"株洲市",
                      "district":"天元区",
                      "adcode":"430211",
                      "citycode":"0733"
                    },
                    "pois":[]
                  }
                }
                """);
        when(fixture.gateway().reverseGeocode(113.12, 27.88)).thenReturn(upstream);

        Map<String, Object> result = fixture.service().reverseGeocode(113.12, 27.88);

        assertThat(result.get("formattedAddress")).isEqualTo("湖南省株洲市天元区示范路1号");
        assertThat(result).containsKeys("nearestPoiId", "nearestPoiName");
        assertThat(result.get("nearestPoiId")).isNull();
        assertThat(result.get("nearestPoiName")).isNull();
    }

    @Test
    void realForwardGeocodingPreservesExistingPhase2Shape() throws Exception {
        Fixture fixture = fixture(true, "test-key");
        JsonNode upstream = objectMapper.readTree("""
                {
                  "status":"1",
                  "geocodes":[{
                    "formatted_address":"湖南省株洲市天元区示范路1号",
                    "location":"113.123456,27.876543",
                    "level":"门牌号"
                  }]
                }
                """);
        when(fixture.gateway().geocode("示范路1号", "株洲市")).thenReturn(upstream);

        Map<String, Object> result = fixture.service().geocode("示范路1号", "株洲市");

        assertThat(result)
                .containsEntry("formattedAddress", "湖南省株洲市天元区示范路1号")
                .containsEntry("longitude", 113.123456)
                .containsEntry("latitude", 27.876543)
                .containsEntry("provider", "AMAP")
                .containsEntry("matchLevel", "门牌号")
                .containsEntry("mock", false);
    }

    @Test
    void invalidReverseCoordinatesAreRejectedBeforeCallingAmap() {
        Fixture fixture = fixture(true, "test-key");

        assertThatThrownBy(() -> fixture.service().reverseGeocode(181, 27.88))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_COORDINATE_INVALID"));
        verify(fixture.gateway(), never()).reverseGeocode(181, 27.88);
    }

    @Test
    void upstreamFailureUsesStableBusinessErrorCode() throws Exception {
        Fixture fixture = fixture(true, "test-key");
        JsonNode upstream = objectMapper.readTree("""
                {"status":"0","info":"INVALID_USER_KEY","infocode":"10001"}
                """);
        when(fixture.gateway().searchPlaces("示范小区", "株洲市", true, 8)).thenReturn(upstream);

        assertThatThrownBy(() -> fixture.service().searchPlaces("示范小区", "株洲市", true, 8))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("MAP_UPSTREAM_ERROR"));
    }

    private Fixture fixture(boolean enabled, String key) {
        MapProperties map = new MapProperties();
        map.setEnabled(enabled);
        map.setDefaultCenterLongitude(113.13396);
        map.setDefaultCenterLatitude(27.82767);
        AmapProperties amap = new AmapProperties();
        amap.setWebServiceKey(key);
        AmapDiscoveryGateway gateway = mock(AmapDiscoveryGateway.class);
        return new Fixture(new MapDiscoveryService(map, amap, gateway), gateway);
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            assertThat(ex)
                    .as("地图发现组件 %s 应独立存在", className)
                    .isNull();
            throw new AssertionError("unreachable", ex);
        }
    }

    private record Fixture(MapDiscoveryService service, AmapDiscoveryGateway gateway) {
    }
}
