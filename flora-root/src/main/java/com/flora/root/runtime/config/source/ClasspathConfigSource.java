package com.flora.root.runtime.config.source;

import com.flora.root.runtime.config.ConfigException;
import com.flora.root.runtime.config.impl.ConfigSourceFileFormat;
import com.flora.root.runtime.config.impl.MapConfig;
import com.flora.root.runtime.config.interfaces.Config;
import com.flora.root.runtime.config.interfaces.ConfigSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 从 classpath 资源加载配置的来源（如 {@code "config/app.yaml"}）。
 * <p>与 {@link FileConfigSource} 同构：按资源扩展名经 {@link ConfigSourceFileFormat}
 * 自动识别格式（json/yaml/toml/properties），资源缺失、读取失败或解析失败抛 {@link ConfigException}。</p>
 */
public class ClasspathConfigSource implements ConfigSource {

    private final String resource;

    public ClasspathConfigSource(String resource) {
        if (resource == null || resource.isEmpty()) throw new ConfigException("classpath 资源路径不能为空");
        this.resource = resource.startsWith("/") ? resource.substring(1) : resource;
    }

    @Override
    public Config load() {
        String text = readResource();
        String name = resource.substring(resource.lastIndexOf('/') + 1);
        try {
            return MapConfig.of(ConfigSourceFileFormat.fromFilename(name).parse(text));
        } catch (ConfigException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConfigException("解析 classpath 配置失败: " + resource + " —— " + e.getMessage(), e);
        }
    }

    private String readResource() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = ClasspathConfigSource.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(resource)) {
            if (in == null) throw new ConfigException("classpath 资源不存在: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("读取 classpath 资源失败: " + resource, e);
        }
    }

    @Override
    public String describe() {
        return "classpath:" + resource;
    }
}
