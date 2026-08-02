package com.flora.ai.api.provider.client;

import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;
import com.flora.ai.api.provider.protocol.AnthropicProtocol;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Anthropic 单能力 client 的共享传输基座。
 */
abstract class AnthropicClientSupport {

    protected final Endpoint endpoint;
    protected final HttpTransport http;

    protected AnthropicClientSupport(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    protected String url() {
        return endpoint.baseUrl() + "/v1/messages";
    }

    protected Map<String, String> headers() {
        Map<String, String> h = new LinkedHashMap<>();
        h.put("x-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
        h.put("anthropic-version", AnthropicProtocol.API_VERSION);
        return h;
    }
}
