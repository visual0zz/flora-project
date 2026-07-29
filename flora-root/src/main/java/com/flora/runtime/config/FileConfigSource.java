package com.flora.runtime.config;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

/**
 * 从文件系统中加载配置的源。
 * <p>根据文件扩展名自动识别格式（{@link ConfigFormat#fromFilename(String)}）。</p>
 *
 * <p>路径可以是：
 * <ul>
 *   <li>绝对路径：{@code /etc/app/config.yaml}</li>
 *   <li>相对路径：相对于 {@code user.dir}</li>
 * </ul>
 */
public class FileConfigSource implements ConfigSource {

    private final Path filePath;
    private final ConfigFormat format;

    /**
     * 创建文件配置源，格式从文件扩展名自动推断。
     *
     * @param filePath 文件路径
     */
    public FileConfigSource(Path filePath) {
        this.filePath = filePath.toAbsolutePath().normalize();
        this.format = ConfigFormat.fromFilename(filePath.getFileName().toString());
    }

    /**
     * 创建文件配置源，显式指定格式。
     *
     * @param filePath 文件路径
     * @param format   配置格式
     */
    public FileConfigSource(Path filePath, ConfigFormat format) {
        this.filePath = filePath.toAbsolutePath().normalize();
        this.format = format;
    }

    @Override
    public ConfigMap load() {
        String text = readFile();
        Map<String, Object> parsed = format.parse(text);
        return ConfigMap.of(parsed);
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
