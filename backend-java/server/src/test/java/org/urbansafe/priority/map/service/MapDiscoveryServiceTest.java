package org.urbansafe.priority.map.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.urbansafe.priority.map.config.AmapProperties;
import org.urbansafe.priority.map.config.MapProperties;

/** 可视化建档的地图发现能力必须由独立服务与只读高德网关承载。 */
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

    @Test
    void discoveryServiceDependsOnDedicatedReadOnlyAmapGateway() throws Exception {
        Class<?> gatewayType = load("org.urbansafe.priority.map.service.AmapDiscoveryGateway");
        Class<?> serviceType = load("org.urbansafe.priority.map.service.MapDiscoveryService");

        Method geocode = gatewayType.getMethod("geocode", String.class, String.class);
        Method searchPlaces = gatewayType.getMethod(
                "searchPlaces", String.class, String.class, boolean.class, int.class);
        Method reverseGeocode = gatewayType.getMethod("reverseGeocode", double.class, double.class);
        Constructor<?> constructor = serviceType.getConstructor(
                MapProperties.class, AmapProperties.class, gatewayType);

        assertThat(geocode.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(searchPlaces.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(reverseGeocode.getReturnType().getName()).isEqualTo("com.fasterxml.jackson.databind.JsonNode");
        assertThat(constructor).isNotNull();
    }

    private Class<?> load(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ex) {
            assertThat(ex)
                    .as("地图发现组件 %s 应独立存在", className)
                    .isNull();
            throw new AssertionError("unreachable", ex);
        }
    }
}
