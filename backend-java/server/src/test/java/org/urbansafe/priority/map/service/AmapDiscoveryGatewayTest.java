package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.map.config.AmapProperties;

/** 高德发现网关必须从统一配置获得服务地址、密钥与超时，便于真实调用和受控测试。 */
class AmapDiscoveryGatewayTest {

    @Test
    void gatewayUsesAmapPropertiesConstructor() throws Exception {
        Constructor<AmapDiscoveryGateway> constructor =
                AmapDiscoveryGateway.class.getConstructor(AmapProperties.class);

        assertThat(constructor).isNotNull();
    }
}
