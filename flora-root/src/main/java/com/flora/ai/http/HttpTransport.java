package com.flora.ai.http;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 轻量 HTTP 传输层：封装 JDK {@code HttpClient}。
 * <p>提供 JSON POST（返回字符串）与 SSE 流式 POST（事件回调）。
 * 仅 JDK，零外部依赖。</p>
 *
 * <pre>{@code
 * HttpTransport t = HttpTransport.create();
 * String json = t.postJson(url, headers, body);
 * t.streamSse(url, headers, body, data -> { ... });
 * }</pre>
 */
public final class HttpTransport {

    private final HttpClient client;

    private HttpTransport(HttpClient client) {
        this.client = client;
    }

    /** 创建默认传输层。 */
    public static HttpTransport create() {
        return new HttpTransport(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build());
    }

    /** 创建自定义传输层（注入配置好的 HttpClient）。 */
    public static HttpTransport of(HttpClient client) {
        return new HttpTransport(client);
    }

    /** POST JSON 请求，返回响应体字符串。 */
    public String postJson(String url, Map<String, String> headers, String jsonBody) {
        HttpRequest request = buildRequest(url, headers, jsonBody);
        try {
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            if (status < 200 || status >= 300) {
                throw new HttpStatusException(status, resp.body());
            }
            return resp.body();
        } catch (java.io.IOException e) {
            throw new HttpTransportException("HTTP 请求失败: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpTransportException("HTTP 请求被中断: " + url, e);
        }
    }

    /** POST JSON 请求并流式处理 SSE 响应（data 行逐条回调）。 */
    public void streamSse(String url, Map<String, String> headers, String jsonBody,
                          Consumer<String> onData) {
        HttpRequest request = buildRequest(url, headers, jsonBody);
        HttpResponse<java.io.InputStream> resp;
        try {
            resp = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (java.io.IOException e) {
            throw new HttpTransportException("SSE 请求失败: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HttpTransportException("SSE 请求被中断: " + url, e);
        }
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new HttpStatusException(status, "SSE 请求失败，状态 " + status);
        }
        try (var in = resp.body()) {
            SseParser.parse(in, onData);
        } catch (java.io.IOException e) {
            throw new HttpTransportException("SSE 流读取失败: " + url, e);
        }
    }

    private static HttpRequest buildRequest(String url, Map<String, String> headers, String jsonBody) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        headers.forEach(b::header);
        return b.build();
    }

    /** HTTP 非 2xx 状态错误。 */
    public static class HttpStatusException extends RuntimeException {
        private final int status;

        public HttpStatusException(int status, String body) {
            super("HTTP " + status + ": " + body);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /** 传输层通用错误。 */
    public static class HttpTransportException extends RuntimeException {
        public HttpTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
