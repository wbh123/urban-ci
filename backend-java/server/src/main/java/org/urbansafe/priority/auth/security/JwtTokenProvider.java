package org.urbansafe.priority.auth.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Component;
import org.urbansafe.priority.auth.config.AuthProperties;

/**
 * 使用 Spring Security 官方 JwtEncoder 签发访问令牌。
 *
 * <p>本类只负责组装受控声明，不再自行实现 MAC 签名或验签逻辑。
 */
@Component
public class JwtTokenProvider {

    /** 认证配置，提供签发方、受众和有效期。 */
    private final AuthProperties authProperties;
    /** Spring Security 官方 JWT 编码器。 */
    private final JwtEncoder jwtEncoder;
    /** 可注入时钟，保证生产与测试时间来源一致。 */
    private final Clock clock;

    /**
     * 创建令牌签发器。
     *
     * @param authProperties 认证配置
     * @param jwtEncoder 官方 JWT 编码器
     * @param clock 统一时钟
     */
    public JwtTokenProvider(AuthProperties authProperties, JwtEncoder jwtEncoder, Clock clock) {
        this.authProperties = authProperties;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
    }

    /**
     * 为已完成用户名密码认证的用户签发 HS256 访问令牌。
     *
     * @param userId 用户 UUID
     * @param username 用户名，仅用于显示和审计上下文
     * @param roles 角色编码列表，不包含 ROLE_ 前缀
     * @return 序列化后的 JWT
     */
    public String generateToken(UUID userId, String username, List<String> roles) {
        AuthProperties.Jwt jwtProps = authProperties.getJwt();
        Instant now = clock.instant();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(userId.toString())
                .issuer(jwtProps.getIssuer())
                .audience(List.of(jwtProps.getAudience()))
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(jwtProps.getAccessTokenTtlSeconds()))
                .claim("username", username)
                .claim("roles", List.copyOf(roles))
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * 暴露只读认证配置供登录响应获取令牌有效期。
     *
     * @return 认证配置
     */
    public AuthProperties getAuthProperties() {
        return authProperties;
    }
}
