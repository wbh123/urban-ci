package org.urbansafe.priority.auth.security;

import java.util.Objects;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 校验访问令牌 audience 声明是否包含城安智序前端受众。
 */
public final class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    /** audience 不匹配时使用的稳定错误，避免向客户端暴露底层解析细节。 */
    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            "AUTH_TOKEN_AUDIENCE_INVALID",
            "访问令牌受众无效",
            null);

    /** 配置中要求的受众值。 */
    private final String requiredAudience;

    /**
     * 创建受众校验器。
     *
     * @param requiredAudience 必须出现在 JWT aud 数组中的受众
     */
    public JwtAudienceValidator(String requiredAudience) {
        this.requiredAudience = Objects.requireNonNull(requiredAudience, "requiredAudience");
    }

    /**
     * 校验 JWT 的 aud 声明。
     *
     * @param token 已通过签名解析的 JWT
     * @return 成功结果，或包含稳定错误码的失败结果
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        if (token.getAudience() != null && token.getAudience().contains(requiredAudience)) {
            return OAuth2TokenValidatorResult.success();
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
