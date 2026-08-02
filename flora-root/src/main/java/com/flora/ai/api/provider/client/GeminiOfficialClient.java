package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.StreamEvent;
import com.flora.ai.api.StreamIterator;
import com.flora.ai.api.StreamingClient;
import com.flora.ai.api.ToolCall;
import com.flora.ai.api.ToolClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.impl.JsonHelper;
import com.flora.codec.json.JsonParser;
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.GeminiProtocol;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Gemini 官方客户端（多能力）：对话 + 流式 + JSON 模式 + 工具调用。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。
 * 流式使用 {@code :streamGenerateContent?alt=sse} 端点。</p>
 */
public final class GeminiOfficialClient implements ChatClient, StreamingClient, JsonClient, ToolClient {

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
                queue.offer(new StreamEvent.Done("stop", null));
                return;
            }
            Map<String, Object> root = JsonParser.parseObject(data);
            for (Object c : JsonHelper.asList(root.get("candidates"))) {
                Map<String, Object> content = JsonHelper.asMap(JsonHelper.asMap(c).get("content"));
                for (Object p : JsonHelper.asList(content.get("parts"))) {
                    Map<String, Object> part = JsonHelper.asMap(p);
                    if (part.containsKey("functionCall")) {
                        Map<String, Object> fc = JsonHelper.asMap(part.get("functionCall"));
                        queue.offer(new StreamEvent.ToolCallDelta(
                                new ToolCall(null, JsonHelper.str(fc.get("name")),
                                        JsonHelper.asMap(fc.get("args"))), null));
                    } else {
                        String text = JsonHelper.str(part.get("text"));
                        if (text != null && !text.isEmpty()) {
                            queue.offer(new StreamEvent.Text(text));
                        }
                    }
                }
            }
        });
        return new QueueStreamIterator(queue);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = GeminiProtocol.buildRequest(request, Map.of("type", "json_object"));
        String json = http.postJson(url(false), headers(), body);
        return JsonParser.parseObject(GeminiProtocol.parseResponse(json).text());
    }
}
