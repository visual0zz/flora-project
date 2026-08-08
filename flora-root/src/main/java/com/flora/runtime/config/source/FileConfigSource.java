package com.flora.runtime.config.source;

import com.flora.runtime.config.interfaces.Config;
import com.flora.runtime.config.ConfigException;
import com.flora.runtime.config.impl.ConfigFormat;
import com.flora.runtime.config.interfaces.ConfigSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * 从文件系统中加载配置的源。
 * <p>根据文件扩展名自动识别格式（{@link ConfigFormat#fromFilename(String)}）。</p>
 */
public class FileConfigSource implements ConfigSource {

    private final Path filePath;
    private final ConfigFormat format;

    public FileConfigSource(Path filePath) {
        this.filePath = filePath.toAbsolutePath().normalize();
        this.format = ConfigFormat.fromFilename(filePath.getFileName().toString());
    }

    public FileConfigSource(Path filePath, ConfigFormat format) {
        this.filePath = filePath.toAbsolutePath().normalize();
        this.format = format;
    }

    @Override
    public Config load() {
        String text = readFile();
        Map<String, Object> parsed = format.parse(text);
        return Config.of(parsed);
    }

    @Override
    public String describe() {
        return "file:" + filePath;
    }

    @Override
    public String location() {
        return filePath.toString();
    }

    private String readFile() {
        File file = filePath.toFile();
        if (!file.exists()) throw new ConfigException("配置文件不存在: " + filePath);
        if (!file.isFile()) throw new ConfigException("路径不是文件: " + filePath);
        try {
            return new String(java.nio.file.Files.readAllBytes(filePath), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ConfigException("读取配置文件失败: " + filePath, e);
        }
    }
}
