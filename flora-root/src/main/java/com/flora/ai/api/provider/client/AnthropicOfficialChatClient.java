package com.flora.ai.api.provider.client;

import com.flora.ai.api.ChatClient;
import com.flora.ai.api.ChatRequest;
import com.flora.ai.api.ChatResponse;
import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.AnthropicProtocol;

/**
 * Anthropic 对话客户端（一次性文本输出，支持 Extended Thinking）。
 */
public final class AnthropicOfficialChatClient extends AnthropicClientSupport implements ChatClient {

    public AnthropicOfficialChatClient(Endpoint endpoint, HttpTransport http) {
        super(endpoint, http);
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String json = http.postJson(url(), headers(),
                AnthropicProtocol.buildRequest(request, endpoint.modelId(), false));
        return AnthropicProtocol.parseResponse(json);
    }
}
