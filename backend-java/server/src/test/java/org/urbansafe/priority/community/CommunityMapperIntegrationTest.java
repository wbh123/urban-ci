package org.urbansafe.priority.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.mapperext.CommunityMapperExt;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class CommunityMapperIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private CommunityMapperExt communityMapperExt;

    @Autowired
    private BuildingMapper buildingMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        buildingMapper.delete(null);
        communityMapper.delete(null);
    }

    @Test
    void insertAndReadCommunityWithUuid() {
        CommunityEntity entity = createTestCommunity("C001", "测试小区");

        CommunityEntity loaded = communityMapper.selectById(entity.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getId()).isNotNull();
        assertThat(loaded.getCommunityCode()).isEqualTo("C001");
        assertThat(loaded.getCommunityName()).isEqualTo("测试小区");
    }

    @Test
    void readJsonbExtraAttributesWithChineseContent() {
        CommunityEntity entity = createTestCommunity("C002", "中文小区");
        ObjectNode extra = objectMapper.createObjectNode();
        extra.put("物业管理", "优秀");
        extra.put("建成年代", "2005年");
        extra.put("特色", "绿化率高，有儿童游乐设施");
        entity.setExtraAttributes(extra);
        communityMapper.updateById(entity);

        CommunityEntity loaded = communityMapper.selectById(entity.getId());

        assertThat(loaded).isNotNull();
        assertThat(loaded.getExtraAttributes()).isNotNull();
        assertThat(loaded.getExtraAttributes().get("物业管理").asText()).isEqualTo("优秀");
        assertThat(loaded.getExtraAttributes().get("建成年代").asText()).isEqualTo("2005年");
        assertThat(loaded.getExtraAttributes().get("特色").asText()).isEqualTo("绿化率高，有儿童游乐设施");
    }

    @Test
    void logicalDeleteShouldMakeInvisible() {
        CommunityEntity entity = createTestCommunity("C003", "待删除小区");
        UUID id = entity.getId();

        communityMapper.deleteById(id);

        CommunityEntity loaded = communityMapper.selectById(id);
        assertThat(loaded).isNull();

        long count = communityMapper.selectCount(
                new LambdaQueryWrapper<CommunityEntity>()
                        .eq(CommunityEntity::getCommunityCode, "C003"));
        assertThat(count).isEqualTo(0);
    }

    @Test
    void communityCodeShouldBeReusableAfterDelete() {
        CommunityEntity first = createTestCommunity("C004", "第一个小区");
        communityMapper.deleteById(first.getId());

        CommunityEntity second = createTestCommunity("C004", "第二个小区");

        assertThat(second.getId()).isNotEqualTo(first.getId());
        assertThat(second.getCommunityCode()).isEqualTo("C004");

        CommunityEntity loaded = communityMapper.selectById(second.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getCommunityCode()).isEqualTo("C004");
    }

    @Test
    void refreshBuildingCountShouldUpdateCorrectly() {
        CommunityEntity community = createTestCommunity("C005", "统计小区");
        assertThat(community.getBuildingCount()).isEqualTo(0);

        communityMapperExt.refreshBuildingCount(community.getId());

        CommunityEntity refreshed = communityMapper.selectById(community.getId());
        assertThat(refreshed.getBuildingCount()).isEqualTo(0);
    }

    private CommunityEntity createTestCommunity(String code, String name) {
        CommunityEntity entity = new CommunityEntity();
        entity.setId(UUID.randomUUID());
        entity.setCommunityCode(code);
        entity.setCommunityName(name);
        entity.setAdministrativeRegion("测试区");
        entity.setAddress("测试地址123号");
        entity.setConstructionPeriod("2000-2010");
        entity.setBuildingCount(0);
        entity.setHouseholdCount(100);
        entity.setResidentCount(300);
        entity.setStatus("ACTIVE");
        communityMapper.insert(entity);
        return entity;
    }
}
