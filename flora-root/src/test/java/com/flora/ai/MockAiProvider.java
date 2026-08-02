package com.flora.ai;

import com.flora.ai.api.ApiSchema;
import com.flora.ai.api.Capability;
import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.TokenUsage;
import com.flora.ai.api.spi.AiProvider;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 测试用 mock AI 提供者：绑定 {@code OPENAI_LIKE} 协议标识，实现对话与流式能力。
 * 通过 {@code META-INF/services/com.flora.ai.api.spi.AiProvider} 注册，验证外部 SPI 附加加载。
 * <p>实现类为多能力单类，注册时每能力 new 一个实例。</p>
 */
public final class MockAiProvider implements AiProvider {

    @Override
    public ApiSchema apiSchema() {
        return ApiSchema.OPENAI_LIKE;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return EnumSet.of(Capability.CHAT, Capability.STREAM);
    }

    @Override
    public ChatClient createClient(Endpoint endpoint) {
        // 实现类为多能力单类；每端点一个实例
        return new MockClient(endpoint);
    }

    /** mock 客户端（多能力）：对话返回固定文本，流式返回固定事件序列。 */
    static final class MockClient implements ChatClient, StreamingClient {
        private final Endpoint endpoint;

        MockClient(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return new ChatResponse("mock-answer:" + endpoint.modelId(),
                    null, List.of(), new TokenUsage(1, 1, 0, 0), "stop", null);
        }

        @Override
        public StreamIterator stream(ChatRequest request) {
            Deque<StreamEvent> events = new ArrayDeque<>();
            events.add(new StreamEvent.Text("mock-"));
            events.add(new StreamEvent.Thinking("thinking..."));
            events.add(new StreamEvent.Text("answer"));
            events.add(new StreamEvent.Done("stop", null));
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
