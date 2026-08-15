package org.urbansafe.priority.ai.service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.command.CreateInferenceCommand;
import org.urbansafe.priority.ai.command.RetryCommand;
import org.urbansafe.priority.ai.command.ReviewCommand;
import org.urbansafe.priority.ai.config.AiInferenceProperties;
import org.urbansafe.priority.ai.converter.AiInferenceConverter;
import org.urbansafe.priority.ai.orchestration.AiCapabilityType;
import org.urbansafe.priority.ai.orchestration.AiErrorCodes;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationProperties;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationRequest;
import org.urbansafe.priority.ai.orchestration.AiOrchestrationService;
import org.urbansafe.priority.ai.orchestration.AiStructuredResult;
import org.urbansafe.priority.ai.provider.AiInferenceProvider;
import org.urbansafe.priority.ai.provider.AiProviderException;
import org.urbansafe.priority.ai.repository.AiInferenceRepository;
import org.urbansafe.priority.ai.repository.AiOrchestrationRepository;
import org.urbansafe.priority.asset.service.Phase2AssetService;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.common.exception.InvalidRequestException;
import org.urbansafe.priority.common.exception.ResourceConflictException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;
import org.urbansafe.priority.common.request.RequestContext;

/**
 * 人工智能推理业务编排服务。
 *
 * <p>Spring 容器使用第七阶段统一编排服务；保留旧构造器仅用于冻结阶段回归测试。
 */
@Service
public class AiInferenceService {

    private static final List<String> STATUSES = List.of(
            "PENDING", "RUNNING", "SUCCEEDED", "FAILED", "REJECTED", "CANCELLED");
    private static final List<String> MODES = List.of("MOCK", "REAL");
    private static final List<String> INFERENCE_PROFILES = List.of("FAST", "PRECISION");
    private static final List<String> REVIEW_STATUSES = List.of("CONFIRMED", "CORRECTED", "REJECTED");

    private final AiInferenceRepository repository;
    private final AiOrchestrationRepository orchestrationRepository;
    private final AiInferenceProvider legacyInferenceProvider;
    private final AiOrchestrationService orchestrationService;
    private final Phase2AssetService assetService;
    private final AuditService auditService;
    private final AiInferenceProperties properties;
    private final AiOrchestrationProperties orchestrationProperties;

    @Autowired
    public AiInferenceService(
            AiInferenceRepository repository,
            AiOrchestrationRepository orchestrationRepository,
            AiInferenceProvider legacyInferenceProvider,
            AiOrchestrationService orchestrationService,
            Phase2AssetService assetService,
            AuditService auditService,
            AiInferenceProperties properties,
            AiOrchestrationProperties orchestrationProperties) {
        this.repository = repository;
        this.orchestrationRepository = orchestrationRepository;
        this.legacyInferenceProvider = legacyInferenceProvider;
        this.orchestrationService = orchestrationService;
        this.assetService = assetService;
        this.auditService = auditService;
        this.properties = properties;
        this.orchestrationProperties = orchestrationProperties;
    }

    /** 第六阶段回归测试兼容构造器。运行时不会使用此构造器。 */
    public AiInferenceService(
            AiInferenceRepository repository,
            AiInferenceProvider inferenceProvider,
            Phase2AssetService assetService,
            AuditService auditService,
            AiInferenceProperties properties) {
        this.repository = repository;
        this.orchestrationRepository = null;
        this.legacyInferenceProvider = inferenceProvider;
        this.orchestrationService = null;
        this.assetService = assetService;
        this.auditService = auditService;
        this.properties = properties;
        this.orchestrationProperties = null;
    }

    /** 创建推理任务并同步调用统一人工智能编排服务。 */
    public Map<String, Object> create(CreateInferenceCommand command) {
        String mode = normalizeMode(command.mode());
        String inferenceProfile = normalizeInferenceProfile(command.inferenceProfile());
        Map<String, Object> asset = loadAsset(command.assetId());
        AiCapabilityType capabilityType = normalizeCapability(command.capabilityType());
        String providerCode = effectiveProvider(command.providerCode(), capabilityType);
        Map<String, Object> model = resolveModel(mode, command.modelId());
        Map<String, Object> trace = repository
                .resolveAssetTraceability(command.assetId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_ASSET_NOT_FOUND", "图片未绑定可追溯的巡检任务、记录或楼栋"));
        byte[] imageBytes = readAssetBytes(command.assetId());

        UUID taskId = UUID.randomUUID();
        String requestCode = generateRequestCode();
        UUID modelRegistryId = (UUID) model.get("id");
        UUID inspectionTaskId = (UUID) trace.get("inspectionTaskId");
        UUID inspectionRecordId = (UUID) trace.get("inspectionRecordId");
        UUID buildingId = (UUID) trace.get("buildingId");
        UUID communityId = (UUID) trace.get("communityId");

        try {
            repository.insertTask(taskId, requestCode, command.idempotencyKey(), command.assetId(),
                    inspectionTaskId, inspectionRecordId, buildingId, communityId,
                    modelRegistryId, mode, 1, command.requestedBy());
        } catch (DuplicateKeyException ex) {
            return existingActiveTask(command, mode, modelRegistryId);
        }

        if (orchestrationService == null) {
            executeLegacyInference(taskId, command.assetId(), mode, model, imageBytes, requestCode);
        } else {
            recordRouting(taskId, providerCode, capabilityType, model);
            executeOrchestratedInference(
                    taskId, command.assetId(), mode, model, imageBytes,
                    String.valueOf(asset.get("contentType")), requestCode,
                    providerCode, capabilityType, command.prompt(), inferenceProfile);
        }
        audit(taskId, command.assetId(), requestCode, "AI_INFERENCE_CREATE", "创建推理任务");
        return detail(taskId);
    }

    /** 重试失败推理任务，创建新的尝试并保留历史。 */
    public Map<String, Object> retry(RetryCommand command) {
        Map<String, Object> existing = repository.findTaskStatus(command.inferenceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_INFERENCE_NOT_FOUND", "推理任务不存在"));
        if (!"FAILED".equals(existing.get("status"))) {
            throw new ResourceConflictException("AI_INFERENCE_NOT_RETRYABLE", "只有失败任务可以重试");
        }

        UUID assetId = (UUID) existing.get("assetId");
        String mode = String.valueOf(existing.get("mode"));
        UUID originalModelRegistryId = (UUID) existing.get("modelRegistryId");
        Map<String, Object> model = command.modelId() != null && !command.modelId().isBlank()
                ? resolveModel(mode, command.modelId())
                : repository.findModelById(originalModelRegistryId)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "AI_MODEL_NOT_FOUND", "原模型不存在"));
        UUID modelRegistryId = (UUID) model.get("id");
        Map<String, Object> trace = repository.resolveAssetTraceability(assetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_ASSET_NOT_FOUND", "图片未绑定可追溯业务对象"));
        Map<String, Object> asset = loadAsset(assetId);
        byte[] imageBytes = readAssetBytes(assetId);

        int attemptNo = repository.findMaxAttemptNo(assetId, mode, modelRegistryId) + 1;
        UUID taskId = UUID.randomUUID();
        String requestCode = generateRequestCode();
        repository.insertTask(taskId, requestCode, "retry-" + requestCode, assetId,
                (UUID) trace.get("inspectionTaskId"), (UUID) trace.get("inspectionRecordId"),
                (UUID) trace.get("buildingId"), (UUID) trace.get("communityId"),
                modelRegistryId, mode, attemptNo, command.requestedBy());

        if (orchestrationService == null) {
            executeLegacyInference(taskId, assetId, mode, model, imageBytes, requestCode);
        } else {
            Map<String, Object> originalAudit = orchestrationRepository
                    .findAudit(command.inferenceId()).orElse(Map.of());
            AiCapabilityType capabilityType = normalizeCapability(
                    stringValue(originalAudit.get("capabilityType")));
            String providerCode = effectiveProvider(
                    stringValue(originalAudit.get("providerCode")), capabilityType);
            recordRouting(taskId, providerCode, capabilityType, model);
            executeOrchestratedInference(
                    taskId, assetId, mode, model, imageBytes,
                    String.valueOf(asset.get("contentType")), requestCode,
                    providerCode, capabilityType, null, null);
        }
        audit(taskId, assetId, requestCode, "AI_INFERENCE_RETRY", "重试推理任务");
        return detail(taskId);
    }

    public Map<String, Object> getDetail(UUID inferenceId) {
        return detail(inferenceId);
    }

    public Map<String, Object> list(Map<String, Object> filters, int page, int size) {
        String status = stringValue(filters.get("status"));
        if (status != null && !STATUSES.contains(status)) {
            throw new InvalidRequestException("AI_STATUS_INVALID", "推理任务状态无效");
        }
        List<Map<String, Object>> tasks = repository.listTasks(filters, page, size);
        if (orchestrationRepository != null) {
            for (Map<String, Object> task : tasks) {
                Object inferenceId = task.get("inferenceId");
                if (inferenceId instanceof UUID id) {
                    orchestrationRepository.findAudit(id).ifPresent(task::putAll);
                }
            }
        }
        long total = repository.countTasks(filters);
        return AiInferenceConverter.toListResponse(tasks, total, page, size);
    }

    public Map<String, Object> review(ReviewCommand command) {
        Map<String, Object> existing = repository.findTaskStatus(command.inferenceId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_INFERENCE_NOT_FOUND", "推理任务不存在"));
        String status = String.valueOf(existing.get("status"));
        if (!"SUCCEEDED".equals(status) && !"REJECTED".equals(status)) {
            throw new ResourceConflictException("AI_REVIEW_CONFLICT", "未完成的任务不能复核");
        }
        String reviewStatus = normalizeReviewStatus(command.reviewStatus());
        repository.saveReview(command.inferenceId(), reviewStatus, command.comment(), command.reviewedBy());
        audit(command.inferenceId(), null, null, "AI_INFERENCE_REVIEW", "提交人工复核 " + reviewStatus);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("inferenceId", command.inferenceId());
        response.put("reviewStatus", reviewStatus);
        response.put("reviewedAt", OffsetDateTime.now().toString());
        return response;
    }

    private void executeOrchestratedInference(
            UUID taskId,
            UUID assetId,
            String mode,
            Map<String, Object> model,
            byte[] imageBytes,
            String contentType,
            String requestCode,
            String providerCode,
            AiCapabilityType capabilityType,
            String prompt,
            String inferenceProfile) {
        if (repository.markRunning(taskId) == 0) {
            throw new ResourceConflictException("AI_INFERENCE_CONFLICT", "任务状态已变化，无法执行推理");
        }
        String requestId = RequestContext.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            requestId = requestCode;
        }
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("assetId", String.valueOf(assetId));
        inputs.put("requestCode", requestCode);
        if (inferenceProfile != null) {
            inputs.put("inferenceProfile", inferenceProfile);
        }
        AiOrchestrationRequest request = new AiOrchestrationRequest(
                requestId,
                capabilityType,
                providerCode,
                String.valueOf(model.get("modelCode")),
                mode,
                imageBytes,
                contentType,
                prompt == null || prompt.isBlank()
                        ? "分析图片中的建筑表观病害，输出候选风险信号、补拍建议和人工复核提示"
                        : prompt,
                inputs);
        try {
            AiStructuredResult result = orchestrationService.execute(request);
            orchestrationRepository.saveSuccess(taskId, result);
            if ("REJECTED".equals(result.status())) {
                repository.saveFailure(taskId, "AI_IMAGE_NOT_APPLICABLE",
                        "图片不适用于当前人工智能能力", true);
            }
        } catch (AiProviderException ex) {
            String errorCode = normalizeProviderErrorCode(ex.getErrorCode());
            boolean rejected = repository.isImageRejection(errorCode);
            repository.saveFailure(taskId, errorCode, ex.getMessage(), rejected);
        }
    }

    /** 第六阶段兼容路径，仅由旧构造器测试使用。 */
    private void executeLegacyInference(
            UUID taskId,
            UUID assetId,
            String mode,
            Map<String, Object> model,
            byte[] imageBytes,
            String requestCode) {
        if (repository.markRunning(taskId) == 0) {
            throw new ResourceConflictException("AI_INFERENCE_CONFLICT", "任务状态已变化，无法执行推理");
        }
        String modelCode = String.valueOf(model.get("modelCode"));
        String requestId = RequestContext.getRequestId();
        Map<String, Object> metadata = legacyInferenceProvider.buildMetadata(
                requestId, mode, String.valueOf(assetId),
                "inspection-image", null, null, modelCode);
        try {
            if ("REAL".equals(mode)) {
                legacyInferenceProvider.requireModelReady(modelCode, mode);
            }
            AiInferenceResponse response = legacyInferenceProvider.infer(imageBytes, metadata, requestId);
            if ("REJECTED".equals(response.status())) {
                repository.saveSuccess(taskId, response);
                repository.saveFailure(taskId, "AI_IMAGE_NOT_APPLICABLE",
                        "图片不适用于当前模型", true);
            } else {
                repository.saveSuccess(taskId, response);
            }
        } catch (AiProviderException ex) {
            boolean rejected = repository.isImageRejection(ex.getErrorCode());
            repository.saveFailure(taskId, ex.getErrorCode(), ex.getMessage(), rejected);
        }
    }

    private void recordRouting(
            UUID taskId,
            String providerCode,
            AiCapabilityType capabilityType,
            Map<String, Object> model) {
        String workflowCode = capabilityType == AiCapabilityType.WORKFLOW
                ? String.valueOf(model.get("modelCode")) : null;
        String workflowVersion = capabilityType == AiCapabilityType.WORKFLOW
                ? stringValue(model.get("modelVersion")) : null;
        orchestrationRepository.recordRouting(
                taskId, providerCode, capabilityType.name(), workflowCode, workflowVersion);
    }

    private Map<String, Object> loadAsset(UUID assetId) {
        Map<String, Object> asset;
        try {
            asset = assetService.get(assetId);
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException("AI_ASSET_NOT_FOUND", "图片不存在");
        }
        String contentType = String.valueOf(asset.get("contentType"));
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidRequestException("AI_ASSET_NOT_IMAGE", "所选文件不是图片");
        }
        return asset;
    }

    private byte[] readAssetBytes(UUID assetId) {
        try {
            return assetService.content(assetId);
        } catch (ResourceNotFoundException ex) {
            throw new ResourceNotFoundException("AI_ASSET_NOT_FOUND", "图片内容不可用");
        }
    }

    private Map<String, Object> resolveModel(String mode, String modelId) {
        String effectiveModelId = modelId;
        if (effectiveModelId == null || effectiveModelId.isBlank()) {
            if ("MOCK".equals(mode)) {
                effectiveModelId = properties.getDefaultMockModelId();
            } else {
                throw new InvalidRequestException("AI_MODEL_NOT_FOUND", "REAL 模式必须指定已批准模型");
            }
        }
        Map<String, Object> model = repository.findModelByCode(effectiveModelId)
                .orElseThrow(() -> new ResourceNotFoundException("AI_MODEL_NOT_FOUND", "模型不存在"));
        if (!mode.equals(model.get("mode"))) {
            throw new ResourceConflictException("AI_MODEL_NOT_APPROVED", "模型模式与请求不一致");
        }
        if ("REAL".equals(mode) && !"APPROVED".equals(model.get("status"))) {
            throw new ResourceConflictException("AI_MODEL_NOT_APPROVED", "真实模型未通过准入");
        }
        return model;
    }

    private Map<String, Object> existingActiveTask(
            CreateInferenceCommand command,
            String mode,
            UUID modelRegistryId) {
        Optional<Map<String, Object>> active = repository.findActiveTask(
                command.requestedBy(), command.assetId(), mode, modelRegistryId, command.idempotencyKey());
        if (active.isPresent()) {
            return detail((UUID) active.get().get("inferenceId"));
        }
        throw new ResourceConflictException("AI_INFERENCE_CONFLICT", "存在重复推理任务，请稍后查询");
    }

    private Map<String, Object> detail(UUID inferenceId) {
        Map<String, Object> task = repository.findTaskDetail(inferenceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "AI_INFERENCE_NOT_FOUND", "推理任务不存在"));
        if (orchestrationRepository != null) {
            orchestrationRepository.findAudit(inferenceId).ifPresent(task::putAll);
        }
        return AiInferenceConverter.toDetailResponse(task);
    }

    private void audit(
            UUID taskId,
            UUID assetId,
            String requestCode,
            String operationType,
            String summary) {
        Map<String, Object> afterData = new LinkedHashMap<>();
        afterData.put("inferenceId", taskId);
        afterData.put("assetId", assetId);
        afterData.put("requestCode", requestCode);
        auditService.recordSuccess(AuditOperation.success(
                operationType, "AiInferenceTask", taskId, null, afterData, List.of(), summary));
    }

    private String effectiveProvider(String requestedProvider, AiCapabilityType capabilityType) {
        if (requestedProvider != null && !requestedProvider.isBlank()) {
            return requestedProvider.trim().toUpperCase(Locale.ROOT);
        }
        if (orchestrationProperties == null) {
            return "FAST_API";
        }
        String configured = orchestrationProperties.defaultProvider(capabilityType);
        return configured == null ? "" : configured.trim().toUpperCase(Locale.ROOT);
    }

    private AiCapabilityType normalizeCapability(String capabilityType) {
        if (capabilityType == null || capabilityType.isBlank()) {
            return AiCapabilityType.VISION_INFERENCE;
        }
        try {
            return AiCapabilityType.valueOf(capabilityType.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(
                    AiErrorCodes.AI_UNSUPPORTED_CAPABILITY, "人工智能能力类型无效");
        }
    }

    private String normalizeInferenceProfile(String inferenceProfile) {
        if (inferenceProfile == null || inferenceProfile.isBlank()) {
            return null;
        }
        String normalized = inferenceProfile.trim().toUpperCase(Locale.ROOT);
        if (!INFERENCE_PROFILES.contains(normalized)) {
            throw new InvalidRequestException(
                    "AI_INFERENCE_PROFILE_INVALID", "视觉推理档位无效，仅支持 FAST 或 PRECISION");
        }
        return normalized;
    }

    private String normalizeProviderErrorCode(String errorCode) {
        if ("AI_SERVICE_TIMEOUT".equals(errorCode)) {
            return AiErrorCodes.AI_PROVIDER_TIMEOUT;
        }
        if ("AI_SERVICE_UNAVAILABLE".equals(errorCode)) {
            return AiErrorCodes.AI_PROVIDER_UNAVAILABLE;
        }
        if ("AI_SERVICE_INVALID_RESPONSE".equals(errorCode)) {
            return AiErrorCodes.AI_INVALID_RESPONSE;
        }
        return errorCode == null || errorCode.isBlank()
                ? AiErrorCodes.AI_PROVIDER_UNAVAILABLE : errorCode;
    }

    private String normalizeMode(String mode) {
        String value = mode == null ? properties.getDefaultMode() : mode.trim().toUpperCase(Locale.ROOT);
        if (!MODES.contains(value)) {
            throw new InvalidRequestException("AI_MODE_INVALID", "推理模式无效，仅支持 MOCK 或 REAL");
        }
        return value;
    }

    private String normalizeReviewStatus(String reviewStatus) {
        String value = reviewStatus == null ? "" : reviewStatus.trim().toUpperCase(Locale.ROOT);
        if (!REVIEW_STATUSES.contains(value)) {
            throw new InvalidRequestException("AI_REVIEW_STATUS_INVALID", "复核状态无效");
        }
        return value;
    }

    private String generateRequestCode() {
        return "AI-" + OffsetDateTime.now().toLocalDate().toString().replace("-", "")
                + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim().toUpperCase(Locale.ROOT);
    }
}
