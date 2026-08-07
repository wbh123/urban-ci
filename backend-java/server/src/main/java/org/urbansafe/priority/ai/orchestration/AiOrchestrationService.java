package org.urbansafe.priority.ai.orchestration;

/** 统一人工智能编排入口。第一轮只执行单一明确路由，不自动降级。 */
public class AiOrchestrationService {

    private final AiProviderRouter router;
    private final AiStructuredResultValidator validator;
    private final AiImagePrecheckService imagePrecheckService;

    /** 兼容既有轻量单元测试；运行时使用包含图片预检服务的构造器。 */
    public AiOrchestrationService(
            AiProviderRouter router,
            AiStructuredResultValidator validator) {
        this(router, validator, null);
    }

    public AiOrchestrationService(
            AiProviderRouter router,
            AiStructuredResultValidator validator,
            AiImagePrecheckService imagePrecheckService) {
        this.router = router;
        this.validator = validator;
        this.imagePrecheckService = imagePrecheckService;
    }

    public AiStructuredResult execute(AiOrchestrationRequest request) {
        AiOrchestrationRequest effectiveRequest = imagePrecheckService == null
                ? request
                : imagePrecheckService.precheck(request);
        AiCapabilityProvider provider = router.route(effectiveRequest);
        AiStructuredResult result = provider.execute(effectiveRequest);
        validator.validate(effectiveRequest, result);
        return result;
    }
}
