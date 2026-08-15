package org.urbansafe.priority.feedback.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.orchestration.SpringAiOrchestrationService;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.feedback.repository.FeedbackAiAssistQueryRepository;

/**
 * 面向管理端的公众反馈 AI 初步归类。
 *
 * <p>服务只读取反馈事实，不写入反馈状态、处理摘要或正式风险数据。文本智能能力可用时
 * 使用受控 Spring AI 编排；不可用时退回确定性关键词分类，保证基础分流能力可用。
 */
@Service
public class FeedbackAiAssistService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackAiAssistService.class);
    private static final String DISCLAIMER =
            "AI 初步归类仅用于辅助分流，不会自动修改反馈状态或替代人工处理。";

    private final FeedbackAiAssistQueryRepository repository;
    private final SpringAiOrchestrationService orchestrationService;

    public FeedbackAiAssistService(
            FeedbackAiAssistQueryRepository repository,
            SpringAiOrchestrationService orchestrationService) {
        this.repository = repository;
        this.orchestrationService = orchestrationService;
    }

    public Map<String, Object> analyze(UUID reportId, UUID requestedBy, String requestedByName) {
        Map<String, Object> report = repository.findReport(reportId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FEEDBACK_REPORT_NOT_FOUND", "公众反馈不存在或已删除"));
        UUID buildingId = uuid(report.get("buildingId"));
        String question = buildQuestion(report);

        try {
            SpringAiOrchestrationService.IntelligentAnalysisResult result =
                    orchestrationService.runIntelligentAnalysis(
                            "FEEDBACK",
                            buildingId,
                            question,
                            Map.of(),
                            requestedBy,
                            requestedByName);
            if (result.answer() == null
                    || result.answer().isBlank()
                    || result.modelCode() == null
                    || "FAILED".equals(result.status().name())) {
                return deterministicFallback(reportId, report);
            }
            return aiResult(reportId, report, result);
        } catch (RuntimeException ex) {
            log.warn("公众反馈文本智能归类不可用，切换确定性基础归类：{}", ex.getMessage());
            return deterministicFallback(reportId, report);
        }
    }

    private static Map<String, Object> aiResult(
            UUID reportId,
            Map<String, Object> report,
            SpringAiOrchestrationService.IntelligentAnalysisResult result) {
        String answer = result.answer();
        Map<String, Object> data = commonFields(reportId, report);
        data.put("status", result.status().name());
        data.put("answer", answer);
        data.put("durationMs", result.durationMs());
        data.put("modelCode", result.modelCode());
        data.put("fallback", false);
        data.put("category", labeledValue(answer, "初步类别", "AI 文本辅助归类"));
        data.put("relatedObject", labeledValue(
                answer, "建议关联对象", relatedObject(report)));
        data.put("recommendedAction", labeledValue(answer, "建议动作", "人工确认后处理"));
        data.put("basis", "基于当前反馈描述、位置、空间关联与受控文本智能分析生成。");
        data.put("disclaimer", DISCLAIMER);
        return data;
    }

    private static Map<String, Object> deterministicFallback(UUID reportId, Map<String, Object> report) {
        FallbackClassification classification = classify(report);
        String answer = """
                初步类别：%s
                建议关联对象：%s
                建议动作：%s。%s
                需人工确认：%s
                """.formatted(
                classification.category(),
                classification.relatedObject(),
                classification.recommendedAction(),
                classification.basis(),
                classification.confirmation());

        Map<String, Object> data = commonFields(reportId, report);
        data.put("status", "PARTIAL_SUCCEEDED");
        data.put("answer", answer.strip());
        data.put("durationMs", 0L);
        data.put("modelCode", null);
        data.put("fallback", true);
        data.put("category", classification.category());
        data.put("relatedObject", classification.relatedObject());
        data.put("recommendedAction", classification.recommendedAction());
        data.put("basis", classification.basis());
        data.put("disclaimer", "智能文本能力暂不可用，当前展示基础规则辅助归类。" + DISCLAIMER);
        return data;
    }

    private static FallbackClassification classify(Map<String, Object> report) {
        String reportType = text(report.get("reportType"), "").toUpperCase(Locale.ROOT);
        String description = text(report.get("description"), "");
        String location = text(report.get("locationText"), "");
        String haystack = (reportType + " " + description + " " + location).toLowerCase(Locale.ROOT);
        List<String> evidence = new ArrayList<>();

        boolean crack = containsAny(haystack, "wall_crack", "裂缝", "开裂", "裂纹");
        boolean water = containsAny(haystack, "water_leakage", "渗水", "漏水", "渗漏", "水渍");
        boolean spalling = containsAny(haystack, "surface_falling", "脱落", "剥落", "掉落", "外墙空鼓");
        boolean rebar = containsAny(haystack, "露筋", "钢筋", "锈蚀", "锈迹", "腐蚀");
        boolean deformation = containsAny(haystack, "变形", "倾斜", "沉降", "下沉", "鼓胀");

        if (crack) evidence.add("裂缝/开裂");
        if (water) evidence.add("渗水/漏水");
        if (spalling) evidence.add("表面脱落/剥落");
        if (rebar) evidence.add("钢筋/锈蚀");
        if (deformation) evidence.add("变形/沉降");

        String category;
        String action;
        if (crack && water) {
            category = "疑似外墙裂缝/渗水问题";
            action = "安排巡检";
        } else if (crack) {
            category = "疑似裂缝问题";
            action = "安排巡检";
        } else if (spalling) {
            category = "疑似外墙表面脱落问题";
            action = "安排巡检";
        } else if (water) {
            category = "疑似渗漏问题";
            action = "安排巡检";
        } else if (rebar) {
            category = "疑似钢筋锈蚀/露筋问题";
            action = "转专业复核";
        } else if (deformation) {
            category = "疑似结构变形问题";
            action = "转专业复核";
        } else {
            category = "其他建筑安全线索";
            action = "HIGH".equalsIgnoreCase(text(report.get("urgency"), ""))
                    || "URGENT".equalsIgnoreCase(text(report.get("urgency"), ""))
                    ? "安排巡检" : "常规处理";
        }

        String basis = evidence.isEmpty()
                ? "未命中明确病害关键词，依据现有问题描述和紧急程度进行基础分流。"
                : "依据反馈描述关键词：" + String.join("、", evidence) + "。";
        if (!location.isBlank()) {
            basis += " 反馈位置：" + location + "。";
        }

        return new FallbackClassification(
                category,
                relatedObject(report),
                action,
                basis,
                "具体病害范围、严重程度及是否需要专业处置仍需结合现场证据核实。"
        );
    }

    private static Map<String, Object> commonFields(UUID reportId, Map<String, Object> report) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportId", reportId);
        data.put("reportCode", text(report.get("reportCode"), ""));
        data.put("communityName", text(report.get("communityName"), ""));
        data.put("buildingName", text(report.get("buildingName"), ""));
        return data;
    }

    private static String relatedObject(Map<String, Object> report) {
        String buildingName = text(report.get("buildingName"), "");
        if (!buildingName.isBlank()) return buildingName;
        String communityName = text(report.get("communityName"), "当前小区");
        return communityName + "（具体楼栋需人工确认）";
    }

    private static String labeledValue(String answer, String label, String fallback) {
        if (answer == null || answer.isBlank()) return fallback;
        for (String rawLine : answer.split("\\R")) {
            String line = rawLine.strip().replaceFirst("^\\d+[.、)]\\s*", "");
            if (!line.startsWith(label)) continue;
            int index = Math.max(line.indexOf('：'), line.indexOf(':'));
            if (index >= 0 && index + 1 < line.length()) {
                String value = line.substring(index + 1).strip();
                if (!value.isBlank()) return value;
            }
        }
        return fallback;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    static String buildQuestion(Map<String, Object> report) {
        return """
                请对以下公众反馈做管理端初步归类，仅基于已提供事实提出辅助建议，不得编造现场事实。
                反馈编号：%s
                当前类型：%s
                紧急程度：%s
                所属小区：%s
                关联楼栋：%s
                位置描述：%s
                问题描述：%s

                请严格按以下四个业务部分组织回答：
                1. 初步类别：用一句话概括问题类别；
                2. 建议关联对象：说明现有关联楼栋是否足够，若无法判断应明确写“需人工确认”；
                3. 建议动作：优先在“安排巡检、补充信息、转专业复核、常规处理”中给出建议，并说明原因；
                4. 需人工确认：列出仍需核实的关键事实。

                该结果不得自动修改反馈状态、正式风险等级或专业结论。
                """.formatted(
                text(report.get("reportCode"), "未提供"),
                text(report.get("reportType"), "未分类"),
                text(report.get("urgency"), "未标注"),
                text(report.get("communityName"), "未提供"),
                text(report.get("buildingName"), "未关联楼栋"),
                text(report.get("locationText"), "未提供"),
                text(report.get("description"), "未提供"));
    }

    private static String text(Object value, String fallback) {
        if (value == null || String.valueOf(value).isBlank()) return fallback;
        return String.valueOf(value).trim();
    }

    private static UUID uuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private record FallbackClassification(
            String category,
            String relatedObject,
            String recommendedAction,
            String basis,
            String confirmation) {
    }
}
