package org.urbansafe.priority.ai.provider;

import java.util.Map;
import org.urbansafe.priority.ai.client.AiInferenceResponse;
import org.urbansafe.priority.ai.client.AiRuntimeModelInfo;

/**
 * 统一人工智能推理提供者接口。
 *
 * <p>业务服务只负责提交图片、模型编号和元数据，不感知模型运行环境、图形处理器、
 * 容器、Dify 或具体模型供应商。调用失败时实现应抛出 {@link AiProviderException}。
 */
public interface AiInferenceProvider {

    /** 提供者稳定编号，用于日志和后续路由。 */
    String providerCode();

    /** 校验指定模型是否可由当前提供者调用。 */
    AiRuntimeModelInfo requireModelReady(String expectedModelId, String expectedMode);

    /** 执行一次图片推理。 */
    AiInferenceResponse infer(byte[] imageBytes, Map<String, Object> metadata, String requestId);

    /** 构造提供者所需的推理元数据。 */
    Map<String, Object> buildMetadata(
            String requestId,
            String mode,
            String assetId,
            String filename,
            String contentType,
            String sha256,
            String requestedModelId);
}
