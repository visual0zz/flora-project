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
import com.flora.root.ai.api.impl.SseParser;
import com.flora.root.ai.api.provider.protocol.DeepSeekProtocol;
import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * DeepSeek 官方客户端（多能力）：对话 + 流式 + JSON 模式。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。
 * 使用独立 {@link DeepSeekProtocol}（JSON 仅 json_object、reasoner 拒绝工具调用）。</p>
 */
public final class DeepSeekOfficialClient extends AbstractStreamingClient
        implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final OpenAiToolCallAggregator aggregator = new OpenAiToolCallAggregator();

    public DeepSeekOfficialClient(Endpoint endpoint, HttpTransport http) {
        super(http);
        this.endpoint = endpoint;
    }

    @Override
    public Set<Capability> capabilities() {
        return EnumSet.of(Capability.STREAMING, Capability.JSON_MODE,
                Capability.TOOL_USE, Capability.THINKING);
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
        return startStream(url(), headers(), body);
    }

    /** 处理一个 SSE data 块：[DONE] 哨兵只做工具调用收尾，Done 由生产者统一推送。 */
    @Override
    protected void handleData(String data, BlockingQueue<StreamEvent> queue)
            throws InterruptedException {
        if (SseParser.DONE.equals(data)) {
            emitToolCalls(queue);
            return;
        }
        DeepSeekProtocol.Delta delta = DeepSeekProtocol.extractStreamDelta(data);
        if (delta != null) {
            queue.put(delta.thinking()
                    ? new StreamEvent.Thinking(delta.text())
                    : new StreamEvent.Text(delta.text()));
        }
        // DeepSeek 为 OpenAI 兼容格式，工具调用分片结构与 OpenAI 一致
        for (DeepSeekProtocol.ToolCallFragment f : DeepSeekProtocol.extractStreamToolCalls(data)) {
            aggregator.add(f.index(), f.id(), f.name(), f.arguments());
        }
    }

    /** 把聚合器里已攒齐的工具调用作为完整事件发出。 */
    private void emitToolCalls(BlockingQueue<StreamEvent> queue) throws InterruptedException {
        for (StreamEvent.ToolCallCompleted tc : aggregator.flush()) {
            queue.put(tc);
        }
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(DeepSeekProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        return JsonParser.parseObject(DeepSeekProtocol.parseResponse(json).text()).toMap();
    }
}
