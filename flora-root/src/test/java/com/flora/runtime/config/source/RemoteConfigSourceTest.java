package com.flora.runtime.config.source;

import com.flora.common.RemoteKVSource;
import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.ConfigSchema;
import com.flora.runtime.config.interfaces.Config;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link RemoteConfigSource} 的单元测试。
 */
class RemoteConfigSourceTest {

    /** 内存键值桩。 */
    private static final class MapKV implements RemoteKVSource {
        final Map<String, String> map = new HashMap<>();

        @Override public String get(String key) { return map.get(key); }
        @Override public boolean exists(String key) { return map.containsKey(key); }
    }

    @Test
    void nestedKeysExpandToMap() {
        MapKV kv = new MapKV();
        kv.map.put("db.host", "localhost");
        kv.map.put("db.port", "3306");
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("db.host", "db.port"));

        Config config = source.load();
        assertEquals("localhost", config.get("db.host"));
        assertEquals("3306", config.get("db.port"));
        assertNotNull(config.getSubConfig("db"));
        assertEquals("localhost", config.getSubConfig("db").get("host"));
    }

    @Test
    void missingKeyFillsNull() {
        MapKV kv = new MapKV();
        kv.map.put("a", "1");
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("a", "missing"));

        Config config = source.load();
        assertNull(config.get("missing"));
        assertTrue(config.toMapTree().containsKey("missing"));
        assertNull(config.toMapTree().get("missing"));
    }

    @Test
    void nestedMissingKeyFillsNull() {
        MapKV kv = new MapKV();
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("x.y"));

        Config config = source.load();
        assertNull(config.get("x.y"));
        Config sub = config.getSubConfig("x");
        assertNotNull(sub);
        assertTrue(sub.toMapTree().containsKey("y"));
        assertNull(sub.toMapTree().get("y"));
    }

    @Test
    void allMissingStillExposesKeys() {
        MapKV kv = new MapKV();
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("a", "b.c"));

        Config config = source.load();
        assertFalse(config.isEmpty());
        assertNull(config.get("a"));
        assertNull(config.get("b.c"));
    }

    @Test
    void emptySchemaYieldsEmptyConfig() {
        MapKV kv = new MapKV();
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of());

        assertTrue(source.load().isEmpty());
    }

    @Test
    void loadedConfigIsIsolatedFromKvMutation() {
        MapKV kv = new MapKV();
        kv.map.put("k", "v1");
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("k"));

        Config config = source.load();
        kv.map.put("k", "v2");
        assertEquals("v1", config.get("k"));
    }

    @Test
    void kvReadFailureWrappedAsConfigException() {
        RemoteKVSource failing = new RemoteKVSource() {
            @Override public String get(String key) { throw new IllegalStateException("backend down"); }
            @Override public boolean exists(String key) { return false; }
        };
        RemoteConfigSource source = new RemoteConfigSource(failing, ConfigSchema.of("a"));

        ConfigException ex = assertThrows(ConfigException.class, source::load);
        assertTrue(ex.getMessage().contains("a"));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    @Test
    void nullArgsRejected() {
        assertThrows(ConfigException.class, () -> new RemoteConfigSource(null, ConfigSchema.of("a")));
        assertThrows(ConfigException.class, () -> new RemoteConfigSource(new MapKV(), null));
    }

    @Test
    void describeContainsSourceAndSchemaKeys() {
        MapKV kv = new MapKV();
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("db.host"));

        String describe = source.describe();
        assertTrue(describe.contains("remote("));
        assertTrue(describe.contains("db.host"));
    }

    @Test
    void toLongKeyMapFlattensDotPaths() {
        MapKV kv = new MapKV();
        kv.map.put("db.host", "localhost");
        kv.map.put("db.port", "3306");
        kv.map.put("missing", null);
        RemoteConfigSource source = new RemoteConfigSource(kv, ConfigSchema.of("db.host", "db.port", "missing"));

        Config config = source.load();
        Map<String, Object> flat = config.toLongKeyMap();
        assertEquals("localhost", flat.get("db.host"));
        assertEquals("3306", flat.get("db.port"));
        assertTrue(flat.containsKey("missing"));
        assertNull(flat.get("missing"));
        assertEquals(3, flat.size());
    }
}
