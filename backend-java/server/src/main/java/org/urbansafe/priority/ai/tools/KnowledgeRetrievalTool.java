package org.urbansafe.priority.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.ai.execution.AiAgentStepStatus;
import org.urbansafe.priority.ai.execution.AiAgentTrace;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.knowledge.KnowledgeAnswer;
import org.urbansafe.priority.knowledge.KnowledgeQaService;
import org.urbansafe.priority.knowledge.KnowledgeQuestionCommand;

/** Spring AI Knowledge Tool：从已授权内部知识库检索证据并生成受控回答。 */
@Component
public class KnowledgeRetrievalTool {

    private final KnowledgeQaService knowledgeService;

    public KnowledgeRetrievalTool(KnowledgeQaService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Tool(description = """
            从项目内部已授权知识库检索证据并生成受控回答。
            在问题涉及建筑安全专业业务知识、需要引用已审核证据时使用。
            证据不足时返回拒答，不得凭模型自身知识编造专业结论。
            """)
    public KnowledgeToolResult retrieve(String question) {
        AiAgentTrace.StepToken step = AiAgentTrace.beginStep("KnowledgeRetrievalTool", "SPRING_BOOT");
        try {
            KnowledgeAnswer answer = knowledgeService.ask(new KnowledgeQuestionCommand(
                    question, null, null, 5, CurrentUser.getUserId(), CurrentUser.getRoles()));
            AiAgentTrace.finishStep(step, AiAgentStepStatus.SUCCEEDED, null, null);
            return new KnowledgeToolResult(
                    answer.status(),
                    answer.evidenceSufficient(),
                    answer.answer(),
                    answer.providerCode(),
                    answer.modelCode(),
                    answer.citations().size());
        } catch (RuntimeException ex) {
            AiAgentTrace.finishStep(step, AiAgentStepStatus.FAILED, ex.getClass().getSimpleName(), ex.getMessage());
            throw ex;
        }
    }

    public record KnowledgeToolResult(
            String status,
            boolean evidenceSufficient,
            String answer,
            String providerCode,
            String modelCode,
            int citationCount) {
    }
}
