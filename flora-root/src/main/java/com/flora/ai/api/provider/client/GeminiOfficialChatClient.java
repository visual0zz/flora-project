package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.GeminiProtocol;

/**
 * Gemini 对话客户端（一次性文本输出）。
 */
public final class GeminiOfficialChatClient extends GeminiClientSupport implements ChatClient {

    public GeminiOfficialChatClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(false), headers(), GeminiProtocol.buildRequest(request));
        return GeminiProtocol.parseResponse(json);
    }
}
