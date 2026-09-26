package com.flora.root.ai.api.provider.client;

import com.flora.root.ai.api.Capability;
import com.flora.root.ai.api.ChatClient;
import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.ChatResponse;
import com.flora.root.ai.api.Endpoint;
import com.flora.root.ai.api.JsonClient;
import com.flora.root.ai.api.StreamEvent;
import com.flora.root.ai.api.StreamIterator;
import com.flora.root.ai.api.StreamingClient;
import com.flora.root.ai.api.ToolCall;
import com.flora.root.ai.api.impl.HttpTransport;
import com.flora.root.ai.api.impl.JsonHelper;
import com.flora.root.codec.json.JsonParser;
import com.flora.root.ai.api.impl.SseParser;
import com.flora.root.ai.api.provider.protocol.GeminiProtocol;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Gemini 官方客户端（多能力）：对话 + 流式 + JSON 模式 + 工具调用。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。
 * 流式使用 {@code :streamGenerateContent?alt=sse} 端点。</p>
 */
public final class GeminiOfficialClient extends AbstractStreamingClient
        implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;

    public GeminiOfficialClient(Endpoint endpoint, HttpTransport http) {
        super(http);
        this.endpoint = endpoint;
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.STREAMING, Capability.JSON_MODE,
                Capability.TOOL_USE, Capability.MULTIMODAL, Capability.THINKING);
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
        return startStream(url(true), headers(), body);
    }

    /** 处理一个 SSE data 块；[DONE] 哨兵忽略，Done 由生产者统一推送。 */
    @Override
    protected void handleData(String data, BlockingQueue<StreamEvent> queue)
            throws InterruptedException {
        if (SseParser.DONE.equals(data)) {
            return;
        }
        Map<String, Object> root = JsonParser.parseObject(data).toMap();
        for (Object c : JsonHelper.asList(root.get("candidates"))) {
            Map<String, Object> content = JsonHelper.asMap(JsonHelper.asMap(c).get("content"));
            for (Object p : JsonHelper.asList(content.get("parts"))) {
                Map<String, Object> part = JsonHelper.asMap(p);
                if (part.containsKey("functionCall")) {
                    Map<String, Object> fc = JsonHelper.asMap(part.get("functionCall"));
                    queue.put(new StreamEvent.ToolCallCompleted(
                            new ToolCall(null, JsonHelper.str(fc.get("name")),
                                    JsonHelper.asMap(fc.get("args"))), null));
                } else {
                    String text = JsonHelper.str(part.get("text"));
                    if (text != null && !text.isEmpty()) {
                        queue.put(new StreamEvent.Text(text));
                    }
                }
            }
        }
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = GeminiProtocol.buildRequest(request, Map.of("type", "json_object"));
        String json = http.postJson(url(false), headers(), body);
        return JsonParser.parseObject(GeminiProtocol.parseResponse(json).text()).toMap();
    }
}
