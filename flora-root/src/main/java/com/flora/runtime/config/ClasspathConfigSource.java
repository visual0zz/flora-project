package com.flora.runtime.config;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * 从类路径资源中加载配置的源。
 * <p>通过 {@link ClassLoader#getResourceAsStream(String)} 查找资源。
 * 格式从文件名扩展名自动识别。</p>
 */
public class ClasspathConfigSource implements ConfigSource {

    private final String resourcePath;
    private final ConfigFormat format;

    /**
     * 创建类路径配置源，格式从文件名扩展名自动推断。
     *
     * @param resourcePath 类路径资源路径（如 {@code config/app.yaml}）
     */
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
