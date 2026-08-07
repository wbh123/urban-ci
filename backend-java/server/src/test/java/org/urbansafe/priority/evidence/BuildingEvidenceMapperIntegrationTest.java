package org.urbansafe.priority.evidence;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.urbansafe.priority.persistence.entity.BuildingEntity;
import org.urbansafe.priority.persistence.mapper.BuildingMapper;
import org.urbansafe.priority.persistence.entity.CommunityEntity;
import org.urbansafe.priority.persistence.mapper.CommunityMapper;
import org.urbansafe.priority.persistence.entity.BuildingEvidenceEntity;
import org.urbansafe.priority.persistence.mapper.BuildingEvidenceMapper;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class BuildingEvidenceMapperIntegrationTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private BuildingEvidenceMapper buildingEvidenceMapper;

    @Autowired
    private BuildingMapper buildingMapper;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanUp() {
        buildingEvidenceMapper.delete(null);
        buildingMapper.delete(null);
        communityMapper.delete(null);
    }

    @Test
    void insertAndReadEvidence() {
        UUID buildingId = createTestBuilding();

        BuildingEvidenceEntity entity = new BuildingEvidenceEntity();
        entity.setId(UUID.randomUUID());
        entity.setBuildingId(buildingId);
        entity.setEvidenceType("MAINTENANCE_RECORD");
        entity.setTitle("外墙裂缝维修记录");
        entity.setDescription("2024年3月发现外墙裂缝并进行维修");
        entity.setOccurredAt(OffsetDateTime.parse("2024-03-15T10:00:00Z"));
        entity.setSource("物业报修系统");
        entity.setReliabilityLevel("PROFESSIONAL_CONFIRMED");

        ObjectNode data = objectMapper.createObjectNode();
        data.put("裂缝长度", "2.5米");
        data.put("维修费用", "15000元");
        entity.setEvidenceData(data);

        buildingEvidenceMapper.insert(entity);

        BuildingEvidenceEntity loaded = buildingEvidenceMapper.selectById(entity.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getEvidenceType()).isEqualTo("MAINTENANCE_RECORD");
        assertThat(loaded.getTitle()).isEqualTo("外墙裂缝维修记录");
        assertThat(loaded.getReliabilityLevel()).isEqualTo("PROFESSIONAL_CONFIRMED");
        assertThat(loaded.getEvidenceData()).isNotNull();
        assertThat(loaded.getEvidenceData().get("裂缝长度").asText()).isEqualTo("2.5米");
    }

    @Test
    void logicalDeleteShouldMakeEvidenceInvisible() {
        UUID buildingId = createTestBuilding();

        BuildingEvidenceEntity entity = new BuildingEvidenceEntity();
        entity.setId(UUID.randomUUID());
        entity.setBuildingId(buildingId);
        entity.setEvidenceType("HISTORICAL_COMPLAINT");
        entity.setTitle("历史投诉记录");
        entity.setReliabilityLevel("UNVERIFIED");
        buildingEvidenceMapper.insert(entity);

        buildingEvidenceMapper.deleteById(entity.getId());

        BuildingEvidenceEntity loaded = buildingEvidenceMapper.selectById(entity.getId());
        assertThat(loaded).isNull();
    }

    @Test
    void evidenceTimestampShouldBeHandledCorrectly() {
        UUID buildingId = createTestBuilding();

        BuildingEvidenceEntity entity = new BuildingEvidenceEntity();
        entity.setId(UUID.randomUUID());
        entity.setBuildingId(buildingId);
        entity.setEvidenceType("ENVIRONMENT_RISK");
        entity.setTitle("环境风险记录");
        entity.setOccurredAt(OffsetDateTime.parse("2024-06-01T08:30:00+08:00"));
        entity.setReliabilityLevel("UNVERIFIED");
        buildingEvidenceMapper.insert(entity);

        BuildingEvidenceEntity loaded = buildingEvidenceMapper.selectById(entity.getId());
        assertThat(loaded).isNotNull();
        assertThat(loaded.getOccurredAt()).isNotNull();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    private UUID createTestBuilding() {
        CommunityEntity community = new CommunityEntity();
        community.setId(UUID.randomUUID());
        community.setCommunityCode("EC001");
        community.setCommunityName("证据测试小区");
        community.setBuildingCount(0);
        community.setHouseholdCount(0);
        community.setResidentCount(0);
        community.setStatus("ACTIVE");
        communityMapper.insert(community);

        BuildingEntity building = new BuildingEntity();
        building.setId(UUID.randomUUID());
        building.setCommunityId(community.getId());
        building.setBuildingCode("EB001");
        building.setHouseholdCount(10);
        building.setResidentCount(30);
        building.setStatus("ACTIVE");
        buildingMapper.insert(building);

        return building.getId();
    }
}
