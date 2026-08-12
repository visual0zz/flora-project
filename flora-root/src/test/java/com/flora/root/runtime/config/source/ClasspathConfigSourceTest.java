package com.flora.root.runtime.config.source;

import com.flora.root.runtime.config.ConfigException;
import com.flora.root.runtime.config.interfaces.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ClasspathConfigSource} 的单元测试。
 */
class ClasspathConfigSourceTest {

    @Test
    void loadYamlFromClasspath() {
        Config config = new ClasspathConfigSource("config/app.yaml").load();
        assertEquals("test-app", config.get("app.name"));
        assertEquals(8080L, config.get("server.port"));
    }

    @Test
    void loadPropertiesFromClasspath() {
        Config config = new ClasspathConfigSource("config/app.properties").load();
        assertEquals("props-app", config.get("app.name"));
        assertEquals("8081", config.get("server.port"));
    }

    @Test
    void resourceNotFoundThrows() {
        assertThrows(ConfigException.class,
                () -> new ClasspathConfigSource("nonexistent/config.yaml").load());
    }

    @Test
    void leadingSlashIsNormalized() {
        Config config = new ClasspathConfigSource("/config/app.yaml").load();
        assertEquals("test-app", config.get("app.name"));
    }

    @Test
    void emptyResourceThrows() {
        assertThrows(ConfigException.class, () -> new ClasspathConfigSource(""));
    }
}
