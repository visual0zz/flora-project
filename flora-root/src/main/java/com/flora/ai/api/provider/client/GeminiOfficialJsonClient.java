package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.JsonClient;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.GeminiProtocol;

import java.util.Map;

/**
 * Gemini JSON 结构化输出客户端。
 */
public final class GeminiOfficialJsonClient extends GeminiClientSupport implements JsonClient {

    public GeminiOfficialJsonClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public Map<String, Object> chatJson(ChatRequest request) {
        ChatResponse resp = chat(request);
        return com.flora.codec.json.JsonParser.parseObject(resp.text());
    }

    private ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(false), headers(), GeminiProtocol.buildRequest(request));
        return GeminiProtocol.parseResponse(json);
    }
}
