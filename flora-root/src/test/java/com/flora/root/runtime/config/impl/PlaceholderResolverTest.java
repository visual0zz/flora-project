package com.flora.root.runtime.config.impl;

import com.flora.root.runtime.config.ConfigException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlaceholderResolver} 的单元测试。
 */
class PlaceholderResolverTest {

    private static Function<String, String> lookupOf(Map<String, String> values) {
        return values::get;
    }

    @Test
    void resolveSimple() {
        Map<String, String> values = Map.of("host", "localhost", "port", "3306");
        assertEquals("jdbc://localhost:3306", PlaceholderResolver.resolve("jdbc://${host}:${port}", lookupOf(values)));
    }

    @Test
    void resolveWithoutPlaceholderReturnsAsIs() {
        assertEquals("plain", PlaceholderResolver.resolve("plain", key -> null));
    }

    @Test
    void resolveNestedReferences() {
        Map<String, String> values = Map.of("a", "x-${b}", "b", "y");
        assertEquals("x-y", PlaceholderResolver.resolve("${a}", lookupOf(values)));
    }

    @Test
    void resolveMissingKeyThrows() {
        assertThrows(ConfigException.class,
                () -> PlaceholderResolver.resolve("${missing}", key -> null));
    }

    @Test
    void resolveCircularReferenceThrows() {
        Map<String, String> values = Map.of("a", "${b}", "b", "${a}");
        assertThrows(ConfigException.class,
                () -> PlaceholderResolver.resolve("${a}", lookupOf(values)));
    }

    @Test
    void resolveTreeReplacesAllStringLeaves() {
        Map<String, Object> tree = new LinkedHashMap<>();
        tree.put("host", "localhost");
        tree.put("url", "jdbc://${host}");
        tree.put("list", List.of("v-${host}", 42));
        Map<String, Object> sub = new LinkedHashMap<>();
        sub.put("name", "app-${host}");
        tree.put("sub", sub);

        Map<String, Object> resolved = PlaceholderResolver.resolveTree(tree, lookupOf(Map.of("host", "h1")));
        assertEquals("jdbc://h1", resolved.get("url"));
        assertEquals(List.of("v-h1", 42), resolved.get("list"));
        @SuppressWarnings("unchecked")
        Map<String, Object> subResolved = (Map<String, Object>) resolved.get("sub");
        assertEquals("app-h1", subResolved.get("name"));
    }
}
