package org.urbansafe.priority.ai.automation;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.ai.execution.AiExecutionCommand;
import org.urbansafe.priority.ai.execution.AiExecutionTaskService;
import org.urbansafe.priority.ai.governance.AiAutomationSettings;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;

/** 图片上传自动识别编排器：只持久化任务，绝不在上传请求线程中等待 Dify。 */
@Service
public class AiUploadAutomationService {

    private static final Logger log = LoggerFactory.getLogger(AiUploadAutomationService.class);
    private static final String IMAGE_WORKFLOW_CODE = "DIFY-IMAGE-ANALYSIS-001";

    private final AiAutomationSettingsService settingsService;
    private final AiExecutionTaskService executionTaskService;

    public AiUploadAutomationService(
            AiAutomationSettingsService settingsService,
            AiExecutionTaskService executionTaskService) {
        this.settingsService = settingsService;
        this.executionTaskService = executionTaskService;
    }

    public AiUploadAutomationResult triggerIfEnabled(
            UUID assetId,
            String businessType,
            UUID requestedBy) {
        AiAutomationSettings settings;
        try {
            settings = settingsService.get();
        } catch (RuntimeException ex) {
            log.warn("Automatic inference settings unavailable for asset {}: {}", assetId, ex.getMessage());
            return new AiUploadAutomationResult(
                    false, false, false, AiAutomationSettingsService.AUTO_MODEL_ID,
                    null, null, null,
                    "图片已上传，但自动识别设置读取失败：" + safeMessage(ex));
        }
        if (!settings.autoInferenceOnUpload()) {
            return AiUploadAutomationResult.disabled(settings.modelId());
        }
        if (!"INSPECTION_TASK".equalsIgnoreCase(businessType)) {
            return AiUploadAutomationResult.skipped(
                    settings.modelId(), "当前图片未绑定巡检任务，不触发自动识别");
        }
        try {
            UUID taskId = executionTaskService.enqueue(new AiExecutionCommand(
                    assetId,
                    IMAGE_WORKFLOW_CODE,
                    "REAL",
                    settings.modelId(),
                    settings.providerCode(),
                    settings.capabilityType(),
                    null,
                    "auto-upload-" + assetId,
                    requestedBy,
                    Map.of("assetId", assetId.toString(), "trigger", "UPLOAD")));
            return AiUploadAutomationResult.queued(settings.modelId(), taskId);
        } catch (RuntimeException ex) {
            log.warn("Automatic inference could not be queued for asset {}: {}", assetId, ex.getMessage());
            return AiUploadAutomationResult.skipped(
                    settings.modelId(), "图片已上传，但自动识别入队失败：" + safeMessage(ex));
        }
    }

    private static String safeMessage(RuntimeException ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? "未知错误" : message;
    }
}
