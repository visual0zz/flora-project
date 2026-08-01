package com.flora.ai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.TokenUsage;
import com.flora.ai.spi.AiProvider;
import com.flora.ai.spi.Endpoint;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 测试用 mock AI 提供者：支持 "mock" 厂商，实现对话与流式能力。
 * 通过 {@code META-INF/services/com.flora.ai.spi.AiProvider} 注册，验证 ServiceLoader 加载。
 */
public final class MockAiProvider implements AiProvider {

    @Override
    public boolean supports(ModelSpec model) {
        return "mock".equals(model.provider());
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public ChatClient createClient(ModelSpec model, Endpoint endpoint) {
        return new MockClient(model, endpoint);
    }

    /** mock 客户端：对话返回固定文本，流式返回固定事件序列。 */
    static final class MockClient implements ChatClient, StreamingClient {
        private final ModelSpec model;
        private final Endpoint endpoint;

        MockClient(ModelSpec model, Endpoint endpoint) {
            this.model = model;
            this.endpoint = endpoint;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return new ChatResponse("mock-answer:" + model.id(),
                    null, new TokenUsage(1, 1, 0, 0), "stop", null);
        }

        @Override
        public StreamIterator stream(ChatRequest request) {
            Deque<StreamEvent> events = new ArrayDeque<>();
            events.add(StreamEvent.text("mock-"));
            events.add(StreamEvent.thinking("thinking..."));
            events.add(StreamEvent.text("answer"));
            events.add(StreamEvent.done("stop"));
            return new StreamIterator() {
                @Override
                public boolean hasNext() {
                    return !events.isEmpty();
                }

                @Override
                public StreamEvent next() {
                    return events.poll();
                }
            };
        }
    }
}
