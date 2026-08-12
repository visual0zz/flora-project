package com.flora.root.codec.jsonl;

import com.flora.root.codec.JsonUtil;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * JSONL 写入端。
 * <p>以追加模式打开文件，每调用一次 {@link #append} 写入一行 JSON。</p>
 */
public class JsonlWriter implements Closeable {

    private final BufferedWriter writer;

    /**
     * 打开 JSONL 文件用于追加写入。文件不存在则创建。
     */
    public JsonlWriter(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        this.writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    /**
     * 将对象序列化为 JSON 并追加为一行。
     *
     * @param obj 任意可被 {@link JsonUtil#toJsonString} 序列化的对象
     */
    public JsonlWriter append(Object obj) throws IOException {
        String json = JsonUtil.toJsonString(obj);
        writer.write(json);
        writer.newLine();
        writer.flush();
        return this;
    }

    @Override
    public void close() throws IOException {
        writer.close();
    }
}
