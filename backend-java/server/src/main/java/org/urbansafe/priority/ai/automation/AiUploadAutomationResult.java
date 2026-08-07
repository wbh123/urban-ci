package org.urbansafe.priority.ai.automation;

import java.util.UUID;

/** 图片上传后的自动识别入队结果，不影响图片本身是否上传成功。 */
public record AiUploadAutomationResult(
        boolean enabled,
        boolean triggered,
        boolean queued,
        String modelId,
        UUID executionTaskId,
        UUID inferenceId,
        String status,
        String message) {

    public static AiUploadAutomationResult disabled(String modelId) {
        return new AiUploadAutomationResult(
                false, false, false, modelId, null, null, null, "上传后自动识别已关闭");
    }

    public static AiUploadAutomationResult skipped(String modelId, String message) {
        return new AiUploadAutomationResult(
                true, false, false, modelId, null, null, null, message);
    }

    public static AiUploadAutomationResult queued(String modelId, UUID executionTaskId) {
        return new AiUploadAutomationResult(
                true, true, true, modelId, executionTaskId, null, "PENDING",
                "图片上传完成，自动识别任务已进入后台队列");
    }
}
