package org.urbansafe.priority.auth.initializer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.urbansafe.priority.auth.config.AuthProperties;
import org.urbansafe.priority.persistence.entity.RoleEntity;
import org.urbansafe.priority.persistence.entity.UserAccountEntity;
import org.urbansafe.priority.persistence.entity.UserRoleEntity;
import org.urbansafe.priority.persistence.mapper.RoleMapper;
import org.urbansafe.priority.persistence.mapper.UserAccountMapper;
import org.urbansafe.priority.persistence.mapper.UserRoleMapper;

@Component
@ConditionalOnProperty(prefix = "urban-safe.auth.bootstrap-admin", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LogManager.getLogger(BootstrapAdminInitializer.class);
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserAccountMapper userAccountMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties authProperties;
    /** 当前 Spring 环境，用于阻止生产环境误启用初始化管理员。 */
    private final Environment environment;

    public BootstrapAdminInitializer(
            UserAccountMapper userAccountMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordEncoder passwordEncoder,
            AuthProperties authProperties,
            Environment environment) {
        this.userAccountMapper = userAccountMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
        this.authProperties = authProperties;
        this.environment = environment;
    }

    /**
     * 在允许的开发/初始化环境中创建或补全 Bootstrap 管理员。
     *
     * <p>方法整体位于同一事务：ADMIN 角色不存在、用户写入失败或角色绑定失败时，
     * 都不会留下没有角色的管理员账号。
     *
     * @param args Spring Boot 启动参数，本初始化流程不读取其中的敏感信息
     */
    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AuthProperties.BootstrapAdmin adminProps = authProperties.getBootstrapAdmin();
        assertAllowedEnvironment();

        String username = adminProps.getUsername() == null
                ? ""
                : adminProps.getUsername().trim();
        String password = adminProps.getPassword();

        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "启用 bootstrap-admin 时 urban-safe.auth.bootstrap-admin.password 不能为空");
        }
        if (username.isBlank()) {
            throw new IllegalStateException(
                    "启用 bootstrap-admin 时 urban-safe.auth.bootstrap-admin.username 不能为空");
        }

        RoleEntity adminRole = roleMapper.selectOne(
                new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getRoleCode, ADMIN_ROLE_CODE));
        if (adminRole == null) {
            throw new IllegalStateException("ADMIN 角色不存在，拒绝创建无角色的 Bootstrap 管理员");
        }

        UserAccountEntity existing = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountEntity>()
                        .eq(UserAccountEntity::getUsername, username));

        if (existing != null) {
            repairExistingAdminProfile(existing, adminProps.getRealName());
            ensureAdminRoleBinding(existing, adminRole);
            LOGGER.info("Bootstrap admin user '{}' already exists and ADMIN role is ensured", username);
            return;
        }

        UserAccountEntity admin = new UserAccountEntity();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRealName(adminProps.getRealName());
        admin.setStatus("ACTIVE");
        userAccountMapper.insert(admin);

        ensureAdminRoleBinding(admin, adminRole);
        LOGGER.info("Bootstrap admin user '{}' created with ADMIN role", username);
    }

    /**
     * Bootstrap 初始化只允许在显式开发、测试或初始化 profile 中运行。
     */
    private void assertAllowedEnvironment() {
        boolean allowed = java.util.Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .anyMatch(profile -> profile.equals("dev")
                        || profile.equals("local")
                        || profile.equals("test")
                        || profile.equals("init"));
        if (!allowed) {
            throw new IllegalStateException(
                    "bootstrap-admin 仅允许在 dev、local、test 或 init profile 中启用");
        }
    }

    /**
     * 仅修复启动管理员的空白或历史乱码显示名，避免覆盖人工维护的正常名称。
     */
    private void repairExistingAdminProfile(UserAccountEntity user, String configuredRealName) {
        if (configuredRealName == null || configuredRealName.isBlank()) {
            return;
        }
        String currentRealName = user.getRealName();
        if (currentRealName != null && !currentRealName.isBlank() && !looksMojibake(currentRealName)) {
            return;
        }

        user.setRealName(configuredRealName);
        userAccountMapper.updateById(user);
        LOGGER.info("Bootstrap admin user '{}' real name repaired from local configuration", user.getUsername());
    }

    private boolean looksMojibake(String value) {
        return value.chars().anyMatch(ch -> ch >= 0x80 && ch <= 0x9F)
                || value.contains("Ã")
                || value.contains("Â")
                || value.contains("å¼")
                || value.contains("ç®");
    }

    /**
     * 确保指定用户与 ADMIN 角色存在有效绑定。
     *
     * @param user 管理员账号
     * @param adminRole ADMIN 角色
     */
    private void ensureAdminRoleBinding(UserAccountEntity user, RoleEntity adminRole) {
        UserRoleEntity existingBinding = userRoleMapper.selectOne(
                new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, user.getId())
                        .eq(UserRoleEntity::getRoleId, adminRole.getId()));
        if (existingBinding == null) {
            UserRoleEntity userRole = new UserRoleEntity();
            userRole.setUserId(user.getId());
            userRole.setRoleId(adminRole.getId());
            userRoleMapper.insert(userRole);
        }
    }
}
