package org.urbansafe.priority.ai.tools;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

/**
 * Spring AI 2.0 按 {@link Tool#name()}（缺省时回退到方法名）解析工具名，
 * 若两个 {@code @Tool} 方法解析出同名，会在注册工具集时抛出
 * {@code Multiple tools with the same name (xxx) found in sources}，
 * 导致综合研判整条链路失败。此测试保证当前工具集工具名唯一，防止回归。
 */
class ToolNameUniquenessTest {

    @Test
    void toolNamesMustBeUniqueAcrossRegisteredTools() throws Exception {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        Map<String, String> byName = new HashMap<>();
        List<String> duplicates = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("org.urbansafe.priority.ai.tools")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            for (Method method : type.getDeclaredMethods()) {
                Tool tool = method.getAnnotation(Tool.class);
                if (tool == null) {
                    continue;
                }
                String name = tool.name() == null || tool.name().isBlank()
                        ? method.getName()
                        : tool.name();
                String owner = type.getSimpleName() + "#" + method.getName();
                String previous = byName.putIfAbsent(name, owner);
                if (previous != null) {
                    duplicates.add(name + " -> " + previous + " 与 " + owner);
                }
            }
        }

        assertThat(duplicates)
                .as("工具名必须唯一，避免 Spring AI 注册工具集时抛出同名冲突")
                .isEmpty();
        assertThat(byName).as("至少应扫描到已注册工具").isNotEmpty();
    }
}
