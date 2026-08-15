package org.urbansafe.priority.feedback.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

class FeedbackAiAssistControllerTest {

    @Test
    void exposesReadOnlyAssistRouteToExistingFeedbackManagerRoles() throws Exception {
        Method method = FeedbackAiAssistController.class.getMethod("assist", UUID.class);

        PostMapping postMapping = method.getAnnotation(PostMapping.class);
        assertNotNull(postMapping);
        assertEquals("/feedback/reports/{reportId}/ai-assist", postMapping.value()[0]);

        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertTrue(preAuthorize.value().contains("COMMUNITY_MANAGER"));
        assertTrue(preAuthorize.value().contains("GOVERNMENT_MANAGER"));
        assertTrue(preAuthorize.value().contains("ADMIN"));
    }
}
