package org.urbansafe.priority.ai.command;

import java.util.UUID;

/** 创建人工智能推理任务命令。 */
public record CreateInferenceCommand(
        UUID assetId,
        String mode,
        String modelId,
        String providerCode,
        String capabilityType,
        String prompt,
        String idempotencyKey,
        UUID requestedBy) {

    /** 保留第三阶段调用签名，未指定提供者时由第七阶段默认路由选择。 */
    public CreateInferenceCommand(
            UUID assetId,
            String mode,
            String modelId,
            String idempotencyKey,
            UUID requestedBy) {
        this(assetId, mode, modelId, null, null, null, idempotencyKey, requestedBy);
    }
}
