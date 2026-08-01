package com.flora.ai.provider.openai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.ModelSpec;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.codec.json.JsonBuilder;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.http.SseParser;
import com.flora.ai.provider.JsonHelper;
import com.flora.ai.provider.QueueStreamIterator;
import com.flora.ai.spi.Endpoint;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI Chat Completions 客户端：对话 + 流式 + JSON 模式。
 */
final class OpenAiClient implements ChatClient, StreamingClient, JsonClient {

    private final ModelSpec model;
    private final Endpoint endpoint;
    private final HttpTransport http;

    OpenAiClient(ModelSpec model, Endpoint endpoint, HttpTransport http) {
        this.model = model;
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
        String json = http.postJson(url(), headers(), OpenAiProtocol.buildRequest(request));
        return OpenAiProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(OpenAiProtocol.buildRequestMap(request, true));
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            // 解析 delta
            Map<String, Object> chunk = com.flora.codec.json.JsonParser.parseObject(data);
            var choices = JsonHelper.asList(chunk.get("choices"));
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("delta");
                if (delta != null) {
                    String text = JsonHelper.str(delta.get("content"));
                    if (text != null && !text.isEmpty()) {
                        queue.offer(StreamEvent.text(text));
                    }
                    String thinking = JsonHelper.str(delta.get("reasoning_content"));
                    if (thinking != null && !thinking.isEmpty()) {
                        queue.offer(StreamEvent.thinking(thinking));
                    }
                }
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
