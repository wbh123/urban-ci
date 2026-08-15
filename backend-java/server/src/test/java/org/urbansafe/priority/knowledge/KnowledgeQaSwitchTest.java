package org.urbansafe.priority.knowledge;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.ai.governance.AiAutomationSettingsService;
import org.urbansafe.priority.common.exception.InvalidRequestException;

class KnowledgeQaSwitchTest {

    @Test
    void shouldRejectQuestionWhenKnowledgeQaIsDisabled() {
        KnowledgeRepository repository = mock(KnowledgeRepository.class);
        AiAutomationSettingsService automation = mock(AiAutomationSettingsService.class);
        when(automation.knowledgeQaEnabled()).thenReturn(false);
        KnowledgeQaService service = new KnowledgeQaService(
                repository,
                new KnowledgeTextScorer(),
                Optional.empty(),
                Optional.of(automation));

        assertThatThrownBy(() -> service.ask(null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("知识问答已由系统管理员关闭");
        verifyNoInteractions(repository);
    }
}
