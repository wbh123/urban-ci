package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.ArchiveLocationService;

/** 楼栋中心点 HTTP 接口必须复用 ArchiveLocationService 并声明方法级权限。 */
class ArchiveLocationControllerTest {

    @Test
    void controllerRoutesGetAndPutThroughArchiveLocationService() throws Exception {
        ArchiveLocationService service = mock(ArchiveLocationService.class);
        Class<?> type = loadController();
        Object controller = type.getConstructor(ArchiveLocationService.class).newInstance(service);
        UUID buildingId = UUID.randomUUID();
        Map<String, Object> stored = Map.of("buildingId", buildingId, "provider", "MANUAL");
        Map<String, Object> request = Map.of(
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MANUAL");
        when(service.getBuildingLocation(buildingId)).thenReturn(stored);
        when(service.saveBuildingLocation(buildingId, request)).thenReturn(stored);

        ResponseEntity<?> getResponse = (ResponseEntity<?>) type
                .getMethod("getBuildingLocation", UUID.class)
                .invoke(controller, buildingId);
        ResponseEntity<?> putResponse = (ResponseEntity<?>) type
                .getMethod("saveBuildingLocation", UUID.class, Map.class)
                .invoke(controller, buildingId, request);

        assertThat(getResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(putResponse.getStatusCode().is2xxSuccessful()).isTrue();
        verify(service).getBuildingLocation(buildingId);
        verify(service).saveBuildingLocation(buildingId, request);
    }

    @Test
    void controllerUsesApprovedPathsAndRoleConstants() throws Exception {
        Class<?> type = loadController();
        RequestMapping root = type.getAnnotation(RequestMapping.class);
        assertThat(root).isNotNull();
        assertThat(root.value()).containsExactly("/api/v1/buildings");

        Method get = type.getMethod("getBuildingLocation", UUID.class);
        GetMapping getMapping = get.getAnnotation(GetMapping.class);
        assertThat(getMapping).isNotNull();
        assertThat(getMapping.value()).containsExactly("/{buildingId}/location");
        assertPreAuthorize(get, BusinessAccessService.DIRECTORY_READ_ROLES);

        Method put = type.getMethod("saveBuildingLocation", UUID.class, Map.class);
        PutMapping putMapping = put.getAnnotation(PutMapping.class);
        assertThat(putMapping).isNotNull();
        assertThat(putMapping.value()).containsExactly("/{buildingId}/location");
        assertPreAuthorize(put, BusinessAccessService.ARCHIVE_MANAGE_ROLES);
    }

    private Class<?> loadController() {
        try {
            return Class.forName("org.urbansafe.priority.map.controller.ArchiveLocationController");
        } catch (ClassNotFoundException ex) {
            assertThat(ex).as("ArchiveLocationController 应存在").isNull();
            throw new AssertionError("unreachable", ex);
        }
    }

    private void assertPreAuthorize(Method method, String expected) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("%s 应声明方法级权限", method.getName()).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
