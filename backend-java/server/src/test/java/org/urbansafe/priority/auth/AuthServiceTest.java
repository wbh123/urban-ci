package org.urbansafe.priority.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.urbansafe.priority.auth.config.AuthProperties;
import org.urbansafe.priority.auth.result.LoginResult;
import org.urbansafe.priority.persistence.entity.UserAccountEntity;
import org.urbansafe.priority.persistence.mapper.RoleMapper;
import org.urbansafe.priority.persistence.mapper.UserAccountMapper;
import org.urbansafe.priority.persistence.mapper.UserRoleMapper;
import org.urbansafe.priority.auth.security.JwtTokenProvider;
import org.urbansafe.priority.auth.service.impl.AuthServiceImpl;
import org.urbansafe.priority.common.exception.BusinessException;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtTokenProvider jwtTokenProvider;

    /** 统一审计服务使用 Mock，单元测试只验证认证业务而不访问数据库。 */
    @Mock
    private org.urbansafe.priority.audit.service.AuditService auditService;

    @InjectMocks
    private AuthServiceImpl authService;

    private AuthProperties authProperties;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        AuthProperties.Jwt jwt = new AuthProperties.Jwt();
        jwt.setIssuer("test");
        jwt.setAudience("test");
        jwt.setSecret("test-secret-key-for-unit-tests-which-is-long-enough!!");
        jwt.setAccessTokenTtlSeconds(7200);
        authProperties.setJwt(jwt);
    }

    @Test
    void loginWithCorrectCredentialsShouldSucceed() {
        UUID userId = UUID.randomUUID();
        String username = "admin";
        String rawPassword = "password123";
        String encodedPassword = "$2a$10$encodedHash";

        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash(encodedPassword);
        user.setRealName("管理员");
        user.setStatus("ACTIVE");

        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);
        when(userRoleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        when(jwtTokenProvider.generateToken(eq(userId), eq(username), anyList()))
                .thenReturn("jwt-access-token");
        when(jwtTokenProvider.getAuthProperties()).thenReturn(authProperties);

        LoginResult response = authService.login(username, rawPassword);

        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("jwt-access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(7200);
        assertThat(response.username()).isEqualTo("admin");
        assertThat(response.realName()).isEqualTo("管理员");
    }

    @Test
    void loginWithWrongPasswordShouldThrow401() {
        UUID userId = UUID.randomUUID();
        String username = "admin";
        String rawPassword = "wrongpassword";
        String encodedPassword = "$2a$10$encodedHash";

        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash(encodedPassword);
        user.setStatus("ACTIVE");

        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

        assertThatThrownBy(() -> authService.login(username, rawPassword))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(bex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void loginWithNonexistentUserShouldThrow401() {
        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> authService.login("nobody", "password"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(bex.getErrorCode()).isEqualTo("INVALID_CREDENTIALS");
                });
    }

    @Test
    void loginWithDisabledAccountShouldThrow403() {
        UUID userId = UUID.randomUUID();
        String username = "disabled_user";
        String rawPassword = "password123";
        String encodedPassword = "$2a$10$encodedHash";

        UserAccountEntity user = new UserAccountEntity();
        user.setId(userId);
        user.setUsername(username);
        user.setPasswordHash(encodedPassword);
        user.setStatus("DISABLED");

        when(userAccountMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        assertThatThrownBy(() -> authService.login(username, rawPassword))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException bex = (BusinessException) ex;
                    assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(bex.getErrorCode()).isEqualTo("ACCOUNT_DISABLED");
                });
    }

    /** 验证认证 Service 的登录契约返回内部不可变结果，而非 OpenAPI 生成的响应 DTO。 */
    @Test
    void loginContractShouldReturnInternalLoginResult() throws NoSuchMethodException {
        Class<?> loginResultType = AuthServiceImpl.class
                .getMethod("login", String.class, String.class)
                .getReturnType();

        assertThat(loginResultType.getName())
                .isEqualTo("org.urbansafe.priority.auth.result.LoginResult");
    }
}
