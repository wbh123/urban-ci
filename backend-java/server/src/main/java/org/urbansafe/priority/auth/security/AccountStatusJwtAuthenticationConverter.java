package org.urbansafe.priority.auth.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.persistence.entity.UserAccountEntity;
import org.urbansafe.priority.persistence.mapper.UserAccountMapper;

/**
 * 将已完成密码学验证的 JWT 转换为应用认证对象，并实时检查账号状态。
 *
 * <p>短期访问令牌仍可能在签发后遇到账号停用。本转换器在每次请求认证时查询账号，
 * 使不存在、已逻辑删除或非 ACTIVE 的账号立即失效，而不需要在 Controller 中重复检查。
 */
@Component
public class AccountStatusJwtAuthenticationConverter
        implements Converter<Jwt, AbstractAuthenticationToken> {

    /** 账号状态的数据库访问入口。 */
    private final UserAccountMapper userAccountMapper;

    /**
     * 创建认证转换器。
     *
     * @param userAccountMapper 用户账号基础 Mapper
     */
    public AccountStatusJwtAuthenticationConverter(UserAccountMapper userAccountMapper) {
        this.userAccountMapper = userAccountMapper;
    }

    /**
     * 校验账号仍有效并构造带 ROLE_ 权限的认证对象。
     *
     * @param jwt 已通过签名、时间、issuer、audience 和 subject validator 的 JWT
     * @return Spring Security 认证对象
     * @throws OAuth2AuthenticationException 账号不存在或已停用时抛出统一 Bearer 认证异常
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserAccountEntity account = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getId, userId));
        if (account == null || !"ACTIVE".equals(account.getStatus())) {
            throw new InvalidBearerTokenException("AUTH_TOKEN_SUBJECT_INVALID");
        }

        List<String> roles = jwt.getClaimAsStringList("roles");
        List<String> safeRoles = roles == null ? List.of() : roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        List<SimpleGrantedAuthority> authorities = safeRoles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();

        String username = jwt.getClaimAsString("username");
        CurrentUser.UserInfo principal = new CurrentUser.UserInfo(
                account.getId(),
                username == null ? account.getUsername() : username,
                safeRoles);
        return UsernamePasswordAuthenticationToken.authenticated(principal, jwt, authorities);
    }
}
