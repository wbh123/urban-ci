package org.urbansafe.priority.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.community.service.impl.CommunityServiceImpl;
import org.urbansafe.priority.community.converter.CommunityConverter;
import org.urbansafe.priority.community.result.CommunityDetailResult;
import org.urbansafe.priority.common.pagination.ApiPageRequest;
import org.urbansafe.priority.model.dto.CreateCommunityRequest;
import org.urbansafe.priority.model.dto.UpdateCommunityRequest;

@ExtendWith(MockitoExtension.class)
class CommunityServiceTest {

    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private org.urbansafe.priority.audit.service.AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    @Spy
    private CommunityConverter communityConverter = new CommunityConverter();

    @InjectMocks
    private CommunityServiceImpl communityService;

    @Test
    void createCommunityShouldReturnNewEntity() {
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setCommunityCode("C001");
        request.setCommunityName("测试小区");
        request.setAdministrativeRegion("测试区");
        request.setAddress("测试地址");
        request.setHouseholdCount(100);
        request.setResidentCount(300);

        when(communityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(communityMapper.insert(any(CommunityEntity.class))).thenReturn(1);

        CommunityDetailResult result = communityService.create(communityConverter.toCommand(request));

        assertThat(result).isNotNull();
        assertThat(result.communityCode()).isEqualTo("C001");
        assertThat(result.communityName()).isEqualTo("测试小区");
        assertThat(result.buildingCount()).isEqualTo(0);
        verify(communityMapper).insert(any(CommunityEntity.class));
    }

    @Test
    void createCommunityWithDuplicateCodeShouldThrow409() {
        CreateCommunityRequest request = new CreateCommunityRequest();
        request.setCommunityCode("C001");
        request.setCommunityName("新小区");

        CommunityEntity existing = new CommunityEntity();
        existing.setId(UUID.randomUUID());
        existing.setCommunityCode("C001");

        when(communityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);

        assertThatThrownBy(() -> communityService.create(communityConverter.toCommand(request)))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> {
                    ResourceConflictException rcex = (ResourceConflictException) ex;
                    assertThat(rcex.getErrorCode()).isEqualTo("COMMUNITY_CODE_CONFLICT");
                });
    }

    @Test
    void deleteEmptyCommunityShouldSucceed() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);
        community.setCommunityCode("C001");

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(communityMapper.deleteById((Serializable) communityId)).thenReturn(1);

        communityService.delete(communityId);

        verify(communityMapper).deleteById((Serializable) communityId);
    }

    @Test
    void deleteCommunityWithBuildingsShouldThrow409() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> communityService.delete(communityId))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> {
                    ResourceConflictException rcex = (ResourceConflictException) ex;
                    assertThat(rcex.getErrorCode()).isEqualTo("COMMUNITY_HAS_ACTIVE_BUILDINGS");
                });

        verify(communityMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    void getNonexistentCommunityShouldThrow404() {
        UUID communityId = UUID.randomUUID();
        when(communityMapper.selectById(communityId)).thenReturn(null);

        assertThatThrownBy(() -> communityService.get(communityId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException nfex = (ResourceNotFoundException) ex;
                    assertThat(nfex.getErrorCode()).isEqualTo("COMMUNITY_NOT_FOUND");
                });
    }

    /** 验证修改小区编码时会实际写入规范化后的新编码。 */
    @Test
    void updateCommunityShouldPersistNormalizedCode() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity existing = new CommunityEntity();
        existing.setId(communityId);
        existing.setCommunityCode("OLD-001");
        existing.setVersion(0L);

        UpdateCommunityRequest request = new UpdateCommunityRequest();
        request.setVersion(0L);
        request.setCommunityCode(" new-001 ");

        when(communityMapper.selectById(communityId)).thenReturn(existing);
        when(communityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(communityMapper.updateById(any(CommunityEntity.class))).thenReturn(1);

        CommunityDetailResult result = communityService.update(
                communityId, communityConverter.toCommand(request));

        assertThat(result.communityCode()).isEqualTo("NEW-001");
        verify(communityMapper).updateById(existing);
    }

    /** 验证未知排序字段不会静默回退到默认排序。 */
    @Test
    void pageWithUnknownSortFieldShouldThrow400() {
        assertThatThrownBy(() -> communityService.page(null, null, null,
                new ApiPageRequest(0, 20), "dropTable,desc"))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(error -> assertThat(((InvalidRequestException) error).getErrorCode())
                        .isEqualTo("INVALID_SORT_FIELD"));
    }
}
