package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.map.service.Phase2MapService;

/** 可视化建档地图发现接口的控制层契约。 */
class Phase2MapControllerTest {

    @Test
    void controllerInjectsDiscoveryServiceAndRoutesAllDiscoveryCallsThroughIt() throws Exception {
        Phase2MapService legacyService = mock(Phase2MapService.class);
        MapDiscoveryService discoveryService = mock(MapDiscoveryService.class);
        Constructor<Phase2MapController> constructor = Phase2MapController.class.getConstructor(
                Phase2MapService.class, MapDiscoveryService.class);
        Phase2MapController controller = constructor.newInstance(legacyService, discoveryService);

        Map<String, Object> geocodeResult = Map.of("provider", "MOCK", "mock", true);
        List<Map<String, Object>> placeResult = List.of(Map.of("name", "示范小区"));
        Map<String, Object> reverseResult = Map.of(
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MOCK",
                "mock", true);
        when(discoveryService.geocode("示范路1号", "株洲市")).thenReturn(geocodeResult);
        when(discoveryService.searchPlaces("示范小区", "株洲市", true, 8)).thenReturn(placeResult);
        when(discoveryService.reverseGeocode(113.12, 27.88)).thenReturn(reverseResult);

        invoke(controller, "geocode", Map.of("address", "示范路1号", "city", "株洲市"));
        invoke(controller, "searchPlaces", Map.of(
                "keyword", "示范小区",
                "region", "株洲市",
                "cityLimit", true,
                "pageSize", 8));
        invoke(controller, "reverseGeocode", Map.of(
                "longitude", 113.12,
                "latitude", 27.88));

        verify(discoveryService).geocode("示范路1号", "株洲市");
        verify(discoveryService).searchPlaces("示范小区", "株洲市", true, 8);
        verify(discoveryService).reverseGeocode(113.12, 27.88);
        verifyNoInteractions(legacyService);
    }

    @Test
    void discoveryEndpointsRequireDirectoryReadRole() throws Exception {
        assertPreAuthorize("geocode");
        assertPreAuthorize("searchPlaces");
        assertPreAuthorize("reverseGeocode");
    }

    private ResponseEntity<?> invoke(
            Phase2MapController controller,
            String methodName,
            Map<String, Object> body) throws Exception {
        Method method = Phase2MapController.class.getMethod(methodName, Map.class);
        return (ResponseEntity<?>) method.invoke(controller, body);
    }

    private void assertPreAuthorize(String methodName) throws Exception {
        Method method = Phase2MapController.class.getMethod(methodName, Map.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s 应声明方法级目录读取权限", methodName)
                .isNotNull();
        assertThat(annotation.value()).isEqualTo(BusinessAccessService.DIRECTORY_READ_ROLES);
    }
}
