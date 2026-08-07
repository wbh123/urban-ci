package org.urbansafe.priority.building;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证楼栋列表通过统一适配层向前端暴露零基分页。 */
@WithMockUser(username = "building-pagination-test", roles = "ADMIN")
class BuildingPaginationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CommunityMapper communityMapper;
    @Autowired private BuildingMapper buildingMapper;

    /** 三个构造年份稳定有序的楼栋必须可由零、 一、二页依次读取且不重复。 */
    @Test
    void listBuildingsShouldUseZeroBasedPagesWithoutRepeatingFirstRecord() throws Exception {
        String runToken = "PAGE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CommunityEntity community = new CommunityEntity();
        community.setId(UUID.randomUUID());
        community.setCommunityCode(runToken);
        community.setCommunityName("楼栋分页测试小区");
        community.setBuildingCount(0);
        community.setHouseholdCount(0);
        community.setResidentCount(0);
        community.setStatus("ACTIVE");
        communityMapper.insert(community);
        for (int index = 0; index < 3; index++) {
            BuildingEntity building = new BuildingEntity();
            building.setId(UUID.randomUUID());
            building.setCommunityId(community.getId());
            building.setBuildingCode(runToken + "-" + index);
            building.setBuildingName(runToken + "楼栋" + index);
            building.setConstructionYear((short) (2000 + index));
            building.setHouseholdCount(0);
            building.setResidentCount(0);
            building.setElderlyCount(0);
            building.setChildCount(0);
            building.setHasElevator(false);
            building.setHasIllegalModification(false);
            building.setHasGroundFloorBusiness(false);
            building.setStatus("ACTIVE");
            buildingMapper.insert(building);
        }

        List<UUID> identifiers = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/buildings")
                            .param("keyword", runToken)
                            .param("sort", "constructionYear,asc")
                            .param("page", String.valueOf(page))
                            .param("size", "1"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(body.at("/data/page/page").asInt()).isEqualTo(page);
            identifiers.add(UUID.fromString(body.at("/data/content/0/id").asText()));
        }

        assertThat(identifiers).doesNotHaveDuplicates();
    }
}
