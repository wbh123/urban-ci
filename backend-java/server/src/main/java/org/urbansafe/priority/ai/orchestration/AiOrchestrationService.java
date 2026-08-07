package org.urbansafe.priority.ai.orchestration;

/** 统一人工智能编排入口。第一轮只执行单一明确路由，不自动降级。 */
public class AiOrchestrationService {

    private final AiProviderRouter router;
    private final AiStructuredResultValidator validator;

    public AiOrchestrationService(
            AiProviderRouter router,
            AiStructuredResultValidator validator) {
        this.router = router;
        this.validator = validator;
    }

    public AiStructuredResult execute(AiOrchestrationRequest request) {
        AiCapabilityProvider provider = router.route(request);
        AiStructuredResult result = provider.execute(request);
        validator.validate(request, result);
        return result;
    }
}
