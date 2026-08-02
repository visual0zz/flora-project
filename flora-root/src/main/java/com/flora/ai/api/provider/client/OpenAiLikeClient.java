package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.OpenAiProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI 风格兼容端点客户端：对话 + 流式 + JSON 模式。
 * <p>面向第三方 OpenAI 兼容接口（Together/Fireworks/vLLM/Ollama 等），
 * 复用 {@link OpenAiProtocol}。兼容端点通常仅支持 {@code json_object}。</p>
 */
public final class OpenAiLikeClient implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final HttpTransport http;

    public OpenAiLikeClient(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    private String url() {
        return endpoint.baseUrl() + "/v1/chat/completions";
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + (endpoint.apiKey() == null ? "" : endpoint.apiKey()));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(),
                OpenAiProtocol.buildRequest(request, endpoint.modelId()));
        return OpenAiProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(
                OpenAiProtocol.buildRequestMap(request, endpoint.modelId(), true));
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            Map<String, Object> chunk = com.flora.codec.json.JsonParser.parseObject(data);
            var choices = com.flora.ai.api.impl.JsonHelper.asList(chunk.get("choices"));
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("delta");
                if (delta != null) {
                    String text = com.flora.ai.api.impl.JsonHelper.str(delta.get("content"));
                    if (text != null && !text.isEmpty()) {
                        queue.offer(StreamEvent.text(text));
                    }
                }
            }
        });
        return new QueueStreamIterator(queue);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(OpenAiProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        return com.flora.codec.json.JsonParser.parseObject(OpenAiProtocol.parseResponse(json).text());
    }
}
