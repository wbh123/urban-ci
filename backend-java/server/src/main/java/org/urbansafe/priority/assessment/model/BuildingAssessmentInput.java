package org.urbansafe.priority.assessment.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 第四阶段三个计算器共享的、已经完成业务筛选的统一输入。 */
public record BuildingAssessmentInput(
        BuildingSnapshot building,
        CommunitySnapshot community,
        boolean geometryAvailable,
        List<InspectionEvidence> inspections,
        int availableImageCount,
        List<String> imageParts,
        List<BusinessEvidence> businessEvidence,
        List<FeedbackEvidence> residentReports,
        List<AiEvidence> eligibleAiEvidence,
        List<AiEvidence> excludedAiEvidence,
        List<SpatialMetric> spatialMetrics,
        LocalDate calculationDate) {

    public BuildingAssessmentInput {
        inspections = List.copyOf(inspections);
        imageParts = List.copyOf(imageParts);
        businessEvidence = List.copyOf(businessEvidence);
        residentReports = List.copyOf(residentReports);
        eligibleAiEvidence = List.copyOf(eligibleAiEvidence);
        excludedAiEvidence = List.copyOf(excludedAiEvidence);
        spatialMetrics = List.copyOf(spatialMetrics);
    }

    public record BuildingSnapshot(
            UUID buildingId,
            UUID communityId,
            String buildingCode,
            String buildingName,
            String address,
            Integer constructionYear,
            String structureType,
            Integer floorCount,
            BigDecimal buildingArea,
            Integer householdCount,
            Integer residentCount,
            Integer elderlyCount,
            Integer childCount,
            boolean illegalModification,
            boolean groundFloorBusiness,
            Map<String, Object> extraAttributes) {
    }

    public record CommunitySnapshot(
            UUID communityId,
            String communityCode,
            String communityName,
            String administrativeRegion) {
    }

    public record InspectionEvidence(
            UUID inspectionRecordId,
            OffsetDateTime inspectedAt,
            String inspectionPart,
            String severity,
            boolean persistent,
            boolean worsening,
            Map<String, Object> formData) {
    }

    public record BusinessEvidence(
            UUID evidenceId,
            String evidenceType,
            String reliabilityLevel,
            OffsetDateTime occurredAt,
            BigDecimal score,
            String severity,
            Map<String, Object> evidenceData) {
    }

    public record FeedbackEvidence(
            UUID reportId,
            String reportType,
            String urgency,
            String status,
            OffsetDateTime submittedAt) {
    }

    public record AiEvidence(
            UUID inferenceId,
            String mode,
            String status,
            String reviewStatus,
            String assessmentEligibility,
            String defectType,
            String severity,
            int quantity,
            String part,
            String exclusionReason) {
    }

    public record SpatialMetric(
            String metricCode,
            BigDecimal metricValue,
            String metricText,
            OffsetDateTime calculatedAt,
            OffsetDateTime expiresAt) {
    }
}
