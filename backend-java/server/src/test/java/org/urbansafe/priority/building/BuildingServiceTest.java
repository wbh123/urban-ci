package org.urbansafe.priority.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.building.service.impl.BuildingServiceImpl;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.mapperext.CommunityMapperExt;
import org.urbansafe.priority.persistence.mapper.BuildingEvidenceMapper;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.building.command.CreateBuildingCommand;
import org.urbansafe.priority.building.command.UpdateBuildingCommand;
import org.urbansafe.priority.building.converter.BuildingConverter;
import org.urbansafe.priority.building.result.BuildingDetailResult;

@ExtendWith(MockitoExtension.class)
class BuildingServiceTest {

    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private CommunityMapper communityMapper;
    @Mock
    private CommunityMapperExt communityMapperExt;
    @Mock
    private BuildingEvidenceMapper buildingEvidenceMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private java.time.Clock clock;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();
    /**
     * Service 现在以内部结果返回，需要注入真实转换器以覆盖实体到 Result 的边界。
     */
    @Spy
    private BuildingConverter buildingConverter = new BuildingConverter();

    @InjectMocks
    private BuildingServiceImpl buildingService;

    @Test
    void createBuildingShouldReturnNewBuilding() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);
        community.setCommunityCode("C001");

        CreateBuildingCommand request = createCommand(communityId, "B001", "测试楼栋", 24, 72, null);

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(buildingMapper.insert(any(BuildingEntity.class))).thenReturn(1);

        BuildingDetailResult response = buildingService.createBuilding(request);

        assertThat(response).isNotNull();
        assertThat(response.buildingCode()).isEqualTo("B001");
        assertThat(response.communityId()).isEqualTo(communityId);
        verify(buildingMapper).insert(any(BuildingEntity.class));
        verify(communityMapperExt).refreshBuildingCount(communityId);
    }

    @Test
    void createBuildingWithNonexistentCommunityShouldThrow404() {
        UUID communityId = UUID.randomUUID();

        CreateBuildingCommand request = createCommand(communityId, "B001", null, 10, 30, null);

        when(communityMapper.selectById(communityId)).thenReturn(null);

        assertThatThrownBy(() -> buildingService.createBuilding(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException nfex = (ResourceNotFoundException) ex;
                    assertThat(nfex.getErrorCode()).isEqualTo("COMMUNITY_NOT_FOUND");
                });

        verify(buildingMapper, never()).insert(any(BuildingEntity.class));
    }

    @Test
    void createBuildingWithDuplicateCodeInSameCommunityShouldThrow409() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);

        CreateBuildingCommand request = createCommand(communityId, "B001", null, 10, 30, null);

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> buildingService.createBuilding(request))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(ex -> {
                    ResourceConflictException rcex = (ResourceConflictException) ex;
                    assertThat(rcex.getErrorCode()).isEqualTo("BUILDING_CODE_CONFLICT");
                });

        verify(buildingMapper, never()).insert(any(BuildingEntity.class));
    }

    @Test
    void createBuildingShouldRefreshCommunityStats() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);

        CreateBuildingCommand request = createCommand(communityId, "B002", null, 24, 72, null);

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(buildingMapper.insert(any(BuildingEntity.class))).thenReturn(1);

        buildingService.createBuilding(request);

        verify(communityMapperExt).refreshBuildingCount(communityId);
    }

    @Test
    void getNonexistentBuildingShouldThrow404() {
        UUID buildingId = UUID.randomUUID();
        when(buildingMapper.selectById(buildingId)).thenReturn(null);

        assertThatThrownBy(() -> buildingService.getBuilding(buildingId))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(ex -> {
                    ResourceNotFoundException nfex = (ResourceNotFoundException) ex;
                    assertThat(nfex.getErrorCode()).isEqualTo("BUILDING_NOT_FOUND");
                });
    }

    @Test
    void createBuildingWithElderlyExceedingResidentShouldThrow400() {
        UUID communityId = UUID.randomUUID();
        CommunityEntity community = new CommunityEntity();
        community.setId(communityId);

        CreateBuildingCommand request = createCommand(communityId, "B003", null, 10, 30, 40);

        when(communityMapper.selectById(communityId)).thenReturn(community);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> buildingService.createBuilding(request))
                .isInstanceOf(InvalidRequestException.class)
                .satisfies(ex -> {
                    InvalidRequestException invalidRequest = (InvalidRequestException) ex;
                    assertThat(invalidRequest.getErrorCode())
                            .isEqualTo("BUILDING_POPULATION_RELATION_INVALID");
                });
    }

    /** 验证楼栋迁移时按目标小区和最终编码检查唯一性。 */
    @Test
    void updateBuildingToTargetCommunityWithDuplicateCodeShouldThrow409() {
        UUID buildingId = UUID.randomUUID();
        UUID sourceCommunityId = UUID.randomUUID();
        UUID targetCommunityId = UUID.randomUUID();
        BuildingEntity existing = building(buildingId, sourceCommunityId, "B001", 0L);
        CommunityEntity targetCommunity = new CommunityEntity();
        targetCommunity.setId(targetCommunityId);

        UpdateBuildingCommand request = updateCommand(0L, targetCommunityId, " b001 ");

        when(buildingMapper.selectById(buildingId)).thenReturn(existing);
        when(communityMapper.selectById(targetCommunityId)).thenReturn(targetCommunity);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> buildingService.updateBuilding(buildingId, request))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(error -> assertThat(((ResourceConflictException) error).getErrorCode())
                        .isEqualTo("BUILDING_CODE_CONFLICT"));

        verify(buildingMapper, never()).updateById(any(BuildingEntity.class));
    }

    /**
     * 验证两个小区可以各自使用相同楼栋编码，且迁移到已存在同编码楼栋的小区失败后，
     * 源楼栋在持久层中的所属小区不会被提前改写。
     *
     * <p>该测试用内存映射模拟 Mapper 的最小读写行为，而非只验证 Mockito 调用次数，
     * 从而覆盖比赛演示中“跨小区同编码可共存、冲突迁移不改变原归属”的完整业务序列。</p>
     */
    @Test
    void buildingsWithSameCodeInDifferentCommunitiesShouldCoexistAndConflictMigrationShouldKeepSourceCommunity() {
        // 源、目标小区标识分别代表两个独立的业务归属范围。
        UUID sourceCommunityId = UUID.randomUUID();
        UUID targetCommunityId = UUID.randomUUID();
        // 使用内存映射保存 insert 后的实体，以便后续通过 Mapper 读取验证实际归属。
        Map<UUID, BuildingEntity> persistedBuildings = new HashMap<>();
        CommunityEntity sourceCommunity = new CommunityEntity();
        sourceCommunity.setId(sourceCommunityId);
        CommunityEntity targetCommunity = new CommunityEntity();
        targetCommunity.setId(targetCommunityId);

        when(communityMapper.selectById(sourceCommunityId)).thenReturn(sourceCommunity);
        when(communityMapper.selectById(targetCommunityId)).thenReturn(targetCommunity);
        // 前四次查询分别对应两栋共享编码楼栋、源迁移楼栋和目标冲突楼栋的创建；
        // 第五次查询对应迁移前的目标小区冲突校验。
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L, 0L, 0L, 0L, 1L);
        // 模拟数据库写入时分配主键并保存实体，使测试可从 Mapper 读取源楼栋的最终状态。
        org.mockito.Mockito.doAnswer(invocation -> {
            BuildingEntity entity = invocation.getArgument(0);
            entity.setId(UUID.randomUUID());
            persistedBuildings.put(entity.getId(), entity);
            return 1;
        }).when(buildingMapper).insert(any(BuildingEntity.class));
        when(buildingMapper.selectById(any(UUID.class)))
                .thenAnswer(invocation -> persistedBuildings.get(invocation.getArgument(0)));

        // 两个小区使用相同编码创建楼栋，均应成功且得到不同主键、不同所属小区。
        BuildingDetailResult sourceShared = buildingService.createBuilding(
                createCommand(sourceCommunityId, "BLD-SHARED", "源小区共享楼栋", 10, 30, null));
        BuildingDetailResult targetShared = buildingService.createBuilding(
                createCommand(targetCommunityId, "BLD-SHARED", "目标小区共享楼栋", 10, 30, null));

        assertThat(sourceShared.id()).isNotEqualTo(targetShared.id());
        assertThat(sourceShared.communityId()).isEqualTo(sourceCommunityId);
        assertThat(targetShared.communityId()).isEqualTo(targetCommunityId);
        assertThat(sourceShared.buildingCode()).isEqualTo(targetShared.buildingCode());

        // 再分别创建待迁移楼栋和目标小区内的同编码楼栋，构造迁移编码冲突。
        BuildingDetailResult sourceMove = buildingService.createBuilding(
                createCommand(sourceCommunityId, "BLD-MOVE", "待迁移楼栋", 10, 30, null));
        buildingService.createBuilding(
                createCommand(targetCommunityId, "BLD-MOVE", "目标小区冲突楼栋", 10, 30, null));

        UpdateBuildingCommand migrationRequest = updateCommand(sourceMove.version(), targetCommunityId, null);

        assertThatThrownBy(() -> buildingService.updateBuilding(sourceMove.id(), migrationRequest))
                .isInstanceOf(ResourceConflictException.class)
                .satisfies(error -> assertThat(((ResourceConflictException) error).getErrorCode())
                        .isEqualTo("BUILDING_CODE_CONFLICT"));

        // 重新通过 Mapper 读取源楼栋，证明冲突路径未把其所属小区改为目标小区。
        BuildingEntity sourceBuildingAfterConflict = buildingMapper.selectById(sourceMove.id());
        assertThat(sourceBuildingAfterConflict).isNotNull();
        assertThat(sourceBuildingAfterConflict.getCommunityId()).isEqualTo(sourceCommunityId);
        verify(buildingMapper, never()).updateById(any(BuildingEntity.class));
    }

    /** 验证迁移成功后同时刷新源小区与目标小区统计。 */
    @Test
    void updateBuildingToNewCommunityShouldRefreshBothCommunityCounts() {
        UUID buildingId = UUID.randomUUID();
        UUID sourceCommunityId = UUID.randomUUID();
        UUID targetCommunityId = UUID.randomUUID();
        BuildingEntity existing = building(buildingId, sourceCommunityId, "B001", 0L);
        CommunityEntity targetCommunity = new CommunityEntity();
        targetCommunity.setId(targetCommunityId);

        UpdateBuildingCommand request = updateCommand(0L, targetCommunityId, null);

        when(buildingMapper.selectById(buildingId)).thenReturn(existing);
        when(communityMapper.selectById(targetCommunityId)).thenReturn(targetCommunity);
        when(buildingMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(buildingMapper.updateById(any(BuildingEntity.class))).thenReturn(1);

        BuildingDetailResult response = buildingService.updateBuilding(buildingId, request);

        assertThat(response.communityId()).isEqualTo(targetCommunityId);
        verify(communityMapperExt).refreshBuildingCount(sourceCommunityId);
        verify(communityMapperExt).refreshBuildingCount(targetCommunityId);
    }

    /** 创建供更新测试复用的最小楼栋实体。 */
    private BuildingEntity building(UUID id, UUID communityId, String code, Long version) {
        BuildingEntity entity = new BuildingEntity();
        entity.setId(id);
        entity.setCommunityId(communityId);
        entity.setBuildingCode(code);
        entity.setResidentCount(0);
        entity.setElderlyCount(0);
        entity.setChildCount(0);
        entity.setVersion(version);
        return entity;
    }

    /**
     * 创建测试所需的最小内部楼栋创建命令。
     *
     * @param communityId 所属小区标识
     * @param buildingCode 楼栋业务编码
     * @param buildingName 楼栋展示名称
     * @param householdCount 户数
     * @param residentCount 居民数
     * @param elderlyCount 老年人数，可为空
     * @return 与 OpenAPI DTO 无关的 Service 输入对象
     */
    private CreateBuildingCommand createCommand(UUID communityId, String buildingCode, String buildingName,
            Integer householdCount, Integer residentCount, Integer elderlyCount) {
        return new CreateBuildingCommand(communityId, buildingCode, buildingName,
                null, null, null, null, null, householdCount, residentCount, elderlyCount,
                null, null, null, null, null, null, null);
    }

    /**
     * 创建测试所需的最小内部楼栋更新命令。
     *
     * @param version 乐观锁版本号
     * @param communityId 迁移后的目标小区标识
     * @param buildingCode 迁移或改名后的楼栋业务编码
     * @return 与 OpenAPI DTO 无关的 Service 更新输入对象
     */
    private UpdateBuildingCommand updateCommand(Long version, UUID communityId, String buildingCode) {
        return new UpdateBuildingCommand(version, communityId, buildingCode,
                null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
    }
}
