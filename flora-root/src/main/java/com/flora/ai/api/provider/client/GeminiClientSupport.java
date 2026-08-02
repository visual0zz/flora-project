package com.flora.ai.api.provider.client;

import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;

import java.util.Map;

/**
 * Gemini 单能力 client 的共享传输基座。
 */
abstract class GeminiClientSupport {

    protected final Endpoint endpoint;
    protected final HttpTransport http;

    protected GeminiClientSupport(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    protected String url(boolean stream) {
        String base = endpoint.baseUrl() + "/v1beta/models/" + endpoint.modelId();
        return stream ? base + ":streamGenerateContent?alt=sse" : base + ":generateContent";
    }

    protected Map<String, String> headers() {
        return Map.of("x-goog-api-key", endpoint.apiKey() == null ? "" : endpoint.apiKey());
    }
}
