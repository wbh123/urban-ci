package org.urbansafe.priority.ai.orchestration;

import java.util.Set;

/** 通用人工智能能力提供者。 */
public interface AiCapabilityProvider {

    String providerCode();

    boolean enabled();

    boolean configured();

    Set<AiCapabilityType> capabilities();

    AiStructuredResult execute(AiOrchestrationRequest request);

    default boolean supports(AiCapabilityType capabilityType) {
        return capabilityType != null && capabilities().contains(capabilityType);
    }

    /**
     * 判断提供者是否支持请求中的模型编号和任务模式。
     *
     * <p>默认要求模型编号和任务模式非空；具体提供者可进一步限制模式。
     */
    default boolean supportsModel(String modelCode, String taskMode) {
        return modelCode != null && !modelCode.isBlank()
                && taskMode != null && !taskMode.isBlank();
    }
}
