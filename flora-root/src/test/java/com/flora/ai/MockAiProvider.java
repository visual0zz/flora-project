package com.flora.ai;

import com.flora.ai.api.ApiKind;
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
 */
public final class MockAiProvider implements AiProvider {

    @Override
    public ApiKind apiKind() {
        return ApiKind.OPENAI_LIKE;
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
    @SuppressWarnings("unchecked")
    public <T> T createClient(Endpoint endpoint, Capability capability) {
        return (T) switch (capability) {
            case CHAT -> new MockChatClient(endpoint);
            case STREAM -> new MockStreamClient(endpoint);
            default -> throw new IllegalArgumentException("mock 不支持能力: " + capability);
        };
    }

    /** mock 对话客户端：返回固定文本。 */
    static final class MockChatClient implements ChatClient {
        private final Endpoint endpoint;

        MockChatClient(Endpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public ChatResponse chat(ChatRequest request) {
            return new ChatResponse("mock-answer:" + endpoint.modelId(),
                    null, List.of(), new TokenUsage(1, 1, 0, 0), "stop", null);
        }
    }

    /** mock 流式客户端：返回固定事件序列。 */
    static final class MockStreamClient implements StreamingClient {
        private final Endpoint endpoint;

        MockStreamClient(Endpoint endpoint) {
            this.endpoint = endpoint;
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
