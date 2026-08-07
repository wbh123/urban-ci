package org.urbansafe.priority.evidence;

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
import org.urbansafe.priority.persistence.entity.BuildingEvidenceEntity;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.BuildingEvidenceMapper;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证证据列表通过统一适配层向前端暴露零基分页。 */
@WithMockUser(username = "evidence-pagination-test", roles = "ADMIN")
class BuildingEvidencePaginationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CommunityMapper communityMapper;
    @Autowired private BuildingMapper buildingMapper;
    @Autowired private BuildingEvidenceMapper buildingEvidenceMapper;

    /** 同一楼栋的三条证据必须通过零、一、二页获取不同的首记录。 */
    @Test
    void listEvidenceShouldUseZeroBasedPagesWithoutRepeatingFirstRecord() throws Exception {
        String runToken = "PAGE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        CommunityEntity community = new CommunityEntity();
        community.setId(UUID.randomUUID());
        community.setCommunityCode(runToken);
        community.setCommunityName("证据分页测试小区");
        community.setBuildingCount(0);
        community.setHouseholdCount(0);
        community.setResidentCount(0);
        community.setStatus("ACTIVE");
        communityMapper.insert(community);
        BuildingEntity building = new BuildingEntity();
        building.setId(UUID.randomUUID());
        building.setCommunityId(community.getId());
        building.setBuildingCode(runToken);
        building.setBuildingName("证据分页测试楼栋");
        building.setHouseholdCount(0);
        building.setResidentCount(0);
        building.setElderlyCount(0);
        building.setChildCount(0);
        building.setHasElevator(false);
        building.setHasIllegalModification(false);
        building.setHasGroundFloorBusiness(false);
        building.setStatus("ACTIVE");
        buildingMapper.insert(building);
        for (int index = 0; index < 3; index++) {
            BuildingEvidenceEntity evidence = new BuildingEvidenceEntity();
            evidence.setId(UUID.randomUUID());
            evidence.setBuildingId(building.getId());
            evidence.setEvidenceType("MAINTENANCE_RECORD");
            evidence.setTitle(runToken + "证据" + index);
            evidence.setReliabilityLevel("UNVERIFIED");
            buildingEvidenceMapper.insert(evidence);
        }

        List<UUID> identifiers = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode body = objectMapper.readTree(mockMvc.perform(get(
                            "/api/v1/buildings/" + building.getId() + "/evidence")
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
