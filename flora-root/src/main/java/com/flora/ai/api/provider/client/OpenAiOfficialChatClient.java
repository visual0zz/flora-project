package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.OpenAiProtocol;

/**
 * OpenAI 官方对话客户端（一次性文本输出）。
 */
public final class OpenAiOfficialChatClient extends OpenAiClientSupport implements ChatClient {

    public OpenAiOfficialChatClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(chatUrl(), headers(),
                OpenAiProtocol.buildRequest(request, endpoint.modelId()));
        return OpenAiProtocol.parseResponse(json);
    }
}
