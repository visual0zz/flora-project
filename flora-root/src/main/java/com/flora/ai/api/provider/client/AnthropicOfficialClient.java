package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.AnthropicProtocol;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Anthropic 官方客户端（多能力）：对话 + 流式（Extended Thinking 支持）。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。</p>
 */
public final class AnthropicOfficialClient implements ChatClient, StreamingClient {

    private final Endpoint endpoint;
    private final HttpTransport http;

    public AnthropicOfficialClient(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    private String url() {
        return endpoint.baseUrl() + "/v1/messages";
    }

    private Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
        h.put("anthropic-version", AnthropicProtocol.API_VERSION);
        return h;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(),
                AnthropicProtocol.buildRequest(request, endpoint.modelId(), false));
        return AnthropicProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = AnthropicProtocol.buildRequest(request, endpoint.modelId(), true);
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            AnthropicProtocol.Delta delta = AnthropicProtocol.extractStreamDelta(data);
            if (delta == null) {
                return;
            }
            queue.offer(delta.thinking()
                    ? StreamEvent.thinking(delta.text()) : StreamEvent.text(delta.text()));
        });
        return new QueueStreamIterator(queue);
    }
}
