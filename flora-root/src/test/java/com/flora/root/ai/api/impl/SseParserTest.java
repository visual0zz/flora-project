package com.flora.root.ai.api.impl;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SseParser} 测试。
 */
class SseParserTest {

    private List<String> parse(String sse) throws IOException {
        List<String> data = new ArrayList<>();
        SseParser.parse(new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)), data::add);
        return data;
    }

    @Test
    void singleEvent() throws IOException {
        List<String> data = parse("data: hello\n\n");
        assertEquals(List.of("hello"), data);
    }

    @Test
    void multipleEvents() throws IOException {
        List<String> data = parse("data: a\n\ndata: b\n\ndata: c\n\n");
        assertEquals(List.of("a", "b", "c"), data);
    }

    @Test
    void multiLineData() throws IOException {
        // 多 data 行拼接为一条（以换行分隔）
        List<String> data = parse("data: line1\ndata: line2\n\n");
        assertEquals(List.of("line1\nline2"), data);
    }

    @Test
    void doneSentinelStops() throws IOException {
        List<String> data = parse("data: a\n\ndata: [DONE]\n\ndata: b\n\n");
        assertEquals(List.of("a"), data, "[DONE] 后应停止解析");
    }

    @Test
    void ignoresOtherFields() throws IOException {
        List<String> data = parse("event: message\nid: 1\ndata: payload\nretry: 100\n\n");
        assertEquals(List.of("payload"), data);
    }

    @Test
    void dataWithLeadingSpaceTrimmed() throws IOException {
        // data: 后首个空格是分隔符应剥离；多个空格只剥第一个（SSE 规范）
        List<String> data = parse("data: hello\n\n");
        assertEquals(List.of("hello"), data, "data: 后首个空格应剥离");
        List<String> data2 = parse("data:  hello\n\n");
        assertEquals(List.of(" hello"), data2, "多个空格只剥第一个");
    }
}
