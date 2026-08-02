package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.DeepSeekProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;

/**
 * DeepSeek JSON 结构化输出客户端（仅 json_object）。
 */
public final class DeepSeekOfficialJsonClient extends DeepSeekClientSupport implements JsonClient {

    public DeepSeekOfficialJsonClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(DeepSeekProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(url(), headers(), body);
        return com.flora.codec.json.JsonParser.parseObject(DeepSeekProtocol.parseResponse(json).text());
    }
}
