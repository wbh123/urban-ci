package org.urbansafe.priority.assessment.calculator;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.assessment.model.AssessmentResults.CompletenessResult;
import org.urbansafe.priority.assessment.model.AssessmentResults.Dimension;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.BusinessEvidence;
import org.urbansafe.priority.assessment.model.BuildingAssessmentInput.InspectionEvidence;
import org.urbansafe.priority.assessment.model.RuleSnapshot;
import org.urbansafe.priority.assessment.rule.RuleAccess;

/** 数据完整度 V1 纯计算器。 */
@Component
public class CompletenessCalculator {

    public CompletenessResult calculate(BuildingAssessmentInput input, RuleSnapshot rule) {
        RuleAccess access = new RuleAccess(rule);
        List<String> available = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();

        BigDecimal archive = archiveScore(input, access, available, missing);
        BigDecimal inspection = inspectionScore(input, access, available, missing);
        BigDecimal images = imageScore(input, access, available, missing);
        BigDecimal maintenance = evidenceCoverage(input, access, "MAINTENANCE_RECORD", available, missing);
        BigDecimal professional = evidenceCoverage(input, access, "PROFESSIONAL_INSPECTION", available, missing);

        if (inspection.compareTo(BigDecimal.ZERO) == 0) {
            suggestions.add("补充有效已完成的现场巡检记录");
        }
        if (images.compareTo(BigDecimal.valueOf(70)) < 0) {
            suggestions.add("补拍并覆盖至少两个关键部位的现场图片");
        }
        if (maintenance.compareTo(BigDecimal.ZERO) == 0) {
            suggestions.add("补充维修、加固或改造记录");
        }
        if (professional.compareTo(BigDecimal.ZERO) == 0) {
            suggestions.add("根据风险筛查结果安排第三方专业检测");
        }

        List<Dimension> dimensions = List.of(
                dimension(access, "BASIC_ARCHIVE", archive, archive.intValue() == 100 ? "COMPLETE" : "PARTIAL", archive.intValue() > 0 ? 1 : 0),
                dimension(access, "RECENT_INSPECTION", inspection, inspection.intValue() > 0 ? "AVAILABLE" : "MISSING", input.inspections().size()),
                dimension(access, "IMAGE_COVERAGE", images, images.intValue() > 0 ? "AVAILABLE" : "MISSING", input.availableImageCount()),
                dimension(access, "MAINTENANCE_RECORD", maintenance, maintenance.intValue() > 0 ? "AVAILABLE" : "MISSING", countEvidence(input, "MAINTENANCE_RECORD")),
                dimension(access, "PROFESSIONAL_INSPECTION", professional, professional.intValue() > 0 ? "AVAILABLE" : "MISSING", countEvidence(input, "PROFESSIONAL_INSPECTION")));

        BigDecimal total = dimensions.stream()
                .map(Dimension::contribution)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal output = AssessmentMath.output(total);
        return new CompletenessResult(
                output,
                access.level(output),
                dimensions,
                List.copyOf(available),
                List.copyOf(missing),
                List.copyOf(suggestions));
    }

    private BigDecimal archiveScore(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<String> available,
            List<String> missing) {
        Map<String, Boolean> fields = new LinkedHashMap<>();
        var building = input.building();
        int currentYear = input.calculationDate().getYear();
        fields.put("constructionYear", building.constructionYear() != null
                && building.constructionYear() >= 1800 && building.constructionYear() <= currentYear);
        fields.put("structureType", text(building.structureType()));
        fields.put("floorCount", building.floorCount() != null && building.floorCount() > 0);
        fields.put("buildingArea", building.buildingArea() != null
                && building.buildingArea().compareTo(BigDecimal.ZERO) > 0);
        fields.put("householdCount", building.householdCount() != null && building.householdCount() >= 0);
        fields.put("residentCount", building.residentCount() != null && building.residentCount() >= 0);
        fields.put("address", text(building.address()));
        fields.put("geometry", input.geometryAvailable());

        BigDecimal score = BigDecimal.ZERO;
        for (Map.Entry<String, Boolean> field : fields.entrySet()) {
            int weight = access.mapInt("/fieldWeights", field.getKey(), 0);
            if (Boolean.TRUE.equals(field.getValue())) {
                score = score.add(BigDecimal.valueOf(weight));
                available.add("基础档案：" + field.getKey());
            } else {
                missing.add("基础档案：" + field.getKey());
            }
        }
        return AssessmentMath.output(score);
    }

    private BigDecimal inspectionScore(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<String> available,
            List<String> missing) {
        OffsetDateTime latest = input.inspections().stream()
                .map(InspectionEvidence::inspectedAt)
                .filter(java.util.Objects::nonNull)
                .max(OffsetDateTime::compareTo)
                .orElse(null);
        if (latest == null) {
            missing.add("有效已完成巡检");
            return BigDecimal.ZERO.setScale(2);
        }
        long days = Math.max(0, ChronoUnit.DAYS.between(latest.toLocalDate(), input.calculationDate()));
        available.add("最近巡检：" + latest.toLocalDate());
        return AssessmentMath.output(inspectionRecencyScore(access, days));
    }

    private BigDecimal imageScore(
            BuildingAssessmentInput input,
            RuleAccess access,
            List<String> available,
            List<String> missing) {
        int images = input.availableImageCount();
        int parts = (int) input.imageParts().stream().filter(CompletenessCalculator::text).distinct().count();
        BigDecimal score = imageCoverageScore(access, images, parts);
        if (score.compareTo(BigDecimal.ZERO) > 0) {
            available.add("有效巡检图片 " + images + " 张，覆盖 " + parts + " 个部位");
        } else {
            missing.add("可用且绑定有效巡检记录的图片");
        }
        return AssessmentMath.output(score);
    }

    private BigDecimal evidenceCoverage(
            BuildingAssessmentInput input,
            RuleAccess access,
            String type,
            List<String> available,
            List<String> missing) {
        List<BusinessEvidence> evidence = input.businessEvidence().stream()
                .filter(item -> type.equals(item.evidenceType()))
                .toList();
        if (evidence.isEmpty()) {
            missing.add(evidenceLabel(type));
            return evidenceScore(access, "/evidenceCoverage/missing", "0");
        }
        boolean verified = evidence.stream()
                .anyMatch(item -> item.reliabilityLevel() != null
                        && item.reliabilityLevel().toUpperCase().contains("VERIFIED"));
        int validYears = validYears(access, type);
        boolean recent = evidence.stream()
                .map(BusinessEvidence::occurredAt)
                .filter(java.util.Objects::nonNull)
                .anyMatch(time -> !time.toLocalDate().isBefore(input.calculationDate().minusYears(validYears)));
        BigDecimal score = verified && recent
                ? evidenceScore(access, "/evidenceCoverage/verifiedRecent", "100")
                : verified
                        ? evidenceScore(access, "/evidenceCoverage/verifiedOld", "80")
                        : evidenceScore(access, "/evidenceCoverage/unverified", "40");
        available.add(evidenceLabel(type) + " " + evidence.size() + " 条");
        return AssessmentMath.output(score);
    }

    private BigDecimal inspectionRecencyScore(RuleAccess access, long days) {
        JsonNode rules = access.snapshot().ruleContent().path("inspectionRecency");
        if (!rules.isArray() || rules.isEmpty()) {
            int score = days <= 180 ? 100 : days <= 365 ? 80 : days <= 730 ? 50 : 20;
            return BigDecimal.valueOf(score);
        }
        BigDecimal fallback = null;
        for (JsonNode rule : rules) {
            if (rule.has("fallbackScore")) {
                fallback = decimal(rule.path("fallbackScore"));
            }
            if (rule.has("maxDays") && days <= rule.path("maxDays").asLong()) {
                return decimal(rule.path("score"));
            }
        }
        return fallback == null ? BigDecimal.ZERO : fallback;
    }

    private BigDecimal imageCoverageScore(RuleAccess access, int images, int parts) {
        JsonNode rules = access.snapshot().ruleContent().path("imageCoverage");
        if (!rules.isArray() || rules.isEmpty()) {
            int score = images >= 4 && parts >= 3 ? 100
                    : images >= 2 && parts >= 2 ? 70
                    : images >= 1 ? 40 : 0;
            return BigDecimal.valueOf(score);
        }
        BigDecimal matched = null;
        for (JsonNode rule : rules) {
            int minImages = rule.path("minImages").asInt(0);
            int minParts = rule.path("minParts").asInt(0);
            if (images >= minImages && parts >= minParts) {
                BigDecimal score = decimal(rule.path("score"));
                if (matched == null || score.compareTo(matched) > 0) {
                    matched = score;
                }
            }
        }
        return matched == null ? BigDecimal.ZERO : matched;
    }

    private int validYears(RuleAccess access, String type) {
        int legacyDefault = access.integer("/evidenceCoverage/recentYears", 5);
        return switch (type) {
            case "MAINTENANCE_RECORD" -> access.integer("/evidenceRecency/maintenanceYears", legacyDefault);
            case "PROFESSIONAL_INSPECTION" -> access.integer("/evidenceRecency/professionalInspectionYears", legacyDefault);
            default -> legacyDefault;
        };
    }

    private BigDecimal evidenceScore(RuleAccess access, String pointer, String fallback) {
        return AssessmentMath.output(access.decimal(pointer, fallback));
    }

    private BigDecimal decimal(JsonNode node) {
        try {
            return new BigDecimal(node.asText());
        } catch (RuntimeException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Dimension dimension(
            RuleAccess access, String code, BigDecimal score, String status, int evidenceCount) {
        BigDecimal weight = access.dimensionWeight(code);
        return new Dimension(
                code,
                access.dimensionLabel(code),
                AssessmentMath.output(score),
                weight,
                AssessmentMath.output(AssessmentMath.weighted(score, weight)),
                status,
                evidenceCount);
    }

    private int countEvidence(BuildingAssessmentInput input, String type) {
        return (int) input.businessEvidence().stream()
                .filter(item -> type.equals(item.evidenceType()))
                .count();
    }

    private static String evidenceLabel(String type) {
        return switch (type) {
            case "MAINTENANCE_RECORD" -> "维修资料";
            case "PROFESSIONAL_INSPECTION" -> "有效专业检测资料";
            default -> type;
        };
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }
}
