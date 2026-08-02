package com.flora.ai.api.provider.client;

import com.flora.ai.api.StreamEvent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link OpenAiToolCallAggregator} 聚合测试：验证 OpenAI 流式 tool_calls 碎片
 * 按 index 正确归组，攒齐后 flush 出完整调用（并行调用交错场景）。
 */
class OpenAiToolCallAggregatorTest {

    @Test
    void flushEmptyReturnsNothing() {
        OpenAiToolCallAggregator agg = new OpenAiToolCallAggregator();
        assertTrue(agg.isEmpty());
        assertTrue(agg.flush().isEmpty());
    }

    @Test
    void singleCallFragmentsAssemble() {
        OpenAiToolCallAggregator agg = new OpenAiToolCallAggregator();
        // 模拟一个调用：首片带 id/name，后续片只带 arguments 碎片
        agg.add(0, "call_1", "get_weather", "{\"city\":");
        agg.add(0, null, null, "\"beijing\"}");
        List<StreamEvent.ToolCallCompleted> out = agg.flush();
        assertEquals(1, out.size());
        StreamEvent.ToolCallCompleted e = out.get(0);
        assertEquals("call_1", e.call().id());
        assertEquals("get_weather", e.call().name());
        assertEquals("beijing", e.call().arguments().get("city"));
        assertEquals("{\"city\":\"beijing\"}", e.rawArguments());
        assertTrue(agg.isEmpty(), "flush 后应清空状态");
    }

    @Test
    void parallelCallsInterleavedFragments() {
        OpenAiToolCallAggregator agg = new OpenAiToolCallAggregator();
        // 两个并行调用，index 0/1 交错到达（OpenAI 并行场景的真实形态）
        agg.add(0, "call_a", "get_weather", "{\"city\":");
        agg.add(1, "call_b", "get_news", "{\"topic\":");
        agg.add(0, null, null, "\"beijing\"}");
        agg.add(1, null, null, "\"ai\"}");
        List<StreamEvent.ToolCallCompleted> out = agg.flush();
        assertEquals(2, out.size());
        StreamEvent.ToolCallCompleted a = out.get(0);
        assertEquals("call_a", a.call().id());
        assertEquals("beijing", a.call().arguments().get("city"));
        StreamEvent.ToolCallCompleted b = out.get(1);
        assertEquals("call_b", b.call().id());
        assertEquals("ai", b.call().arguments().get("topic"));
    }

    @Test
    void unparseableArgumentsKeepsRaw() {
        OpenAiToolCallAggregator agg = new OpenAiToolCallAggregator();
        agg.add(0, "call_x", "frag", "{\"broken\"");
        List<StreamEvent.ToolCallCompleted> out = agg.flush();
        assertEquals(1, out.size());
        StreamEvent.ToolCallCompleted e = out.get(0);
        assertEquals("{\"broken\"", e.rawArguments());
        assertTrue(e.call().arguments().isEmpty(), "无法解析时 arguments 应为空 Map");
    }

    @Test
    void emptyArgumentsYieldsEmptyMap() {
        OpenAiToolCallAggregator agg = new OpenAiToolCallAggregator();
        agg.add(0, "call_1", "noop", "");
        agg.add(0, null, null, null);
        List<StreamEvent.ToolCallCompleted> out = agg.flush();
        assertEquals(1, out.size());
        assertTrue(out.get(0).call().arguments().isEmpty());
    }
}
