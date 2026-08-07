package org.urbansafe.priority.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第一阶段后端分层和代码生成边界测试。
 *
 * <p>这些规则把提示词中的目录约定转为可执行约束，避免后续开发在编译仍能通过的情况下重新引入
 * Controller 直连 Mapper、持久化实体进入接口层或状态码绑定响应类等架构回退。
 */
class Phase1ArchitectureTest {

    /** 需要由 ArchUnit 扫描的项目根包。 */
    private static final String BASE_PACKAGE = "org.urbansafe.priority";

    /** 验证 Controller 不会绕过 Service/Repository 直接访问 Mapper。 */
    @Test
    void controllersMustNotDependOnMappers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat()
                .resideInAPackage("..mapper..");

        rule.check(importedClasses());
    }

    /** 验证 Service 不会反向依赖 Web Controller。 */
    @Test
    void servicesMustNotDependOnControllers() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAPackage("..controller..");

        rule.check(importedClasses());
    }

    /** Controller 不得依赖或暴露 persistence 实体。 */
    @Test
    void controllersMustNotDependOnPersistenceEntities() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..controller..")
                .should().dependOnClassesThat()
                .resideInAPackage("..persistence.entity..");

        rule.check(importedClasses());
    }

    /** 业务 Service 不得绕过统一审计服务直接访问 OperationLogMapper。 */
    @Test
    void businessServicesMustNotDependOnOperationLogMapper() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .haveSimpleName("OperationLogMapper");

        rule.check(importedClasses());
    }

    /** OpenAPI model 模块不得反向依赖 persistence。 */
    @Test
    void modelMustNotDependOnPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..model..")
                .should().dependOnClassesThat()
                .resideInAPackage("..persistence..");

        rule.check(importedClasses());
    }

    /** persistence 模块不得依赖 OpenAPI DTO。 */
    @Test
    void persistenceMustNotDependOnOpenApiDtos() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..persistence..")
                .should().dependOnClassesThat()
                .resideInAPackage("..model.dto..");

        rule.check(importedClasses());
    }

    /** 验证业务 Service 只返回内部结果，不能直接依赖 OpenAPI 生成 DTO。 */
    @Test
    void servicesMustNotDependOnOpenApiDtos() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..service..", "..service.impl..")
                .should().dependOnClassesThat()
                .resideInAPackage("..model.dto..");

        rule.check(importedClasses());
    }

    /** 所有持久化 Entity 必须位于 persistence.entity 包。 */
    @Test
    void entityNamedClassesMustOnlyResideInPersistence() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Entity")
                .should().resideOutsideOfPackage("..persistence.entity..");

        rule.check(importedClasses());
    }

    /** 每个专用 REST Controller 必须直接实现一个 OpenAPI Generator 生成接口。 */
    @Test
    void controllersMustImplementGeneratedApi() {
        importedClasses().stream()
                .filter(javaClass -> javaClass.isAnnotatedWith(RestController.class))
                .forEach(javaClass -> org.assertj.core.api.Assertions.assertThat(
                                javaClass.getRawInterfaces().stream()
                                        .anyMatch(api -> api.getPackageName().equals(
                                                "org.urbansafe.priority.model.api")))
                        .as("Controller %s 必须实现 model.api 下的生成接口", javaClass.getName())
                        .isTrue());
    }

    /** 验证 OpenAPI model 模块不会出现任何数据库实体或旧公共实体基类。 */
    @Test
    void entitiesMustNotResideInModelModule() {
        ArchRule rule = noClasses()
                .that().haveSimpleNameEndingWith("Entity")
                .or().haveSimpleName("BaseEntity")
                .should().resideInAPackage("org.urbansafe.priority.model..");

        rule.check(importedClasses());
    }

    /**
     * 验证 OpenAPI 成功响应使用业务语义命名，禁止重新生成与具体 HTTP 状态码绑定的 DTO。
     */
    @Test
    void generatedResponsesMustUseSemanticNames() {
        List<String> statusBoundNames = importedClasses().stream()
                .filter(javaClass -> javaClass.getPackageName().equals(
                        "org.urbansafe.priority.model.dto"))
                .map(javaClass -> javaClass.getSimpleName())
                .filter(simpleName -> simpleName.matches(".*\\d{3}Response.*"))
                .sorted()
                .toList();

        org.assertj.core.api.Assertions.assertThat(statusBoundNames)
                .as("OpenAPI 响应模型不得与 200、201、400 等状态码绑定")
                .isEmpty();
    }

    /**
     * 验证所有失败状态都复用唯一公共 ErrorResponse，不产生 ErrorResponse1 等重复类型。
     */
    @Test
    void generatedContractMustContainOnlyOneErrorResponseType() {
        List<String> errorResponseNames = importedClasses().stream()
                .filter(javaClass -> javaClass.getPackageName().equals(
                        "org.urbansafe.priority.model.dto"))
                .map(javaClass -> javaClass.getSimpleName())
                .filter(simpleName -> simpleName.startsWith("ErrorResponse"))
                .sorted()
                .toList();

        org.assertj.core.api.Assertions.assertThat(errorResponseNames)
                .containsExactly("ErrorResponse");
    }

    /**
     * 导入生产代码类并排除测试类，确保规则只约束交付产物。
     *
     * @return 当前构建输出中的 UrbanSafe Priority 生产类集合
     */
    private static JavaClasses importedClasses() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(BASE_PACKAGE);
    }
}
