package org.urbansafe.priority.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.urbansafe.priority.auth.config.AuthProperties;
import org.urbansafe.priority.auth.result.LoginResult;
import org.urbansafe.priority.auth.service.AuthService;
import org.urbansafe.priority.common.request.RequestContext;
import org.urbansafe.priority.support.PostgreSqlIntegrationTestBase;

class AuthLoginControllerTest extends PostgreSqlIntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @BeforeEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    void loginWithValidCredentialsShouldReturn200() throws Exception {
        LoginResult loginResult = new LoginResult(
                UUID.randomUUID(),
                "admin",
                "管理员",
                java.util.List.of("ADMIN"),
                "jwt-token",
                "Bearer",
                7200);

        when(authService.login(eq("admin"), eq("password123"))).thenReturn(loginResult);

        String requestJson = """
                {"username": "admin", "password": "password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.accessToken").value("jwt-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    void loginWithInvalidCredentialsShouldReturn401() throws Exception {
        when(authService.login(eq("admin"), eq("wrong")))
                .thenThrow(new org.urbansafe.priority.common.exception.BusinessException(
                        org.springframework.http.HttpStatus.UNAUTHORIZED,
                        "INVALID_CREDENTIALS", "用户名或密码错误"));

        String requestJson = """
                {"username": "admin", "password": "wrong"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginWithMissingUsernameShouldReturn400() throws Exception {
        String requestJson = """
                {"password": "password123"}
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithEmptyBodyShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
