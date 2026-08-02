package com.flora.ai.api.provider.client;

import com.flora.ai.api.impl.ChatClient;
import com.flora.ai.api.impl.ChatRequest;
import com.flora.ai.api.impl.ChatResponse;
import com.flora.ai.api.impl.JsonClient;
import com.flora.ai.api.impl.RegisteredModel;
import com.flora.ai.api.impl.StreamEvent;
import com.flora.ai.api.impl.StreamIterator;
import com.flora.ai.api.impl.StreamingClient;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.protocal.GeminiProtocol;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Gemini 客户端：对话 + 流式 + JSON 模式。
 * <p>流式使用 {@code :streamGenerateContent?alt=sse} 端点。</p>
 */
final class GeminiClient implements ChatClient, StreamingClient, JsonClient {

    private final RegisteredModel model;
    private final HttpTransport http;

    GeminiClient(RegisteredModel model, HttpTransport http) {
        this.model = model;
        this.http = http;
    }

    private String url(boolean stream) {
        String base = model.baseUrl() + "/v1beta/models/" + model.modelId();
        return stream ? base + ":streamGenerateContent?alt=sse" : base + ":generateContent";
    }

    private Map<String, String> headers() {
        return Map.of("x-goog-api-key", model.apiKey() == null ? "" : model.apiKey());
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
