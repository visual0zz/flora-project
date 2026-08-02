package com.flora.ai.api.provider.client;

import com.flora.ai.api.Endpoint;
import com.flora.ai.api.impl.HttpTransport;

import java.util.Map;

/**
 * OpenAI 系列（官方/兼容）单能力 client 的共享传输基座。
 * <p>持有 {@link Endpoint} 与 {@link HttpTransport}，提供 URL 与认证头。
 * 各单能力 client（Chat/Stream/Json）继承并复用。</p>
 */
abstract class OpenAiClientSupport {

    protected final Endpoint endpoint;
    protected final HttpTransport http;

    protected OpenAiClientSupport(Endpoint endpoint, HttpTransport http) {
        this.endpoint = endpoint;
        this.http = http;
    }

    protected String chatUrl() {
        return endpoint.baseUrl() + "/v1/chat/completions";
    }

    protected Map<String, String> headers() {
        return Map.of("Authorization", "Bearer " + (endpoint.apiKey() == null ? "" : endpoint.apiKey()));
    }
}
