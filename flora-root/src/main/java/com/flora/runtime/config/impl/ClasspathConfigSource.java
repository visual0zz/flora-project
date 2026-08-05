package com.flora.runtime.config.impl;

import com.flora.runtime.config.Config;
import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.ConfigFormat;
import com.flora.runtime.config.ConfigSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从类路径资源中加载配置的源。
 */
public class ClasspathConfigSource implements ConfigSource {

    private final String resourcePath;
    private final ConfigFormat format;

    public ClasspathConfigSource(String resourcePath) {
        this.resourcePath = resourcePath;
        this.format = ConfigFormat.fromFilename(resourcePath);
    }

    @Override
    public Config load() {
        InputStream is = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(resourcePath);
        if (is == null) {
            is = getClass().getClassLoader().getResourceAsStream(resourcePath);
        }
        if (is == null) {
            throw new ConfigException("类路径资源未找到: " + resourcePath);
        }
        try {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return Config.of(format.parse(text));
        } catch (IOException e) {
            throw new ConfigException("读取类路径资源失败: " + resourcePath, e);
        } finally {
            try { is.close(); } catch (IOException ignored) {}
        }
    }

    @Override
    public String describe() {
        return "classpath:" + resourcePath;
    }

    @Override
    public String location() {
        return resourcePath;
    }
}
