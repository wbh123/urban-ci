package org.urbansafe.priority.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * 防止 application.yaml 丢失关键配置结构、重新写入重复运行值或使用内联花括号分级。
 *
 * <p>根目录 .env 是运行参数的唯一持久化来源；application.yaml 只负责导入 .env
 * 并将环境变量映射到 Spring Boot 配置项。
 */
class ApplicationYamlPersistenceConfigurationTest {

    private static final ClassPathResource APPLICATION_YAML =
            new ClassPathResource("application.yaml");

    @Test
    void applicationYamlUsesEnvironmentBackedIndentedConfiguration() throws IOException {
        String yaml = readYaml();

        assertThat(yaml)
                .contains("optional:file:./.env[.properties]")
                .contains("optional:file:../.env[.properties]")
                .contains("${URBAN_SAFE_SERVER_PORT}")
                .contains("${URBAN_SAFE_DB_PASSWORD}")
                .contains("${URBAN_SAFE_AUTH_JWT_SECRET}")
                .doesNotContainPattern("(?m)^\\s*[\\w-]+:\\s*\\{")
                .doesNotContainPattern("(?m)^\\s*[\\w-]+:\\s*\\[");
    }

    @Test
    void applicationYamlDelegatesRuntimeValuesToEnvironmentVariables() throws IOException {
        List<PropertySource<?>> sources = load();

        assertThat(property(sources, "server.port"))
                .isEqualTo("${URBAN_SAFE_SERVER_PORT}");
        assertThat(property(sources, "spring.profiles.active")).isEqualTo("local");
        assertThat(property(sources, "spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://${URBAN_SAFE_DB_HOST}:${URBAN_SAFE_DB_PORT}/${URBAN_SAFE_DB_NAME}");
        assertThat(property(sources, "spring.datasource.username"))
                .isEqualTo("${URBAN_SAFE_DB_USER}");
        assertThat(property(sources, "spring.datasource.password"))
                .isEqualTo("${URBAN_SAFE_DB_PASSWORD}");
        assertThat(property(sources, "urban-safe.auth.jwt.secret"))
                .isEqualTo("${URBAN_SAFE_AUTH_JWT_SECRET}");
        assertThat(property(sources, "urban-safe.auth.bootstrap-admin.username"))
                .isEqualTo("${URBAN_SAFE_BOOTSTRAP_ADMIN_USERNAME}");
        assertThat(property(sources, "urban-safe.auth.bootstrap-admin.password"))
                .isEqualTo("${URBAN_SAFE_BOOTSTRAP_ADMIN_PASSWORD}");
        assertThat(property(sources, "urban-safe.web.cors.allowed-origins"))
                .isEqualTo("${URBAN_SAFE_CORS_ALLOWED_ORIGINS}");
        assertThat(property(sources, "urban-safe.map.amap.js-api-key"))
                .isEqualTo("${URBAN_SAFE_AMAP_JS_API_KEY:}");
        assertThat(property(sources, "urban-safe.storage.provider"))
                .isEqualTo("${URBAN_SAFE_STORAGE_PROVIDER}");
        assertThat(property(sources, "urban-safe.storage.access-key"))
                .isEqualTo("${URBAN_SAFE_MINIO_APP_USER}");
        assertThat(property(sources, "urban-safe.storage.secret-key"))
                .isEqualTo("${URBAN_SAFE_MINIO_APP_PASSWORD}");
    }

    @Test
    void productionConfigurationRegistersPersistenceTypeHandlers() throws IOException {
        List<PropertySource<?>> sources = load();

        assertThat(property(sources, "mybatis-plus.type-handlers-package"))
                .isEqualTo("org.urbansafe.priority.persistence.typehandler");
        assertThat(property(sources, "mybatis-plus.mapper-locations"))
                .isEqualTo("classpath*:mappers/**/*.xml");
        assertThat(property(sources, "mybatis-plus.global-config.db-config.id-type"))
                .isEqualTo("input");
    }

    @Test
    void flywayCanStartOnPostgisBundleDatabaseWithoutSkippingInitialMigration()
            throws IOException {
        List<PropertySource<?>> sources = load();

        assertThat(property(sources, "spring.flyway.baseline-on-migrate"))
                .isEqualTo(Boolean.TRUE);
        assertThat(property(sources, "spring.flyway.baseline-version")).isEqualTo(0);
        assertThat(property(sources, "spring.flyway.validate-on-migrate"))
                .isEqualTo(Boolean.TRUE);
    }

    private static String readYaml() throws IOException {
        try (var input = APPLICATION_YAML.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<PropertySource<?>> load() throws IOException {
        return new YamlPropertySourceLoader().load("application.yaml", APPLICATION_YAML);
    }

    private static Object property(List<PropertySource<?>> sources, String name) {
        return sources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }
}
