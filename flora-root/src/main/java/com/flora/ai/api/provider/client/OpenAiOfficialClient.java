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
import com.flora.ai.api.impl.SseParser;
import com.flora.ai.api.provider.QueueStreamIterator;
import com.flora.ai.api.provider.protocol.OpenAiProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI 官方客户端（多能力）：对话 + 流式 + JSON 模式。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例
 * （每能力一个对象）。JSON 模式支持 {@code json_object} 与 {@code json_schema}。</p>
 */
public final class OpenAiOfficialClient implements ChatClient, StreamingClient, JsonClient, ToolClient {

    private final Endpoint endpoint;
    private final HttpTransport http;

    public OpenAiOfficialClient(Endpoint endpoint, HttpTransport http) {
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
                queue.offer(new StreamEvent.Done("stop", null));
                return;
            }
            Map<String, Object> chunk = com.flora.codec.json.JsonParser.parseObject(data);
            var choices = JsonHelper.asList(chunk.get("choices"));
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) ((Map<?, ?>) choices.get(0)).get("delta");
                if (delta != null) {
                    String text = JsonHelper.str(delta.get("content"));
                    if (text != null && !text.isEmpty()) {
                        queue.offer(new StreamEvent.Text(text));
                    }
                    String thinking = JsonHelper.str(delta.get("reasoning_content"));
                    if (thinking != null && !thinking.isEmpty()) {
                        queue.offer(new StreamEvent.Thinking(thinking));
                    }
                    // 工具调用增量（分片推送：id/name/arguments 各为独立 delta）
                    for (Object tc : JsonHelper.asList(delta.get("tool_calls"))) {
                        Map<String, Object> call = JsonHelper.asMap(tc);
                        Map<String, Object> fn = JsonHelper.asMap(call.get("function"));
                        String rawArgs = JsonHelper.str(fn.get("arguments"));
                        queue.offer(new StreamEvent.ToolCallDelta(new ToolCall(
                                JsonHelper.str(call.get("id")),
                                JsonHelper.str(fn.get("name")),
                                rawArgs == null ? Map.of() : Map.of("_json", rawArgs)),
                                rawArgs));
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
