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
import com.flora.ai.api.provider.protocol.DeepSeekProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * DeepSeek 官方客户端（多能力）：对话 + 流式 + JSON 模式。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。
 * 使用独立 {@link DeepSeekProtocol}（JSON 仅 json_object、reasoner 拒绝工具调用）。</p>
 */
public final class DeepSeekOfficialClient implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final HttpTransport http;

    public DeepSeekOfficialClient(Endpoint endpoint, HttpTransport http) {
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
                DeepSeekProtocol.buildRequest(request, endpoint.modelId(), false));
        return DeepSeekProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = JsonBuilder.toJsonString(DeepSeekProtocol.buildRequestMap(request,
                endpoint.modelId(), true, null));
        BlockingQueue<StreamEvent> queue = new ArrayBlockingQueue<>(64);
        http.streamSse(url(), headers(), body, data -> {
            if (SseParser.DONE.equals(data)) {
                queue.offer(StreamEvent.done("stop"));
                return;
            }
            DeepSeekProtocol.Delta delta = DeepSeekProtocol.extractStreamDelta(data);
            if (delta == null) {
                return;
            }
            queue.offer(delta.thinking()
                    ? StreamEvent.thinking(delta.text()) : StreamEvent.text(delta.text()));
        });
        return new QueueStreamIterator(queue);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(DeepSeekProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        return com.flora.codec.json.JsonParser.parseObject(DeepSeekProtocol.parseResponse(json).text());
    }
}
