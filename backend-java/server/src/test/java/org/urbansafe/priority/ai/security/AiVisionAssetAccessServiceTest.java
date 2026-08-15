package org.urbansafe.priority.ai.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.urbansafe.priority.common.security.BusinessAccessService;

class AiVisionAssetAccessServiceTest {

    @Test
    void allowsAssetOnlyWhenBindingResolvesToRequestedBuilding() {
        UUID assetId = UUID.randomUUID();
        UUID buildingId = UUID.randomUUID();
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyMap(), org.mockito.ArgumentMatchers.eq(UUID.class)))
                .thenReturn(List.of(buildingId));
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        AiVisionAssetAccessService service = new AiVisionAssetAccessService(jdbc, accessService);

        service.assertCanReadAssetForBuilding(assetId, buildingId);

        verify(accessService).assertCanReadBuilding(buildingId);
    }

    @Test
    void rejectsAssetBoundToDifferentBuilding() {
        UUID assetId = UUID.randomUUID();
        UUID requestedBuildingId = UUID.randomUUID();
        UUID actualBuildingId = UUID.randomUUID();
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.queryForList(anyString(), anyMap(), org.mockito.ArgumentMatchers.eq(UUID.class)))
                .thenReturn(List.of(actualBuildingId));
        BusinessAccessService accessService = mock(BusinessAccessService.class);
        AiVisionAssetAccessService service = new AiVisionAssetAccessService(jdbc, accessService);

        assertThatThrownBy(() -> service.assertCanReadAssetForBuilding(assetId, requestedBuildingId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("AI_VISION_ASSET_BUILDING_MISMATCH");
        verify(accessService, never()).assertCanReadBuilding(requestedBuildingId);
    }
}
