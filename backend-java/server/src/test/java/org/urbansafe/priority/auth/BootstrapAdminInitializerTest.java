package org.urbansafe.priority.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.urbansafe.priority.auth.config.AuthProperties;
import org.urbansafe.priority.auth.initializer.BootstrapAdminInitializer;
import org.urbansafe.priority.persistence.entity.RoleEntity;
import org.urbansafe.priority.persistence.entity.UserAccountEntity;
import org.urbansafe.priority.persistence.entity.UserRoleEntity;
import org.urbansafe.priority.persistence.mapper.RoleMapper;
import org.urbansafe.priority.persistence.mapper.UserAccountMapper;
import org.urbansafe.priority.persistence.mapper.UserRoleMapper;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private UserAccountMapper userAccountMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private Environment environment;

    @Test
    void existingAdminWithMojibakeRealNameShouldBeRepairedFromConfiguration() {
        AuthProperties properties = new AuthProperties();
        properties.getBootstrapAdmin().setUsername("admin");
        properties.getBootstrapAdmin().setPassword("secret-password");
        properties.getBootstrapAdmin().setRealName("开发管理员");

        RoleEntity role = new RoleEntity();
        role.setId(UUID.randomUUID());
        role.setRoleCode("ADMIN");

        UserAccountEntity admin = new UserAccountEntity();
        admin.setId(UUID.randomUUID());
        admin.setUsername("admin");
        admin.setRealName("å¼\u0080å\u008F\u0091ç®¡ç\u0090\u0086å\u0091\u0098");

        UserRoleEntity binding = new UserRoleEntity();
        binding.setUserId(admin.getId());
        binding.setRoleId(role.getId());

        when(environment.getActiveProfiles()).thenReturn(new String[] {"dev"});
        when(roleMapper.selectOne(any())).thenReturn(role);
        when(userAccountMapper.selectOne(any())).thenReturn(admin);
        when(userRoleMapper.selectOne(any())).thenReturn(binding);

        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(
                userAccountMapper, roleMapper, userRoleMapper, passwordEncoder, properties, environment);
        initializer.run(new DefaultApplicationArguments());

        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(userAccountMapper).updateById(captor.capture());
        assertThat(captor.getValue().getRealName()).isEqualTo("开发管理员");
    }
}
