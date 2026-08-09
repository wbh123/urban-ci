package org.urbansafe.priority.map.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.urbansafe.priority.common.security.BusinessAccessService;
import org.urbansafe.priority.map.service.CommunityBoundaryCandidateService;

/** 小区候选边界入口独立于旧地图兼容控制器，避免与建档发现接口互相覆盖。 */
class CommunityBoundaryCandidateControllerTest {

    @Test
    void previewDelegatesToBoundaryCandidateService() {
        CommunityBoundaryCandidateService service = mock(CommunityBoundaryCandidateService.class);
        CommunityBoundaryCandidateController controller = new CommunityBoundaryCandidateController(service);
        Map<String, Object> candidate = Map.of(
                "available", true,
                "provider", "AMAP",
                "sourceType", "AMAP_AOI"
        );
        when(service.preview("示范小区", "示范路1号", "株洲市")).thenReturn(candidate);

        ResponseEntity<?> response = controller.preview(Map.of(
                "communityName", "示范小区",
                "address", "示范路1号",
                "city", "株洲市"
        ));

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(service).preview("示范小区", "示范路1号", "株洲市");
    }

    @Test
    void previewRequiresDirectoryReadRole() throws Exception {
        Method method = CommunityBoundaryCandidateController.class.getMethod("preview", Map.class);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(BusinessAccessService.DIRECTORY_READ_ROLES);
    }
}
