package org.urbansafe.priority.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.service.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthenticatedUserContextRunnerTest {

    @Mock
    private AuthService authService;

    private AuthenticatedUserContextRunner runner;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        runner = new AuthenticatedUserContextRunner(authService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runAsShouldRestoreCurrentUserAndRolesFromDatabase() {
        UUID userId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(user(userId, "expert"));
        when(authService.getUserRoles(userId)).thenReturn(List.of("EXPERT", "PROFESSIONAL_REVIEWER"));

        String result = runner.runAs(userId, () -> {
            assertThat(CurrentUser.isAuthenticated()).isTrue();
            assertThat(CurrentUser.getUserId()).isEqualTo(userId);
            assertThat(CurrentUser.getUsername()).isEqualTo("expert");
            assertThat(CurrentUser.getRoles()).containsExactly("EXPERT", "PROFESSIONAL_REVIEWER");
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting("authority")
                    .containsExactly("ROLE_EXPERT", "ROLE_PROFESSIONAL_REVIEWER");
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(CurrentUser.isAuthenticated()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void runAsShouldClearTemporaryContextWhenActionFails() {
        UUID userId = UUID.randomUUID();
        when(authService.getCurrentUser(userId)).thenReturn(user(userId, "admin"));
        when(authService.getUserRoles(userId)).thenReturn(List.of("ADMIN"));

        assertThatThrownBy(() -> runner.runAs(userId, () -> {
            assertThat(CurrentUser.getRoles()).containsExactly("ADMIN");
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(CurrentUser.isAuthenticated()).isFalse();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void runAsShouldRejectTaskWithoutAuthenticatedUser() {
        assertThatThrownBy(() -> runner.runAs(null, () -> "never"))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("ASYNC_TASK_AUTHENTICATED_USER_REQUIRED");
    }

    private static CurrentUserResult user(UUID userId, String username) {
        return new CurrentUserResult(
                userId,
                username,
                username,
                null,
                null,
                null,
                "ACTIVE",
                List.of(),
                OffsetDateTime.now());
    }
}
