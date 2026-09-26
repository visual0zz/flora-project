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
import com.flora.root.ai.api.provider.protocol.AnthropicProtocol;
import com.flora.root.codec.json.JsonBuilder;
import com.flora.root.codec.json.JsonParser;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;

/**
 * Anthropic 官方客户端（多能力）：对话 + 流式 + JSON 模式 + 工具调用。
 * <p>实现类为多能力单类，注册时按 endpoint 声明的 capabilities 创建多个实例。</p>
 */
public final class AnthropicOfficialClient extends AbstractStreamingClient
        implements ChatClient, StreamingClient, JsonClient {

    private final Endpoint endpoint;
    private final String[] curTool = {null, null};   // [id, name]
    private final StringBuilder curJson = new StringBuilder();

    public AnthropicOfficialClient(Endpoint endpoint, HttpTransport http) {
        super(http);
        this.endpoint = endpoint;
    }

    @Override
    public Set<Capability> capabilities() {
        // 协议层实际已支持 image 块（见 AnthropicProtocol 的 content 块构建）
        return EnumSet.of(Capability.STREAMING, Capability.JSON_MODE,
                Capability.TOOL_USE, Capability.MULTIMODAL);
    }

    private String url() {
        return endpoint.baseUrl() + "/v1/messages";
    }

    private Map<String, String> headers() {
        Map<String, String> h = new java.util.LinkedHashMap<>();
        h.put("x-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
        h.put("anthropic-version", AnthropicProtocol.API_VERSION);
        return h;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(),
                AnthropicProtocol.buildRequest(request, endpoint.modelId(), false));
        return AnthropicProtocol.parseResponse(json);
    }

    @Override
    public StreamIterator stream(ChatRequest request) {
        String body = AnthropicProtocol.buildRequest(request, endpoint.modelId(), true);
        return startStream(url(), headers(), body);
    }

    /** 处理一个 SSE data 块；Anthropic 无 [DONE] 哨兵，流自然结束由生产者统一推送 Done。 */
    @Override
    protected void handleData(String data, BlockingQueue<StreamEvent> queue)
            throws InterruptedException {
        Map<String, Object> event = JsonParser.parseObject(data).toMap();
        String type = JsonHelper.str(event.get("type"));
        switch (type) {
            case "content_block_start" -> {
                Map<String, Object> block = JsonHelper.asMap(event.get("content_block"));
                if ("tool_use".equals(JsonHelper.str(block.get("type")))) {
                    curTool[0] = JsonHelper.str(block.get("id"));
                    curTool[1] = JsonHelper.str(block.get("name"));
                    curJson.setLength(0);
                }
            }
            case "content_block_delta" -> {
                Map<String, Object> delta = JsonHelper.asMap(event.get("delta"));
                String dt = JsonHelper.str(delta.get("type"));
                if ("text_delta".equals(dt)) {
                    queue.put(new StreamEvent.Text(JsonHelper.str(delta.get("text"))));
                } else if ("thinking_delta".equals(dt)) {
                    queue.put(new StreamEvent.Thinking(JsonHelper.str(delta.get("thinking"))));
                } else if ("input_json_delta".equals(dt)) {
                    String pj = JsonHelper.str(delta.get("partial_json"));
                    if (pj != null) {
                        curJson.append(pj);
                    }
                }
            }
            case "content_block_stop" -> {
                if (curTool[0] != null) {
                    String raw = curJson.toString();
                    Map<String, Object> parsed = Map.of();
                    if (raw != null && !raw.isBlank()) {
                        try {
                            Object v = JsonParser.parse(raw).toMap();
                            if (v instanceof Map<?, ?> m) {
                                parsed = (Map<String, Object>) m;
                            }
                        } catch (IllegalStateException ignored) {
                            // 非完整 JSON：保留空 Map，原始串由 rawArguments 带出
                        }
                    }
                    queue.put(new StreamEvent.ToolCallCompleted(
                            new ToolCall(curTool[0], curTool[1], parsed), raw));
                    curTool[0] = curTool[1] = null;
                    curJson.setLength(0);
                }
            }
            default -> { /* message_start / message_delta / ping 等忽略 */ }
        }
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(
                AnthropicProtocol.buildRequestMap(request, endpoint.modelId(), false,
                        Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        ChatResponse resp = AnthropicProtocol.parseResponse(json);
        // 强制工具：结构化结果在首个 json_output 工具调用的 input 中
        for (ToolCall tc : resp.toolCalls()) {
            if (AnthropicProtocol.FORCED_TOOL_NAME.equals(tc.name())) {
                return tc.arguments();
            }
        }
        // 退化：直接解析文本
        Object parsed = JsonParser.parse(resp.text()).toMap();
        return parsed instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}
