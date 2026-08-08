package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 可视化建档的地图发现能力必须由独立服务承载，避免继续膨胀 Phase2MapService。 */
class MapDiscoveryServiceTest {

    @Test
    void exposesStandaloneDiscoveryServiceContract() throws Exception {
        Class<?> serviceType = load("org.urbansafe.priority.map.service.MapDiscoveryService");

        Method geocode = serviceType.getMethod("geocode", String.class, String.class);
        Method searchPlaces = serviceType.getMethod(
                "searchPlaces", String.class, String.class, boolean.class, int.class);
        Method reverseGeocode = serviceType.getMethod("reverseGeocode", double.class, double.class);

        assertThat(geocode.getReturnType()).isEqualTo(Map.class);
        assertThat(searchPlaces.getReturnType()).isEqualTo(List.class);
        assertThat(reverseGeocode.getReturnType()).isEqualTo(Map.class);
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            assertThat(ex)
                    .as("地图发现服务 %s 应独立存在", className)
                    .isNull();
            throw new AssertionError("unreachable", ex);
        }
    }
}
