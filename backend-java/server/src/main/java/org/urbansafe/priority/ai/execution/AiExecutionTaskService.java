package org.urbansafe.priority.ai.execution;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.service.AiInferenceService;

/** 入队并执行持久化人工智能任务，复用现有推理、结果和审计链路。 */
@Service
public class AiExecutionTaskService {

    private static final Set<String> RETRYABLE_INFERENCE_ERROR_CODES = Set.of(
            AiErrorCodes.AI_PROVIDER_TIMEOUT,
            AiErrorCodes.AI_PROVIDER_UNAVAILABLE);

    private final AiExecutionTaskRepository repository;
    private final AiInferenceService inferenceService;
    private final AiExecutionProperties properties;

    public AiExecutionTaskService(
            AiExecutionTaskRepository repository,
            AiInferenceService inferenceService,
            AiExecutionProperties properties) {
        this.repository = repository;
        this.inferenceService = inferenceService;
        this.properties = properties;
    }

    public UUID enqueue(AiExecutionCommand command) {
        return repository.enqueue(command);
    }

    public void executeClaimed(AiExecutionTask task) {
        try {
            Map<String, Object> result = inferenceService.create(new CreateInferenceCommand(
                    task.assetId(), task.mode(), task.modelId(), task.providerCode(),
                    task.capabilityType(), task.prompt(), task.idempotencyKey(), task.requestedBy()));
            String status = string(result.get("status"));
            UUID inferenceId = uuid(result.get("inferenceId"));
            if ("SUCCEEDED".equals(status)) {
                repository.markSucceeded(task.id(), inferenceId);
                return;
            }
            if ("REJECTED".equals(status)) {
                repository.markRejected(task.id(), inferenceId, string(result.get("errorMessage")));
                return;
            }
            String errorCode = firstNonBlank(string(result.get("errorCode")), "AI_INFERENCE_FAILED");
            handleFailure(task,
                    errorCode,
                    firstNonBlank(string(result.get("errorMessage")), "人工智能推理失败"),
                    RETRYABLE_INFERENCE_ERROR_CODES.contains(errorCode));
        } catch (RuntimeException ex) {
            handleFailure(task, "AI_EXECUTION_UNEXPECTED", safeMessage(ex), true);
        }
    }

    public void recoverExpiredLeases() {
        repository.recoverExpiredLeases();
    }

    private void handleFailure(AiExecutionTask task, String code, String message, boolean retryable) {
        if (!retryable || task.attemptCount() >= task.maxAttempts()) {
            repository.markFailed(task.id(), code, message);
            return;
        }
        long multiplier = 1L << Math.max(0, task.attemptCount() - 1);
        long delay = Math.min(900L, properties.getRetryBaseDelaySeconds() * multiplier);
        repository.markRetry(task.id(), OffsetDateTime.now().plusSeconds(delay), code, message);
    }

    private static String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private static UUID uuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        return value == null || String.valueOf(value).isBlank()
                ? null : UUID.fromString(String.valueOf(value));
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String safeMessage(RuntimeException ex) {
        return ex.getMessage() == null || ex.getMessage().isBlank()
                ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
