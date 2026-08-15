package org.urbansafe.priority.ai.governance;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class AiAutomationSettingsControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiAutomationSettingsService service;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminShouldReadAndUpdateAutomationSettingsWithDefaultModel() throws Exception {
        AiAutomationSettings current = new AiAutomationSettings(
                false, false, false, "AI-VISION-LOCAL-001", "FAST_API", "VISION_INFERENCE", null);
        AiAutomationSettings updated = new AiAutomationSettings(
                true, false, false, "AI-CRACK-HF-UNET-001", "FAST_API", "VISION_INFERENCE", null);
        when(service.get()).thenReturn(current);
        when(service.update(true, false, false, "AI-CRACK-HF-UNET-001", null)).thenReturn(updated);

        mockMvc.perform(get("/api/v1/ai-governance/automation-settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoInferenceOnUpload").value(false))
                .andExpect(jsonPath("$.data.modelId").value("AI-VISION-LOCAL-001"));

        mockMvc.perform(put("/api/v1/ai-governance/automation-settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoInferenceOnUpload\":true,\"intelligentWorkflowEnabled\":false,"
                                + "\"knowledgeQaEnabled\":false,\"modelId\":\"AI-CRACK-HF-UNET-001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.autoInferenceOnUpload").value(true))
                .andExpect(jsonPath("$.data.modelId").value("AI-CRACK-HF-UNET-001"));
    }

    @Test
    @WithMockUser(username = "operator", roles = "COMMUNITY_OPERATOR")
    void nonAdminShouldBeForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/ai-governance/automation-settings"))
                .andExpect(status().isForbidden());
    }
}
