package com.flora.root.runtime.config.source;

import com.flora.root.runtime.config.ConfigException;
import com.flora.root.runtime.config.ConfigSchema;
import com.flora.root.runtime.config.interfaces.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link EnvConfigSource} 与 {@link SystemPropertiesConfigSource} 的单元测试。
 */
class EnvSystemPropertiesConfigSourceTest {

    @Test
    void envMissingKeyFillsNull() {
        Config config = new EnvConfigSource(ConfigSchema.of("FLORA_TEST_DEFINITELY_MISSING_VAR_XYZ")).load();
        assertNull(config.get("FLORA_TEST_DEFINITELY_MISSING_VAR_XYZ"));
        assertTrue(config.toMapTree().containsKey("FLORA_TEST_DEFINITELY_MISSING_VAR_XYZ"));
    }

    @Test
    void envNullSchemaThrows() {
        assertThrows(ConfigException.class, () -> new EnvConfigSource(null));
    }

    @Test
    void envDescribeContainsSource() {
        EnvConfigSource source = new EnvConfigSource(ConfigSchema.of("A", "B"));
        assertTrue(source.describe().startsWith("env:"));
    }

    @Test
    void systemPropertiesReadsValue() {
        System.setProperty("flora.test.prop", "hello");
        try {
            Config config = new SystemPropertiesConfigSource(ConfigSchema.of("flora.test.prop")).load();
            assertEquals("hello", config.get("flora.test.prop"));
        } finally {
            System.clearProperty("flora.test.prop");
        }
    }

    @Test
    void systemPropertiesMissingKeyFillsNull() {
        Config config = new SystemPropertiesConfigSource(ConfigSchema.of("flora.test.missing")).load();
        assertNull(config.get("flora.test.missing"));
    }

    @Test
    void systemPropertiesNullSchemaThrows() {
        assertThrows(ConfigException.class, () -> new SystemPropertiesConfigSource(null));
    }
}
