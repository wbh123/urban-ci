package org.urbansafe.priority.ai.execution;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Spring AI 智能编排执行轨迹（只含工具调用与最终状态，不含模型私有思维过程）。 */
public class AiAgentExecution {

    private UUID id;
    private final String businessType;
    private final UUID businessId;
    private final String question;
    private AiAgentExecutionStatus status = AiAgentExecutionStatus.PENDING;
    private final UUID requestedBy;
    private final String requestedByName;
    private String modelCode;
    private Instant startedAt = Instant.now();
    private Instant finishedAt;
    private Long durationMs;
    private String summary;
    private String errorCode;
    private String errorMessage;
    private final List<AiAgentExecutionStep> steps = new ArrayList<>();

    public AiAgentExecution(
            UUID id,
            String businessType,
            UUID businessId,
            String question,
            UUID requestedBy,
            String requestedByName) {
        this.id = id;
        this.businessType = businessType;
        this.businessId = businessId;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedByName = requestedByName;
    }

    public int nextSeq() {
        return steps.size() + 1;
    }

    public void addStep(AiAgentExecutionStep step) {
        steps.add(step);
    }

    public List<AiAgentExecutionStep> steps() {
        return List.copyOf(steps);
    }

    // accessors
    public UUID id() { return id; }
    public String businessType() { return businessType; }
    public UUID businessId() { return businessId; }
    public String question() { return question; }
    public AiAgentExecutionStatus status() { return status; }
    public UUID requestedBy() { return requestedBy; }
    public String requestedByName() { return requestedByName; }
    public String modelCode() { return modelCode; }
    public Instant startedAt() { return startedAt; }
    public Instant finishedAt() { return finishedAt; }
    public Long durationMs() { return durationMs; }
    public String summary() { return summary; }
    public String errorCode() { return errorCode; }
    public String errorMessage() { return errorMessage; }

    public void setId(UUID id) { this.id = id; }
    public void setStatus(AiAgentExecutionStatus status) { this.status = status; }
    public void setModelCode(String modelCode) { this.modelCode = modelCode; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }
    public void setSummary(String summary) { this.summary = summary; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
