package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.MapDiscoveryService;
import org.urbansafe.priority.map.service.Phase2MapService;

/** 旧地图控制器仅保留原有兼容入口，新建档发现接口由 ArchiveMapController 承担。 */
class Phase2MapControllerTest {

    @Test
    void legacyGeocodingPreviewUsesUnifiedDiscoveryService() throws Exception {
        Phase2MapService legacyService = mock(Phase2MapService.class);
        MapDiscoveryService discoveryService = mock(MapDiscoveryService.class);
        Phase2MapController controller = new Phase2MapController(legacyService, discoveryService);
        Map<String, Object> geocodeResult = Map.of("provider", "MOCK", "mock", true);
        when(discoveryService.geocode("示范路1号", "株洲市")).thenReturn(geocodeResult);

        ResponseEntity<?> response = controller.geocode(
                Map.of("address", "示范路1号", "city", "株洲市"));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(discoveryService).geocode("示范路1号", "株洲市");
        verifyNoInteractions(legacyService);
    }

    @Test
    void legacyGeocodingPreviewRequiresDirectoryReadRole() throws Exception {
        Method method = Phase2MapController.class.getMethod("geocode", Map.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(BusinessAccessService.DIRECTORY_READ_ROLES);
    }

    @Test
    void archiveDiscoveryRoutesAreNotHandwrittenOnLegacyController() {
        assertThat(Phase2MapController.class.getDeclaredMethods())
                .extracting(Method::getName)
                .doesNotContain("searchPlaces", "reverseGeocode");
    }
}
