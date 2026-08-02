package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.OpenAiProtocol;
import com.flora.codec.json.JsonBuilder;

import java.util.Map;

/**
 * OpenAI 官方 JSON 结构化输出客户端。
 */
public final class OpenAiOfficialJsonClient extends OpenAiClientSupport implements JsonClient {

    public OpenAiOfficialJsonClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        String body = JsonBuilder.toJsonString(OpenAiProtocol.buildRequestMap(request,
                endpoint.modelId(), false, Map.of("type", "json_object")));
        String json = http.postJson(chatUrl(), headers(), body);
        return com.flora.codec.json.JsonParser.parseObject(OpenAiProtocol.parseResponse(json).text());
    }
}
