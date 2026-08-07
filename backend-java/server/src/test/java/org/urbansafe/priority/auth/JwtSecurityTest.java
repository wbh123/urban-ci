package org.urbansafe.priority.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.urbansafe.priority.auth.config.AuthProperties;
import org.urbansafe.priority.auth.security.JwtAudienceValidator;
import org.urbansafe.priority.auth.security.JwtSubjectValidator;
import org.urbansafe.priority.auth.security.JwtTokenProvider;

/**
 * JWT 安全边界单元测试。
 *
 * <p>测试不启动 Spring 或数据库，只验证签发、固定算法、受众和 subject 规则，
 * 因此即使 Docker 不可用也可以作为认证实现的快速安全网。
 */
class JwtSecurityTest {

    /** 测试使用的固定 UTC 时间，避免断言依赖机器当前时间。 */
    private static final Instant FIXED_NOW = Instant.parse("2026-07-14T00:00:00Z");

    /** 长度超过 32 字节的 HMAC 测试密钥。 */
    private static final String SECRET =
            "test-secret-key-for-urban-safe-jwt-security-2026";

    private AuthProperties authProperties;
    private JwtEncoder jwtEncoder;
    private NimbusJwtDecoder jwtDecoder;
    private Clock fixedClock;

    /**
     * 为每个测试创建全新的官方 Nimbus 编解码器，防止测试间共享状态。
     */
    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.getJwt().setIssuer("urban-safe-test");
        authProperties.getJwt().setAudience("urban-safe-web-test");
        authProperties.getJwt().setSecret(SECRET);
        authProperties.getJwt().setAccessTokenTtlSeconds(600);

        SecretKey secretKey = new SecretKeySpec(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");
        fixedClock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
        jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setClock(fixedClock);
        jwtDecoder.setJwtValidator(timestampValidator);
    }

    /**
     * 验证令牌签发必须使用注入 Clock，并由官方 decoder 校验 HS256 签名。
     */
    @Test
    void generatedTokenShouldUseFixedClockAndHs256() {
        UUID userId = UUID.randomUUID();
        JwtTokenProvider provider = new JwtTokenProvider(authProperties, jwtEncoder, fixedClock);

        String token = provider.generateToken(userId, "admin", List.of("ADMIN"));
        Jwt decoded = jwtDecoder.decode(token);

        assertThat(decoded.getHeaders().get("alg")).isEqualTo("HS256");
        assertThat(decoded.getSubject()).isEqualTo(userId.toString());
        assertThat(decoded.getIssuedAt()).isEqualTo(FIXED_NOW);
        assertThat(decoded.getExpiresAt()).isEqualTo(FIXED_NOW.plusSeconds(600));
        assertThat(decoded.getAudience()).containsExactly("urban-safe-web-test");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("ADMIN");
    }

    /**
     * 验证 audience 不匹配时 validator 返回稳定的认证错误。
     */
    @Test
    void audienceValidatorShouldRejectUnexpectedAudience() {
        Jwt jwt = jwt("3ceaf1f3-9973-451f-a391-2d23f9a95f47",
                List.of("another-client"));

        OAuth2TokenValidatorResult result =
                new JwtAudienceValidator("urban-safe-web-test").validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getErrorCode())
                .contains("AUTH_TOKEN_AUDIENCE_INVALID");
    }

    /**
     * 验证 subject 必须是合法 UUID，防止任意字符串进入账号查询链路。
     */
    @Test
    void subjectValidatorShouldRejectNonUuidSubject() {
        Jwt jwt = jwt("not-a-uuid", List.of("urban-safe-web-test"));

        OAuth2TokenValidatorResult result = new JwtSubjectValidator().validate(jwt);

        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).extracting(error -> error.getErrorCode())
                .contains("AUTH_TOKEN_SUBJECT_INVALID");
    }

    /**
     * 构造只用于 validator 的 Jwt，避免把签名验证与声明验证混在一个测试中。
     *
     * @param subject subject 声明
     * @param audiences audience 声明列表
     * @return 包含固定时间和指定声明的 Jwt
     */
    private Jwt jwt(String subject, List<String> audiences) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("urban-safe-test")
                .subject(subject)
                .audience(audiences)
                .issuedAt(FIXED_NOW)
                .expiresAt(FIXED_NOW.plusSeconds(600))
                .claims(values -> values.putAll(Map.of("roles", List.of("ADMIN"))))
                .build();
        return new Jwt(
                "validator-only-token",
                FIXED_NOW,
                FIXED_NOW.plusSeconds(600),
                JwsHeader.with(MacAlgorithm.HS256).build().getHeaders(),
                claims.getClaims());
    }
}
