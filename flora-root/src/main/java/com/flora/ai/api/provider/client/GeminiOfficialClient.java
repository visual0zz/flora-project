package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.protocol.GeminiProtocol;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Gemini 客户端：对话 + 流式 + JSON 模式。
 * <p>流式使用 {@code :streamGenerateContent?alt=sse} 端点。</p>
 */
public final class GeminiOfficialClient implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final HttpTransport http;

    public GeminiOfficialClient(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    private String url(boolean stream) {
        String base = endpoint.baseUrl() + "/v1beta/models/" + endpoint.modelId();
        return stream ? base + ":streamGenerateContent?alt=sse" : base + ":generateContent";
    }

    private Map<String, String> headers() {
        return Map.of("x-goog-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(false), headers(), GeminiProtocol.buildRequest(request));
        return GeminiProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = GeminiProtocol.buildRequest(request);
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(true), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            String delta = GeminiProtocol.extractStreamDelta(data);
            if (delta != null && !delta.isEmpty()) {
                queue.offer(StreamEvent.text(delta));
            }
        });
        return new QueueStreamIterator(queue);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        ChatResponse resp = chat(request);
        return com.flora.codec.json.JsonParser.parseObject(resp.text());
    }
}
