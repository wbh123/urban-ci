package org.urbansafe.priority.building;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.mapperext.CommunityMapperExt;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class BuildingMapperIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private BuildingMapper buildingMapper;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private CommunityMapperExt communityMapperExt;

    @BeforeEach
    void cleanUp() {
        buildingMapper.delete(null);
        communityMapper.delete(null);
    }

    @Test
    void insertAndReadBuilding() {
        UUID communityId = createTestCommunity("C001");

        BuildingEntity entity = new BuildingEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommunityId(communityId);
        entity.setBuildingCode("B001");
        entity.setBuildingName("测试楼栋1号");
        entity.setAddress("测试路1号");
        entity.setConstructionYear((short) 2005);
        entity.setStructureType("RC");
        entity.setFloorCount(6);
        entity.setHouseholdCount(24);
        entity.setResidentCount(72);
        entity.setStatus("ACTIVE");
        buildingMapper.insert(entity);

        BuildingEntity loaded = buildingMapper.selectById(entity.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getBuildingCode()).isEqualTo("B001");
        assertThat(loaded.getCommunityId()).isEqualTo(communityId);
        assertThat(loaded.getConstructionYear()).isEqualTo((short) 2005);
    }

    @Test
    void refreshBuildingCountShouldWork() {
        UUID communityId = createTestCommunity("C002");

        BuildingEntity b1 = new BuildingEntity();
        b1.setId(UUID.randomUUID());
        b1.setCommunityId(communityId);
        b1.setBuildingCode("B010");
        b1.setHouseholdCount(10);
        b1.setResidentCount(30);
        b1.setStatus("ACTIVE");
        buildingMapper.insert(b1);

        BuildingEntity b2 = new BuildingEntity();
        b2.setId(UUID.randomUUID());
        b2.setCommunityId(communityId);
        b2.setBuildingCode("B011");
        b2.setHouseholdCount(20);
        b2.setResidentCount(60);
        b2.setStatus("ACTIVE");
        buildingMapper.insert(b2);

        communityMapperExt.refreshBuildingCount(communityId);

        CommunityEntity community = communityMapper.selectById(communityId);
        assertThat(community.getBuildingCount()).isEqualTo(2);
    }

    @Test
    void logicalDeleteShouldMakeBuildingInvisible() {
        UUID communityId = createTestCommunity("C003");

        BuildingEntity entity = new BuildingEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommunityId(communityId);
        entity.setBuildingCode("B020");
        entity.setHouseholdCount(10);
        entity.setResidentCount(30);
        entity.setStatus("ACTIVE");
        buildingMapper.insert(entity);

        buildingMapper.deleteById(entity.getId());

        BuildingEntity loaded = buildingMapper.selectById(entity.getId());
        assertThat(loaded).isNull();
    }

    @Test
    void buildingCodeShouldBeReusableAfterDelete() {
        UUID communityId = createTestCommunity("C004");

        BuildingEntity first = new BuildingEntity();
        first.setId(UUID.randomUUID());
        first.setCommunityId(communityId);
        first.setBuildingCode("B030");
        first.setHouseholdCount(10);
        first.setResidentCount(30);
        first.setStatus("ACTIVE");
        buildingMapper.insert(first);

        buildingMapper.deleteById(first.getId());

        BuildingEntity second = new BuildingEntity();
        second.setId(UUID.randomUUID());
        second.setCommunityId(communityId);
        second.setBuildingCode("B030");
        second.setHouseholdCount(20);
        second.setResidentCount(60);
        second.setStatus("ACTIVE");
        buildingMapper.insert(second);

        BuildingEntity loaded = buildingMapper.selectById(second.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getBuildingCode()).isEqualTo("B030");
        assertThat(loaded.getId()).isNotEqualTo(first.getId());
    }

    private UUID createTestCommunity(String code) {
        CommunityEntity community = new CommunityEntity();
        community.setId(UUID.randomUUID());
        community.setCommunityCode(code);
        community.setCommunityName("测试小区-" + code);
        community.setBuildingCount(0);
        community.setHouseholdCount(0);
        community.setResidentCount(0);
        community.setStatus("ACTIVE");
        communityMapper.insert(community);
        return community.getId();
    }
}
