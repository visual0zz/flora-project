package com.flora.root.runtime.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConfigSchema} 的单元测试。
 */
class ConfigSchemaTest {

    @Test
    void ofVarargs() {
        ConfigSchema schema = ConfigSchema.of("db.host", "db.port", "app.name");
        assertEquals(3, schema.keys().size());
        assertTrue(schema.contains("db.host"));
        assertTrue(schema.contains("app.name"));
        assertFalse(schema.contains("missing"));
    }

    @Test
    void ofCollection() {
        ConfigSchema schema = ConfigSchema.of(List.of("a", "b.c"));
        assertEquals(2, schema.keys().size());
        assertTrue(schema.contains("b.c"));
    }

    @Test
    void keysKeepDeclarationOrder() {
        ConfigSchema schema = ConfigSchema.of("z", "a", "m");
        assertEquals(List.of("z", "a", "m"), List.copyOf(schema.keys()));
    }

    @Test
    void keysIsImmutable() {
        ConfigSchema schema = ConfigSchema.of("a", "b");
        assertThrows(UnsupportedOperationException.class, () -> schema.keys().add("c"));
    }

    @Test
    void duplicateKeysAreDeduplicated() {
        ConfigSchema schema = ConfigSchema.of("a", "a", "b");
        assertEquals(2, schema.keys().size());
    }

    @Test
    void nullKeysCollectionThrows() {
        assertThrows(ConfigException.class, () -> ConfigSchema.of((List<String>) null));
    }

    @Test
    void invalidKeysThrow() {
        assertThrows(ConfigException.class, () -> ConfigSchema.of((String) null));
        assertThrows(ConfigException.class, () -> ConfigSchema.of(""));
        assertThrows(ConfigException.class, () -> ConfigSchema.of("a..b"));
        assertThrows(ConfigException.class, () -> ConfigSchema.of(".a"));
        assertThrows(ConfigException.class, () -> ConfigSchema.of("a."));
    }

    @Test
    void prefixConflictThrows() {
        assertThrows(ConfigException.class, () -> ConfigSchema.of("a.b", "a.b.c"));
        assertThrows(ConfigException.class, () -> ConfigSchema.of("a.b.c", "a.b"));
    }

    @Test
    void similarPrefixNotConflict() {
        // "ab" 与 "a.b" 按点号段边界不冲突
        ConfigSchema schema = ConfigSchema.of("ab", "a.b");
        assertEquals(2, schema.keys().size());
    }
}
