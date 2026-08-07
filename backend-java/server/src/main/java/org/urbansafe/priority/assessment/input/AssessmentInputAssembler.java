package org.urbansafe.priority.assessment.input;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.AiEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.BuildingSnapshot;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.BusinessEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.CommunitySnapshot;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.FeedbackEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.InspectionEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.SpatialMetric;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

/** 将仓储返回的受控数据组装成三个计算器共享的统一输入。 */
@Component
public class AssessmentInputAssembler {

    private final AssessmentInputRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AssessmentInputAssembler(
            AssessmentInputRepository repository,
            ObjectMapper objectMapper,
            Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BuildingAssessmentInput assemble(UUID buildingId) {
        LocalDate calculationDate = LocalDate.now(clock);
        Map<String, Object> row = repository.findBuilding(buildingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "BUILDING_NOT_FOUND", "楼栋不存在"));

        BuildingSnapshot building = new BuildingSnapshot(
                uuid(row, "buildingId"),
                uuid(row, "communityId"),
                text(row, "buildingCode"),
                text(row, "buildingName"),
                text(row, "address"),
                integer(row, "constructionYear"),
                text(row, "structureType"),
                integer(row, "floorCount"),
                decimal(row, "buildingArea"),
                integer(row, "householdCount"),
                integer(row, "residentCount"),
                integer(row, "elderlyCount"),
                integer(row, "childCount"),
                bool(row, "illegalModification"),
                bool(row, "groundFloorBusiness"),
                jsonMap(row.get("extraAttributes")));

        CommunitySnapshot community = new CommunitySnapshot(
                building.communityId(),
                text(row, "communityCode"),
                text(row, "communityName"),
                text(row, "administrativeRegion"));

        List<InspectionEvidence> inspections = repository.findCompletedInspections(buildingId).stream()
                .map(this::inspection)
                .toList();
        Map<String, Object> imageRow = repository.findAvailableInspectionImages(buildingId);
        Integer imageCount = integer(imageRow, "imageCount");

        return new BuildingAssessmentInput(
                building,
                community,
                repository.hasGeometry(buildingId),
                inspections,
                imageCount == null ? 0 : imageCount,
                jsonStringList(imageRow.get("parts")),
                repository.findBusinessEvidence(buildingId).stream().map(this::businessEvidence).toList(),
                repository.findResidentReports(buildingId, calculationDate).stream().map(this::feedback).toList(),
                repository.findEligibleAiEvidence(buildingId).stream().map(row0 -> ai(row0, true)).toList(),
                repository.findExcludedAiEvidence(buildingId).stream().map(row0 -> ai(row0, false)).toList(),
                repository.findSpatialMetrics(buildingId, calculationDate).stream().map(this::spatial).toList(),
                calculationDate);
    }

    private InspectionEvidence inspection(Map<String, Object> row) {
        Map<String, Object> data = jsonMap(row.get("formData"));
        return new InspectionEvidence(
                uuid(row, "inspectionRecordId"),
                time(row.get("inspectedAt")),
                text(row, "inspectionPart"),
                firstText(data, "severity", "defectSeverity", "maxSeverity"),
                bool(data, "persistent"),
                bool(data, "worsening"),
                data);
    }

    private BusinessEvidence businessEvidence(Map<String, Object> row) {
        Map<String, Object> data = jsonMap(row.get("evidenceData"));
        return new BusinessEvidence(
                uuid(row, "evidenceId"),
                text(row, "evidenceType"),
                text(row, "reliabilityLevel"),
                time(row.get("occurredAt")),
                decimal(data, "score"),
                firstText(data, "severity", "riskSeverity"),
                data);
    }

    private FeedbackEvidence feedback(Map<String, Object> row) {
        return new FeedbackEvidence(
                uuid(row, "reportId"),
                text(row, "reportType"),
                text(row, "urgency"),
                text(row, "status"),
                time(row.get("submittedAt")));
    }

    private AiEvidence ai(Map<String, Object> row, boolean eligible) {
        Integer quantity = integer(row, "quantity");
        return new AiEvidence(
                uuid(row, "inferenceId"),
                text(row, "mode"),
                text(row, "status"),
                text(row, "reviewStatus"),
                text(row, "assessmentEligibility"),
                text(row, "defectType"),
                text(row, "severity"),
                quantity == null ? 0 : quantity,
                text(row, "part"),
                eligible ? null : text(row, "exclusionReason"));
    }

    private SpatialMetric spatial(Map<String, Object> row) {
        return new SpatialMetric(
                text(row, "metricCode"),
                decimal(row, "metricValue"),
                text(row, "metricText"),
                time(row.get("calculatedAt")),
                time(row.get("expiresAt")));
    }

    private Map<String, Object> jsonMap(Object value) {
        if (value == null) return Map.of();
        if (value instanceof Map<?, ?> map) {
            return objectMapper.convertValue(map, new TypeReference<>() {});
        }
        if (value instanceof JsonNode node) {
            return objectMapper.convertValue(node, new TypeReference<>() {});
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<String> jsonStringList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) {
            return list.stream().filter(java.util.Objects::nonNull).map(String::valueOf).toList();
        }
        try {
            return objectMapper.readValue(String.valueOf(value), new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String firstText(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            String value = text(map, key);
            if (value != null && !value.isBlank()) return value;
        }
        return null;
    }

    private String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private UUID uuid(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof UUID uuid ? uuid : value == null ? null : UUID.fromString(String.valueOf(value));
    }

    private Integer integer(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) return null;
        return value instanceof Number number ? number.intValue() : Integer.valueOf(String.valueOf(value));
    }

    private BigDecimal decimal(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null || String.valueOf(value).isBlank()) return null;
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(value));
    }

    private boolean bool(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Boolean bool ? bool : value != null && Boolean.parseBoolean(String.valueOf(value));
    }

    private OffsetDateTime time(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime time) return time;
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        }
        return OffsetDateTime.parse(String.valueOf(value));
    }
}
