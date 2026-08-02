package com.flora.ai.api;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link StreamEvent} 多态事件测试。
 */
class StreamEventTest {

    @Test
    void textEvent() {
        StreamEvent e = new StreamEvent.Text("Hello");
        assertInstanceOf(StreamEvent.Text.class, e);
        assertEquals("Hello", ((StreamEvent.Text) e).delta());
    }

    @Test
    void thinkingEvent() {
        StreamEvent e = new StreamEvent.Thinking("hmm");
        assertInstanceOf(StreamEvent.Thinking.class, e);
        assertEquals("hmm", ((StreamEvent.Thinking) e).delta());
    }

    @Test
    void toolCallDeltaEvent() {
        ToolCall call = ToolCall.of("call_1", "get_weather", Map.of());
        StreamEvent e = new StreamEvent.ToolCallDelta(call, "{\"city\":\"b");
        assertInstanceOf(StreamEvent.ToolCallDelta.class, e);
        assertEquals("call_1", ((StreamEvent.ToolCallDelta) e).call().id());
        assertEquals("{\"city\":\"b", ((StreamEvent.ToolCallDelta) e).rawArguments());
    }

    @Test
    void errorEvent() {
        StreamEvent e = new StreamEvent.Error("boom");
        assertInstanceOf(StreamEvent.Error.class, e);
        assertEquals("boom", ((StreamEvent.Error) e).message());
    }

    @Test
    void doneEventWithUsage() {
        TokenUsage usage = new TokenUsage(10, 5, 0, 0);
        StreamEvent e = new StreamEvent.Done("stop", usage);
        assertInstanceOf(StreamEvent.Done.class, e);
        assertEquals("stop", ((StreamEvent.Done) e).finishReason());
        assertEquals(5, ((StreamEvent.Done) e).usage().outputTokens());
    }

    @Test
    void sealedExhaustiveMatch() {
        // 验证 switch 模式匹配可穷尽所有子类型
        StreamEvent e = new StreamEvent.Text("x");
        String kind = switch (e) {
            case StreamEvent.Text t -> "text";
            case StreamEvent.Thinking t -> "thinking";
            case StreamEvent.ToolCallDelta t -> "tool";
            case StreamEvent.Error t -> "error";
            case StreamEvent.Done t -> "done";
        };
        assertEquals("text", kind);
    }

    @Test
    void collectTextOnlyJoinsText() {
        // 用 mock 迭代器验证 collectText 只拼接 Text
        var events = java.util.List.<StreamEvent>of(
                new StreamEvent.Text("mock-"),
                new StreamEvent.Thinking("thinking..."),
                new StreamEvent.Text("answer"),
                new StreamEvent.Done("stop", null));
        StreamIterator it = new StreamIterator() {
            private int pos;

            @Override
            public boolean hasNext() {
                return pos < events.size();
            }

            @Override
            public StreamEvent next() {
                return events.get(pos++);
            }
        };
        assertEquals("mock-answer", it.collectText());
    }
}
