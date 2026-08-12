package com.flora.root.ai.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolSpec / ToolCall / 工具消息 测试。
 */
class ToolTest {

    private static final ToolSpec WEATHER = ToolSpec.of("get_weather", "查询天气",
            Map.of("type", "object", "properties", Map.of("city", Map.of("type", "string"))));

    @Test
    void toolSpecBuilds() {
        assertEquals("get_weather", WEATHER.name());
        assertEquals("查询天气", WEATHER.description());
        assertEquals("object", WEATHER.parameters().get("type"));
    }

    @Test
    void toolSpecNullParametersDefaultsToEmpty() {
        ToolSpec t = ToolSpec.of("f", "d", null);
        assertTrue(t.parameters().isEmpty());
    }

    @Test
    void toolCallBuilds() {
        ToolCall c = ToolCall.of("call_1", "get_weather", Map.of("city", "beijing"));
        assertEquals("call_1", c.id());
        assertEquals("get_weather", c.name());
        assertEquals("beijing", c.arguments().get("city"));
    }

    @Test
    void toolResultMessage() {
        Message m = Message.toolResult("call_1", "sunny");
        assertEquals(Message.Role.TOOL, m.role());
        assertEquals("call_1", m.toolCallId());
        assertTrue(m.toolCalls().isEmpty());
    }

    @Test
    void assistantWithCallsMessage() {
        Message m = Message.assistantWithCalls(
                List.of(ToolCall.of("call_1", "get_weather", Map.of())), "I'll check");
        assertEquals(Message.Role.ASSISTANT, m.role());
        assertEquals(1, m.toolCalls().size());
        assertEquals("get_weather", m.toolCalls().get(0).name());
    }

    @Test
    void chatRequestWithTools() {
        ChatRequest req = ChatRequest.builder()
                .message(Message.of(Message.Role.USER, "北京天气"))
                .tool(WEATHER)
                .build();
        assertEquals(1, req.tools().size());
        assertEquals("get_weather", req.tools().get(0).name());
    }
}
