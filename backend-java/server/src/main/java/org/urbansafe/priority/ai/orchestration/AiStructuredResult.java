package org.urbansafe.priority.ai.orchestration;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import java.util.List;
import org.apache.ibatis.type.Alias;

/**
 * 项目统一结构化人工智能结果基类，不直接暴露供应商原始响应。
 *
 * <p>显式类型别名用于避免与 OpenAPI 生成的同名数据传输对象发生 MyBatis 别名冲突。
 * 访问器采用 {@code requestId()} 等非 Bean 命名，Jackson 无法按 getter 识别属性，
 * 必须显式允许字段可见性，才能将结构化结果序列化为 ai.inference_result.structured_result 快照。
 */
@Alias("Phase7AiStructuredResult")
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        getterVisibility = JsonAutoDetect.Visibility.NONE,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class AiStructuredResult {

    private final String requestId;
    private final String providerCode;
    private final String modelCode;
    private final String modelVersion;
    private final AiCapabilityType capabilityType;
    private final String status;
    private final String summary;
    private final List<Detection> detections;
    private final List<RiskSignal> riskSignals;
    private final List<String> recommendations;
    private final Double confidence;
    private final List<String> warnings;
    private final String rawResponseReference;
    private final long durationMs;

    public AiStructuredResult(
            String requestId,
            String providerCode,
            String modelCode,
            String modelVersion,
            AiCapabilityType capabilityType,
            String status,
            String summary,
            List<Detection> detections,
            List<RiskSignal> riskSignals,
            List<String> recommendations,
            Double confidence,
            List<String> warnings,
            String rawResponseReference,
            long durationMs) {
        this.requestId = requestId;
        this.providerCode = providerCode;
        this.modelCode = modelCode;
        this.modelVersion = modelVersion;
        this.capabilityType = capabilityType;
        this.status = status;
        this.summary = summary;
        this.detections = detections == null ? List.of() : List.copyOf(detections);
        this.riskSignals = riskSignals == null ? List.of() : List.copyOf(riskSignals);
        this.recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        this.confidence = confidence;
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.rawResponseReference = rawResponseReference;
        this.durationMs = durationMs;
    }

    public String requestId() { return requestId; }
    public String providerCode() { return providerCode; }
    public String modelCode() { return modelCode; }
    public String modelVersion() { return modelVersion; }
    public AiCapabilityType capabilityType() { return capabilityType; }
    public String status() { return status; }
    public String summary() { return summary; }
    public List<Detection> detections() { return detections; }
    public List<RiskSignal> riskSignals() { return riskSignals; }
    public List<String> recommendations() { return recommendations; }
    public Double confidence() { return confidence; }
    public List<String> warnings() { return warnings; }
    public String rawResponseReference() { return rawResponseReference; }
    public long durationMs() { return durationMs; }

    public record Detection(
            String classCode,
            String className,
            Double confidence,
            BoundingBox boundingBox) {
    }

    public record BoundingBox(
            Double x,
            Double y,
            Double width,
            Double height,
            String coordinateType) {
    }

    public record RiskSignal(
            String code,
            String level,
            String description,
            Double confidence) {
    }
}
