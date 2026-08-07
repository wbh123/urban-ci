package org.urbansafe.priority.ai.client;

import java.util.List;

/** FastAPI 进程中实际装载模型的运行时身份与就绪信息。 */
public record AiRuntimeModelInfo(
        String modelId,
        String modelName,
        String version,
        String mode,
        String status,
        List<String> supportedDefects,
        String license,
        String weightSha256,
        boolean ready,
        String executionProvider,
        Integer deviceId,
        String task,
        String adapter) {
}
