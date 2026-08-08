package com.flora.hanako.storage;

import com.flora.codec.json.JsonBuilder;
import com.flora.codec.json.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 文件 JSON 存储辅助：读写 + 原子写（temp + rename）。
 * <p>复刻「append-only / 配置文件」的持久化底座；所有写入先落临时文件再原子改名，
 * 避免进程中断留下半截文件（对应基座能力评估 E1 的原子写原语）。</p>
 */
public final class JsonStores {

    private JsonStores() {
    }

    /** 读取并解析 JSON 文件为对象（不存在时返回 null）。 */
    @SuppressWarnings("unchecked")
    public static <T> T read(Path file, Class<T> type) {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            Object parsed = JsonParser.parse(raw);
            if (type.isInstance(parsed)) {
                return (T) parsed;
            }
            return null;
        } catch (IOException e) {
            throw new IllegalStateException("读取 JSON 失败: " + file, e);
        }
    }

    /** 序列化对象并原子写入文件（临时文件 + rename）。 */
    public static void writeAtomically(Path file, Object value) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            String json = JsonBuilder.toPrettyJsonString(value);
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("原子写入 JSON 失败: " + file, e);
        }
    }

    /** 读取为 Map（便捷）。 */
    @SuppressWarnings("unchecked")
    public static java.util.Map<String, Object> readMap(Path file) {
        Object v = read(file, Object.class);
        if (v instanceof java.util.Map) {
            return (java.util.Map<String, Object>) v;
        }
        return new java.util.LinkedHashMap<>();
    }
}
