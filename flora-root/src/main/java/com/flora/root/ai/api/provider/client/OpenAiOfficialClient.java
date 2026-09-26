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
import com.flora.root.ai.api.impl.HttpTransport;
import com.flora.root.ai.api.impl.JsonHelper;
import com.flora.root.ai.api.impl.SseParser;
import com.flora.root.ai.api.provider.protocol.OpenAiProtocol;
import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * OpenAI 官方客户端（多能力）：对话 + 流式 + JSON 模式。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例
 * （每能力一个对象）。JSON 模式支持 {@code json_object} 与 {@code json_schema}。</p>
 */
public final class OpenAiOfficialClient extends AbstractStreamingClient
        implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final OpenAiToolCallAggregator aggregator = new OpenAiToolCallAggregator();

    public OpenAiOfficialClient(Endpoint endpoint, HttpTransport http) {
        super(http);
        this.endpoint = endpoint;
    }

    @Override
    public Set<Capability> capabilities() {
        // 协议层实际已支持 reasoning_effort / reasoning_content（见 OpenAiProtocol）
        return EnumSet.of(Capability.STREAMING, Capability.JSON_MODE,
                Capability.TOOL_USE, Capability.MULTIMODAL, Capability.THINKING);
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
        return startStream(url(), headers(), body);
    }

    /** 处理一个 SSE data 块；[DONE] 哨兵只做工具调用收尾，Done 由生产者统一推送。 */
    @Override
    protected void handleData(String data, BlockingQueue<StreamEvent> queue)
            throws InterruptedException {
        if (SseParser.DONE.equals(data)) {
            emitToolCalls(queue, aggregator);
            return;
        }
        Map<String, Object> chunk = JsonParser.parseObject(data).toMap();
        var choices = JsonHelper.asList(chunk.get("choices"));
        if (choices.isEmpty()) {
            return;
        }
        Map<String, Object> choice = JsonHelper.asMap(choices.get(0));
        Map<String, Object> delta = JsonHelper.asMap(choice.get("delta"));
        String text = JsonHelper.str(delta.get("content"));
        if (text != null && !text.isEmpty()) {
            queue.put(new StreamEvent.Text(text));
        }
        String thinking = JsonHelper.str(delta.get("reasoning_content"));
        if (thinking != null && !thinking.isEmpty()) {
            queue.put(new StreamEvent.Thinking(thinking));
        }
        // 工具调用碎片：按 index 归组（id/name 仅首片出现）
        for (Object tc : JsonHelper.asList(delta.get("tool_calls"))) {
            Map<String, Object> call = JsonHelper.asMap(tc);
            int index = JsonHelper.intOf(call.get("index"));
            Map<String, Object> fn = JsonHelper.asMap(call.get("function"));
            aggregator.add(index, JsonHelper.str(call.get("id")),
                    JsonHelper.str(fn.get("name")), JsonHelper.str(fn.get("arguments")));
        }
        // 协议层完成信号：finish_reason="tool_calls" 表示本批调用结束
        if ("tool_calls".equals(JsonHelper.str(choice.get("finish_reason")))) {
            emitToolCalls(queue, aggregator);
        }
    }

    /** 把聚合器里已攒齐的调用作为完整事件发出。 */
    private void emitToolCalls(BlockingQueue<StreamEvent> queue,
                               OpenAiToolCallAggregator aggregator) throws InterruptedException {
        for (StreamEvent.ToolCallCompleted tc : aggregator.flush()) {
            queue.put(tc);
        }
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(OpenAiProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        return JsonParser.parseObject(OpenAiProtocol.parseResponse(json).text()).toMap();
    }
}
