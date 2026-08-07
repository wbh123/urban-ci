package org.urbansafe.priority.knowledge;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class KnowledgeQaControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private KnowledgeQaService service;

    @Test
    @WithMockUser(username = "expert", roles = "EXPERT")
    void expertShouldAskAndReceiveCitationAnswer() throws Exception {
        UUID questionId = UUID.randomUUID();
        KnowledgeCitation citation = new KnowledgeCitation(
                UUID.randomUUID(), UUID.randomUUID(), "DOC-001", "巡检规范", "1.0",
                UUID.randomUUID(), "补拍", 2, "模糊时补拍近景照。", 1, 0.88d);
        when(service.ask(any())).thenReturn(new KnowledgeAnswer(
                questionId, "ANSWERED", "应补拍近景照。", true, List.of(citation),
                "SPRING_BOOT", "LOCAL-RAG-EXTRACTIVE-001", OffsetDateTime.now(),
                KnowledgeQaService.DISCLAIMER));

        mockMvc.perform(post("/api/v1/knowledge/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"图片模糊如何补拍？\",\"topK\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ANSWERED"))
                .andExpect(jsonPath("$.data.citations[0].documentCode").value("DOC-001"));
    }

    @Test
    @WithMockUser(username = "expert", roles = "EXPERT")
    void expertShouldNotCreateKnowledgeDocument() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentCode\":\"PHOTO_GUIDE\",\"title\":\"指南\",\"documentType\":\"GUIDE\",\"documentVersion\":\"1.0\",\"securityLevel\":\"INTERNAL\",\"roleScope\":[\"EXPERT\"],\"status\":\"ACTIVE\",\"contentChecksum\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"chunks\":[{\"chunkIndex\":0,\"content\":\"测试内容\"}]}"))
                .andExpect(status().isForbidden());
    }
}
