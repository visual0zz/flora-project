package com.flora.ai.provider.openai;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.RegisteredModel;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.codec.json.JsonBuilder;
import com.flora.ai.http.HttpTransport;
import com.flora.ai.http.SseParser;
import com.flora.ai.provider.JsonHelper;
import com.flora.ai.provider.QueueStreamIterator;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI Chat Completions 客户端：对话 + 流式 + JSON 模式。
 * <p>同时服务 {@code OPENAI_OFFICIAL}/{@code OPENAI_COMPATIBLE}/{@code DEEPSEEK_OFFICIAL}
 * 三类 OpenAI 兼容接口。</p>
 */
public final class OpenAiClient implements ChatClient, StreamingClient, JsonClient {

    private final RegisteredModel model;
    private final HttpTransport http;

    public OpenAiClient(RegisteredModel model, HttpTransport http) {
        this.model = model;
        this.http = http;
    }

    private String url() {
        return model.baseUrl() + "/v1/chat/completions";
    }

    private Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + (model.apiKey() == null ? "" : model.apiKey()));
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(), OpenAiProtocol.buildRequest(request, model.modelId()));
        return OpenAiProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(OpenAiProtocol.buildRequestMap(request, model.modelId(), true));
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
