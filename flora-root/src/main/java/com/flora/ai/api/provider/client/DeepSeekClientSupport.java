package com.flora.ai.api.provider.client;

import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;

import java.util.Map;

/**
 * DeepSeek 单能力 client 的共享传输基座。
 */
abstract class DeepSeekClientSupport {

    protected final Endpoint endpoint;
    protected final HttpTransport http;

    protected DeepSeekClientSupport(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    protected String url() {
        return endpoint.baseUrl() + "/v1/chat/completions";
    }

    protected Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + (endpoint.apiKey() == null ? "" : endpoint.apiKey()));
    }
}
