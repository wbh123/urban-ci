package org.urbansafe.priority.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.urbansafe.priority.persistence.entity.RoleEntity;
import org.urbansafe.priority.persistence.entity.UserAccountEntity;
import org.urbansafe.priority.persistence.entity.UserRoleEntity;
import org.urbansafe.priority.persistence.mapper.RoleMapper;
import org.urbansafe.priority.persistence.mapper.UserAccountMapper;
import org.urbansafe.priority.persistence.mapper.UserRoleMapper;
import org.urbansafe.priority.auth.security.JwtTokenProvider;
import org.urbansafe.priority.auth.service.AuthService;
import org.urbansafe.priority.auth.result.CurrentUserResult;
import org.urbansafe.priority.auth.result.CurrentUserRoleResult;
import org.urbansafe.priority.auth.result.LoginResult;
import org.urbansafe.priority.audit.model.AuditOperation;
import org.urbansafe.priority.audit.service.AuditService;
import org.urbansafe.priority.auth.security.CurrentUser;
import org.urbansafe.priority.common.exception.BusinessException;
import org.urbansafe.priority.common.exception.ResourceNotFoundException;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger LOGGER = LogManager.getLogger(AuthServiceImpl.class);

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuditService auditService;

    public AuthServiceImpl(
            UserAccountMapper userAccountMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            AuditService auditService) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.auditService = auditService;
    }

    @Override
    public LoginResult login(String username, String password) {
        UserAccountEntity user = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUsername, username));

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            auditService.recordFailure(AuditOperation.success(
                    "AUTH_LOGIN_FAILED", "UserAccount", null, null, null,
                    List.of(), "登录失败"), "INVALID_CREDENTIALS", "用户名或密码错误");
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "用户名或密码错误");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            auditService.recordFailure(AuditOperation.success(
                    "AUTH_LOGIN_FAILED", "UserAccount", user.getId(), null, null,
                    List.of(), "登录失败"), "ACCOUNT_DISABLED", "账号已停用或锁定");
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用或锁定");
        }

        List<String> roles = getUserRoles(user.getId());
        String accessToken = jwtTokenProvider.generateToken(user.getId(), user.getUsername(), roles);

        auditService.recordSuccess(AuditOperation.success(
                "AUTH_LOGIN_SUCCESS", "UserAccount", user.getId(), null, null,
                List.of(), "登录成功"));
        return new LoginResult(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                roles,
                accessToken,
                "Bearer",
                (int) jwtTokenProvider.getAuthProperties().getJwt().getAccessTokenTtlSeconds());
    }

    /**
     * 记录无状态 JWT 退出事件；不记录完整令牌，也不尝试维护服务端会话。
     */
    @Override
    public void logout() {
        auditService.recordSuccess(AuditOperation.success(
                "AUTH_LOGOUT", "UserAccount", CurrentUser.getUserId(), null, null,
                List.of(), "退出登录"));
    }

    @Override
    public CurrentUserResult getCurrentUser(UUID userId) {
        UserAccountEntity user = userAccountMapper.selectById(userId);
        if (user == null) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", "用户不存在");
        }

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号已停用或锁定");
        }

        List<CurrentUserRoleResult> roles = getUserRoleDetails(userId);

        return new CurrentUserResult(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                user.getEmail(),
                user.getOrganizationName(),
                user.getStatus(),
                roles,
                user.getCreatedAt());
    }

    @Override
    public List<String> getUserRoles(UUID userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId));

        return userRoles.stream()
                .map(ur -> {
                    RoleEntity role = roleMapper.selectById(ur.getRoleId());
                    return role != null ? role.getRoleCode() : null;
                })
                .filter(code -> code != null)
                .toList();
    }

    /**
     * 查询用户角色关联，并将持久化角色转换为认证内部角色结果。
     *
     * @param userId 待查询的用户唯一标识
     * @return 过滤失效角色后的内部角色详情列表
     */
    private List<CurrentUserRoleResult> getUserRoleDetails(UUID userId) {
        List<UserRoleEntity> userRoles = userRoleMapper.selectList(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, userId));

        return userRoles.stream()
                .map(ur -> {
                    RoleEntity role = roleMapper.selectById(ur.getRoleId());
                    if (role == null) {
                        return null;
                    }
                    return new CurrentUserRoleResult(
                            role.getId(), role.getRoleCode(), role.getRoleName());
                })
                .filter(role -> role != null)
                .toList();
    }
}
