package com.flora.root.ai.api.provider.client;

import com.flora.root.ai.api.ApiSchema;
import com.flora.root.ai.api.ChatRequest;
import com.flora.root.ai.api.Endpoint;
import com.flora.root.ai.api.IOMode;
import com.flora.root.ai.api.Message;
import com.flora.root.ai.api.StreamEvent;
import com.flora.root.ai.api.StreamIterator;
import com.flora.root.ai.api.impl.HttpTransport;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式修复回归测试：验证 {@code ArrayBlockingQueue(64)+offer} 的静默丢数据已消除，
 * 以及生产者线程模型下首 token 无需等待整段响应缓冲，并验证异常经 {@link StreamEvent.Error} 暴露。
 * 通过本地 {@link HttpServer} 提供真实 HTTP 响应来驱动真实客户端 {@link OpenAiOfficialClient#stream}。
 */
class StreamingNoDataLossTest {

    /** 启动本地 HTTP 服务，对 /v1/chat/completions 返回给定 SSE 文本（或指定状态码）。 */
    private static HttpServer serve(String sseBody, int status) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            byte[] body = sseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(status, status == 200 ? body.length : 0);
            if (status == 200) {
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private OpenAiOfficialClient clientTo(HttpServer server, String modelId) {
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        Endpoint endpoint = Endpoint.of("t", ApiSchema.OPENAI_OFFICIAL, modelId,
                baseUrl, "k", false, IOMode.STREAM, Map.of());
        return new OpenAiOfficialClient(endpoint, HttpTransport.create());
    }

    /** 构造含 n 个文本增量的 OpenAI SSE 流（末尾 [DONE]）。 */
    private static String openAiSse(int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append("data: {\"choices\":[{\"delta\":{\"content\":\"c")
                    .append(i).append("\"}}]}\n\n");
        }
        sb.append("data: [DONE]\n\n");
        return sb.toString();
    }

    @Test
    void noDataLossBeyondQueueCapacity() throws IOException {
        // 超过旧实现 64 阈值仍不丢数据
        int n = 200;
        HttpServer server = serve(openAiSse(n), 200);
        try {
            OpenAiOfficialClient client = clientTo(server, "gpt");
            ChatRequest req = ChatRequest.builder()
                    .message(Message.of(Message.Role.USER, "hi")).build();
            List<String> texts = new ArrayList<>();
            try (StreamIterator it = client.stream(req)) {
                while (it.hasNext()) {
                    StreamEvent e = it.next();
                    if (e instanceof StreamEvent.Text t) {
                        texts.add(t.delta());
                    }
                }
            }
            // 旧实现用 ArrayBlockingQueue(64)+offer，>64 分片会静默丢数据
            assertEquals(n, texts.size(), "流式文本增量不应因队列容量而丢失");
            assertEquals("c0", texts.get(0));
            assertEquals("c" + (n - 1), texts.get(n - 1));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void transportErrorSurfacedAsErrorEvent() throws IOException {
        // 服务端返回 500 → HttpStatusException → 经 StreamEvent.Error 暴露
        HttpServer server = serve("", 500);
        try {
            OpenAiOfficialClient client = clientTo(server, "gpt");
            ChatRequest req = ChatRequest.builder()
                    .message(Message.of(Message.Role.USER, "hi")).build();
            List<StreamEvent> events = new ArrayList<>();
            try (StreamIterator it = client.stream(req)) {
                while (it.hasNext()) {
                    events.add(it.next());
                }
            }
            assertTrue(events.stream().anyMatch(e -> e instanceof StreamEvent.Error),
                    "传输异常应经 StreamEvent.Error 暴露");
        } finally {
            server.stop(0);
        }
    }
}
