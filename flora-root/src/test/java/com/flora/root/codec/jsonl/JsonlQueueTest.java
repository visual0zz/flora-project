package com.flora.root.codec.jsonl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.flora.root.codec.json.model.JsonObject;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class JsonlQueueTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndRead() throws Exception {
        Path file = tempDir.resolve("test.jsonl");
        try (JsonlWriter w = new JsonlWriter(file)) {
            w.append(Map.of("id", 1, "msg", "hello"));
            w.append(Map.of("id", 2, "msg", "world"));
        }
        try (JsonlReader r = new JsonlReader(file)) {
            assertEquals("hello", r.read().getString("msg"));
            assertEquals("world", r.read().getString("msg"));
        }
    }

    @Test
    void readReturnsNullOnTimeout() throws Exception {
        Path file = tempDir.resolve("empty.jsonl");
        try (JsonlWriter w = new JsonlWriter(file)) {
            w.append(Map.of("x", 1));
        }
        try (JsonlReader r = new JsonlReader(file, 10)) {
            assertNotNull(r.read(100));
            assertNull(r.read(100)); // 无更多数据，超时返回 null
        }
    }

    @Test
    void writerChainable() throws Exception {
        Path file = tempDir.resolve("chain.jsonl");
        try (JsonlWriter w = new JsonlWriter(file)) {
            w.append(Map.of("a", 1)).append(Map.of("b", 2));
        }
        try (JsonlReader r = new JsonlReader(file)) {
            assertEquals(Long.valueOf(1), r.read().getLong("a"));
            assertEquals(Long.valueOf(2), r.read().getLong("b"));
        }
    }

    @Test
    void crossThreadWriteAndRead() throws Exception {
        Path file = tempDir.resolve("thread.jsonl");
        try (JsonlWriter writer = new JsonlWriter(file);
             JsonlReader reader = new JsonlReader(file, 20)) {

            // 另一个线程读取
            ExecutorService exec = Executors.newSingleThreadExecutor();
            Future<JsonObject> future = exec.submit(() -> reader.read(5000));

            // 主线程写入
            Thread.sleep(50); // 确保读取者已就绪
            writer.append(Map.of("from", "thread"));

            JsonObject result = future.get(3, TimeUnit.SECONDS);
            assertEquals("thread", result.getString("from"));
            exec.shutdown();
        }
    }

    @Test
    void readBlocksWhenFileEmpty() throws Exception {
        Path file = tempDir.resolve("wait.jsonl");
        // 先创建空文件
        try (JsonlWriter w = new JsonlWriter(file)) { /* 空 */ }

        ExecutorService exec = Executors.newSingleThreadExecutor();
        try (JsonlReader reader = new JsonlReader(file, 20);
             JsonlWriter writer = new JsonlWriter(file)) {

            Future<JsonObject> future = exec.submit(() -> reader.read(3000));

            Thread.sleep(100);
            writer.append(Map.of("later", "data"));

            JsonObject result = future.get(3, TimeUnit.SECONDS);
            assertEquals("data", result.getString("later"));
            exec.shutdown();
        }
    }
}
