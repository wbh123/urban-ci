package org.urbansafe.priority.community;

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
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

/** 验证小区列表对外保持零基页号，并能逐页读取不同记录。 */
@WithMockUser(username = "community-pagination-test", roles = "ADMIN")
class CommunityPaginationIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private CommunityMapper communityMapper;

    /** 三页大小为一的请求必须分别返回三个不同的小区，且响应页号仍是零基。 */
    @Test
    void listCommunitiesShouldUseZeroBasedPagesWithoutRepeatingFirstRecord() throws Exception {
        String runToken = "PAGE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        for (int index = 0; index < 3; index++) {
            CommunityEntity community = new CommunityEntity();
            community.setId(UUID.randomUUID());
            community.setCommunityCode(runToken + "-" + index);
            community.setCommunityName("分页小区" + index);
            community.setBuildingCount(0);
            community.setHouseholdCount(0);
            community.setResidentCount(0);
            community.setStatus("ACTIVE");
            communityMapper.insert(community);
        }

        List<UUID> identifiers = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            JsonNode body = objectMapper.readTree(mockMvc.perform(get("/api/v1/communities")
                            .param("keyword", runToken)
                            .param("sort", "communityCode,asc")
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
