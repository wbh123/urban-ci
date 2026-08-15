package org.urbansafe.priority.ai.controller;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.command.RetryCommand;
import org.urbansafe.priority.ai.command.ReviewCommand;
import org.urbansafe.priority.ai.execution.AiExecutionCommand;
import org.urbansafe.priority.ai.execution.AiExecutionTaskQueryService;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.review.AiReviewApplicationService;
import org.urbansafe.priority.ai.service.AiInferenceService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.response.ResponseMetadata;
import org.urbansafe.priority.common.response.ResponseMetadataFactory;

/** 人工智能推理公共接口控制器。 */
@Controller
@ResponseBody
@RequestMapping("/api/v1")
public class AiInferenceController {

    private final AiInferenceService service;
    private final AiExecutionTaskService executionTaskService;
    private final AiExecutionTaskQueryService executionTaskQueryService;
    private final AiReviewApplicationService reviewApplicationService;

    public AiInferenceController(
            AiInferenceService service,
            AiExecutionTaskService executionTaskService,
            AiExecutionTaskQueryService executionTaskQueryService,
            AiReviewApplicationService reviewApplicationService) {
        this.service = service;
        this.executionTaskService = executionTaskService;
        this.executionTaskQueryService = executionTaskQueryService;
        this.reviewApplicationService = reviewApplicationService;
    }

    @PostMapping("/ai-inferences")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String inferenceProfile = string(body.get("inferenceProfile"));
        if ("ACCURACY".equalsIgnoreCase(inferenceProfile)) {
            UUID assetId = uuid(body.get("assetId"));
            String modelId = string(body.get("modelId"));
            String idempotencyKey = string(body.get("idempotencyKey"));
            String triggerType = string(body.get("triggerType"));
            if (triggerType == null) {
                triggerType = "MANUAL_SINGLE";
            }
            if (idempotencyKey == null) {
                idempotencyKey = "accuracy-" + assetId + "-" + modelId + "-" + UUID.randomUUID();
            }
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("inferenceProfile", "ACCURACY");
            inputs.put("triggerType", triggerType.trim().toUpperCase());
            inputs.put("assetId", String.valueOf(assetId));
            UUID taskId = executionTaskService.enqueue(new AiExecutionCommand(
                    assetId,
                    modelId,
                    string(body.get("mode")),
                    modelId,
                    string(body.get("providerCode")),
                    string(body.get("capabilityType")),
                    string(body.get("prompt")),
                    idempotencyKey,
                    CurrentUser.getUserId(),
                    inputs));
            Map<String, Object> queued = new LinkedHashMap<>();
            queued.put("taskId", taskId);
            queued.put("status", "PENDING");
            queued.put("inferenceProfile", "ACCURACY");
            queued.put("triggerType", triggerType.trim().toUpperCase());
            queued.put("assetId", assetId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(success(queued));
        }

        CreateInferenceCommand command = new CreateInferenceCommand(
                uuid(body.get("assetId")),
                string(body.get("mode")),
                string(body.get("modelId")),
                string(body.get("providerCode")),
                string(body.get("capabilityType")),
                string(body.get("prompt")),
                inferenceProfile,
                string(body.get("idempotencyKey")),
                CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(success(service.create(command)));
    }

    @GetMapping("/ai-inference-executions")
    public ResponseEntity<Map<String, Object>> listExecutions(
            @RequestParam UUID inspectionTaskId) {
        return ResponseEntity.ok(success(
                executionTaskQueryService.listByInspectionTask(inspectionTaskId)));
    }

    @GetMapping("/ai-inference-executions/{taskId}")
    public ResponseEntity<Map<String, Object>> getExecution(@PathVariable UUID taskId) {
        return ResponseEntity.ok(success(executionTaskQueryService.get(taskId)));
    }

    @GetMapping("/ai-inferences/{inferenceId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable UUID inferenceId) {
        return ResponseEntity.ok(success(reviewApplicationService.enrichDetail(
                service.getDetail(inferenceId), inferenceId)));
    }

    @GetMapping("/ai-inferences")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) String modelId,
            @RequestParam(required = false) String providerCode,
            @RequestParam(required = false) String capabilityType,
            @RequestParam(required = false) UUID assetId,
            @RequestParam(required = false) UUID inspectionTaskId,
            @RequestParam(required = false) UUID inspectionRecordId,
            @RequestParam(required = false) UUID buildingId,
            @RequestParam(required = false) UUID communityId) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", status);
        filters.put("mode", mode);
        filters.put("modelId", modelId);
        filters.put("providerCode", providerCode);
        filters.put("capabilityType", capabilityType);
        filters.put("assetId", assetId);
        filters.put("inspectionTaskId", inspectionTaskId);
        filters.put("inspectionRecordId", inspectionRecordId);
        filters.put("buildingId", buildingId);
        filters.put("communityId", communityId);
        return ResponseEntity.ok(success(service.list(filters, page, size)));
    }

    @PostMapping("/ai-inferences/{inferenceId}/retry")
    public ResponseEntity<Map<String, Object>> retry(
            @PathVariable UUID inferenceId,
            @RequestBody(required = false) Map<String, Object> body) {
        RetryCommand command = new RetryCommand(
                inferenceId,
                body == null ? null : string(body.get("modelId")),
                CurrentUser.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(success(service.retry(command)));
    }

    @PostMapping("/ai-inferences/{inferenceId}/review")
    @PreAuthorize("hasAnyRole('EXPERT', 'PROFESSIONAL_REVIEWER', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> review(
            @PathVariable UUID inferenceId,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> correctedData = map(body.get("correctedData"));
        if (correctedData.isEmpty() && body.get("reviewedRiskLevel") != null) {
            correctedData = Map.of("reviewedRiskLevel", body.get("reviewedRiskLevel"));
        }
        ReviewCommand command = new ReviewCommand(
                inferenceId,
                string(body.get("reviewStatus")),
                string(body.get("comment")),
                CurrentUser.getUserId(),
                correctedData);
        return ResponseEntity.ok(success(reviewApplicationService.review(command)));
    }

    private static Map<String, Object> success(Object data) {
        ResponseMetadata metadata = ResponseMetadataFactory.success();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", metadata.success());
        body.put("data", data);
        body.put("error", null);
        body.put("requestId", metadata.requestId());
        body.put("timestamp", metadata.timestamp());
        return body;
    }

    private static UUID uuid(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return UUID.fromString(String.valueOf(value));
    }

    private static String string(Object value) {
        if (value == null || String.valueOf(value).isBlank()) return null;
        return String.valueOf(value).trim();
    }

    private static Map<String, Object> map(Object value) {
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, entryValue) -> {
            if (key != null) result.put(String.valueOf(key), entryValue);
        });
        return result;
    }
}
