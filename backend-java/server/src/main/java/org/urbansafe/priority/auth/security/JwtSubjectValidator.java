package org.urbansafe.priority.auth.security;

import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * 校验 JWT subject 是否为合法用户 UUID。
 */
public final class JwtSubjectValidator implements OAuth2TokenValidator<Jwt> {

    /** subject 缺失或格式非法时返回的稳定错误。 */
    private static final OAuth2Error INVALID_SUBJECT = new OAuth2Error(
            "AUTH_TOKEN_SUBJECT_INVALID",
            "访问令牌用户标识无效",
            null);

    /**
     * 校验 subject 存在、无首尾空白且能够解析为 UUID。
     *
     * @param token 已通过签名解析的 JWT
     * @return 校验结果
     */
    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        String subject = token.getSubject();
        if (subject == null || subject.isBlank() || !subject.equals(subject.trim())) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }
        try {
            UUID.fromString(subject);
            return OAuth2TokenValidatorResult.success();
        } catch (IllegalArgumentException exception) {
            return OAuth2TokenValidatorResult.failure(INVALID_SUBJECT);
        }
    }
}
