package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.ArchiveLocationService;
import org.urbansafe.priority.model.api.ArchiveLocationApi;
import org.urbansafe.priority.model.dto.BuildingLocationRequest;
import org.urbansafe.priority.model.dto.BuildingLocationSuccessResponse;

/** 楼栋中心点 HTTP 接口必须实现 OpenAPI 生成接口并复用 ArchiveLocationService。 */
class ArchiveLocationControllerTest {

    @Test
    void controllerImplementsGeneratedApiAndRoutesGetAndPutThroughService() {
        ArchiveLocationService service = mock(ArchiveLocationService.class);
        ArchiveLocationController controller = new ArchiveLocationController(service);
        UUID buildingId = UUID.randomUUID();
        Map<String, Object> stored = Map.of(
                "buildingId", buildingId,
                "longitude", 113.12,
                "latitude", 27.88,
                "provider", "MANUAL",
                "coordinateSystem", "GCJ02");
        when(service.getBuildingLocation(buildingId)).thenReturn(stored);
        when(service.saveBuildingLocation(eq(buildingId), anyMap())).thenReturn(stored);

        assertThat(controller).isInstanceOf(ArchiveLocationApi.class);

        ResponseEntity<BuildingLocationSuccessResponse> getResponse =
                controller.getArchiveBuildingLocation(buildingId);

        BuildingLocationRequest request = new BuildingLocationRequest();
        request.setLongitude(113.12);
        request.setLatitude(27.88);
        ResponseEntity<BuildingLocationSuccessResponse> putResponse =
                controller.saveArchiveBuildingLocation(buildingId, request);

        assertThat(getResponse.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(getResponse.getBody()).isNotNull();
        assertThat(getResponse.getBody().getData().getBuildingId()).isEqualTo(buildingId);
        assertThat(putResponse.getStatusCode().is2xxSuccessful()).isTrue();

        verify(service).getBuildingLocation(buildingId);
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(service).saveBuildingLocation(eq(buildingId), payload.capture());
        assertThat(payload.getValue())
                .containsEntry("longitude", 113.12)
                .containsEntry("latitude", 27.88);
    }

    @Test
    void generatedApiMethodsKeepApprovedRoleConstants() throws Exception {
        assertThat(ArchiveLocationApi.class.isAssignableFrom(ArchiveLocationController.class)).isTrue();

        Method get = ArchiveLocationController.class
                .getMethod("getArchiveBuildingLocation", UUID.class);
        assertPreAuthorize(get, BusinessAccessService.DIRECTORY_READ_ROLES);

        Method put = ArchiveLocationController.class
                .getMethod("saveArchiveBuildingLocation", UUID.class, BuildingLocationRequest.class);
        assertPreAuthorize(put, BusinessAccessService.ARCHIVE_MANAGE_ROLES);
    }

    private void assertPreAuthorize(Method method, String expected) {
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).as("%s 应声明方法级权限", method.getName()).isNotNull();
        assertThat(annotation.value()).isEqualTo(expected);
    }
}
