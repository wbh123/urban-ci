package org.urbansafe.priority.support;

import org.testcontainers.utility.DockerImageName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.urbansafe.priority.TestApplication;

@SpringBootTest(classes = TestApplication.class)
// 为继承该基类的 Controller 集成测试创建 MockMvc，并保留完整的安全过滤器链和异常处理器。
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class PostgreSqlIntegrationTestBase {

    /**
     * 集成测试数据库镜像系统属性名。
     *
     * <p>开发者可以通过 {@code -Durban.safe.test.postgres.image=镜像名} 覆盖默认镜像，
     * 但默认值必须是已经预装 PostGIS、pgvector 和 PostgreSQL contrib 扩展的固定版本镜像。
     */
    private static final String DATABASE_IMAGE_PROPERTY = "urban.safe.test.postgres.image";

    /**
     * 默认集成测试镜像。
     *
     * <p>该 bundle 镜像在构建阶段已经安装 PostGIS 与 pgvector；pgcrypto 来自 PostgreSQL contrib。
     * 测试运行期间只创建容器和扩展，绝不再通过 apt 在线安装系统软件包。
     */
    private static final String DEFAULT_DATABASE_IMAGE =
            "imresamu/postgis:17-3.6.1-bundle0-bookworm";

    /** PostgreSQLContainer 只接受 postgres 兼容镜像，因此显式声明兼容关系。 */
    private static final DockerImageName DATABASE_IMAGE = DockerImageName
            .parse(System.getProperty(DATABASE_IMAGE_PROPERTY, DEFAULT_DATABASE_IMAGE))
            .asCompatibleSubstituteFor("postgres");

    /**
     * 全测试进程共享一个数据库容器，避免每个测试类重复拉起 PostgreSQL。
     */
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DATABASE_IMAGE)
                    .withDatabaseName("urban_safe_priority")
                    .withUsername("urban_safe_dev")
                    .withPassword("urban_safe_dev_password")
                    // bundle 镜像会预先 CREATE EXTENSION，测试先清理对象，再由 Flyway V1 作为唯一入口创建。
                    .withInitScript("testcontainer/clean-preinstalled-extensions.sql");

    @Autowired
    protected ApplicationContext applicationContext;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    protected <T> T getMapper(Class<T> mapperClass) {
        return applicationContext.getBean(mapperClass);
    }
}
