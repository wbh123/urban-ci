package org.urbansafe.priority.common.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web CORS 类型安全配置，替代逗号字符串和散落的 {@code @Value} 解析。
 */
@ConfigurationProperties(prefix = "urban-safe.web.cors")
public class WebCorsProperties {

    /** 允许的精确来源列表，默认仅允许本地 Vue 开发服务器。 */
    private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173"));

    /** 允许的 HTTP 方法列表。 */
    private List<String> allowedMethods = new ArrayList<>(
            List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));

    /** 允许的请求头列表。 */
    private List<String> allowedHeaders = new ArrayList<>(
            List.of("Authorization", "Content-Type", "X-UrbanSafe-Request-Id"));

    /** 是否允许浏览器发送凭据。 */
    private boolean allowCredentials = true;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = normalize(allowedOrigins);
    }

    public List<String> getAllowedMethods() {
        return allowedMethods;
    }

    public void setAllowedMethods(List<String> allowedMethods) {
        this.allowedMethods = normalize(allowedMethods);
    }

    public List<String> getAllowedHeaders() {
        return allowedHeaders;
    }

    public void setAllowedHeaders(List<String> allowedHeaders) {
        this.allowedHeaders = normalize(allowedHeaders);
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }

    /**
     * 在创建安全过滤链前校验禁止的通配符与凭据组合。
     */
    public void validate() {
        if (allowedOrigins.isEmpty()) {
            throw new IllegalStateException("urban-safe.web.cors.allowed-origins 不能为空");
        }
        if (allowCredentials && allowedOrigins.contains("*")) {
            throw new IllegalStateException("CORS allow-credentials=true 时禁止 allowed-origins=*");
        }
    }

    /**
     * 清理列表中的首尾空格和空项，并复制为可变列表供 Spring Binder 使用。
     *
     * @param values 原始配置列表
     * @return 规范化后的列表
     */
    private List<String> normalize(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }
}
