package com.flora.ai.provider.anthropic;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.provider.QueueStreamIterator;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.http.SseParser;
import com.flora.ai.spi.Endpoint;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Anthropic Messages 客户端：对话 + 流式（Extended Thinking 支持）。
 */
final class AnthropicClient implements ChatClient, StreamingClient {

    private final ModelSpec model;
    private final Endpoint endpoint;
    private final HttpTransport http;

    AnthropicClient(ModelSpec model, Endpoint endpoint, HttpTransport http) {
        this.model = model;
        this.endpoint = endpoint;
        this.http = http;
    }

    private String url() {
        return endpoint.baseUrl() + "/v1/messages";
    }

    private Map<String, String> headers() {
        Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("x-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
        h.put("anthropic-version", AnthropicProtocol.API_VERSION);
        return h;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(), AnthropicProtocol.buildRequest(request, false));
        return AnthropicProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = AnthropicProtocol.buildRequest(request, true);
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
