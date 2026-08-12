package com.flora.hanako.tools;

import com.flora.root.ai.api.ToolSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    @Test
    void readFileRespectsWorkdirBoundary() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "hanako-test-" + System.nanoTime());
        ReadFileTool tool = new ReadFileTool(tmp);
        // 尝试越界读取
        String result = tool.execute(Map.of("path", "../../etc/passwd"));
        assertTrue(result.contains("拒绝"), "越界访问应被 PathGuard 拒绝: " + result);
    }

    @Test
    void registryProducesSpecsAndDispatches() {
        ToolRegistry reg = new ToolRegistry();
        reg.add(new ReadFileTool(Path.of(".")));
        reg.add(new WebFetchTool());
        List<ToolSpec> specs = reg.specs();
        assertEquals(2, specs.size());
        assertTrue(reg.contains("read_file"));
        assertTrue(reg.contains("web_fetch"));

        String out = reg.execute("unknown_tool", Map.of());
        assertTrue(out.contains("未知工具"));
    }

    @Test
    void writeThenReadRoundTrip() {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"), "hanako-io-" + System.nanoTime());
        WriteFileTool w = new WriteFileTool(tmp);
        String written = w.execute(Map.of("path", "sub/demo.txt", "content", "hello hanako"));
        assertTrue(written.contains("已写入"));

        ReadFileTool r = new ReadFileTool(tmp);
        String read = r.execute(Map.of("path", "sub/demo.txt"));
        assertEquals("hello hanako", read);
    }
}
