package com.flora.codec.jsonl;

import com.flora.codec.JsonUtil;
import com.flora.codec.json.model.JsonObject;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSONL 读取端。
 * <p>顺序读取 JSONL 文件，无可用数据时阻塞等待。可跨线程/跨进程工作。</p>
 */
public class JsonlReader implements Closeable {

    private final Path path;
    private RandomAccessFile raf;
    private final long pollIntervalMs;

    public JsonlReader(Path path) throws IOException {
        this(path, 100);
    }

    /**
     * @param path           JSONL 文件路径
     * @param pollIntervalMs 无数据时的轮询间隔（毫秒）
     */
    public JsonlReader(Path path, long pollIntervalMs) throws IOException {
        this.path = path;
        this.pollIntervalMs = pollIntervalMs;
        ensureOpen();
    }

    /**
     * 读取下一行 JSON 对象。阻塞直到有数据可用。
     *
     * @return 解析后的 JsonObject，读取中断时返回 null
     */
    public JsonObject read() throws IOException {
        try {
            return read(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    /**
     * 读取下一行 JSON 对象，带超时。
     *
     * @param timeoutMs 超时毫秒
     * @return 解析后的 JsonObject，超时或无数据时返回 null
     * @throws InterruptedException 线程中断
     */
    public JsonObject read(long timeoutMs) throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (true) {
            JsonObject result = tryRead();
            if (result != null) return result;

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) return null;

            Thread.sleep(Math.min(pollIntervalMs, remaining));
        }
    }

    private JsonObject tryRead() throws IOException {
        ensureOpen();
        String line = readNextLine();
        if (line == null) return null;
        return JsonUtil.parseObject(line);
    }

    private String readNextLine() throws IOException {
        if (raf.getFilePointer() >= raf.length()) return null;

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = raf.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        return buf.size() > 0 ? buf.toString(StandardCharsets.UTF_8) : null;
    }

    private void ensureOpen() throws IOException {
        if (raf != null && raf.getFD().valid()) return;
        // 等待文件出现
        while (!Files.exists(path)) {
            try { Thread.sleep(pollIntervalMs); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        if (raf != null) raf.close();
        raf = new RandomAccessFile(path.toFile(), "r");
    }

    @Override
    public void close() throws IOException {
        if (raf != null) raf.close();
    }
}
