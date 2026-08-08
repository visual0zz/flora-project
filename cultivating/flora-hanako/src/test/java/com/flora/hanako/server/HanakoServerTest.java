package com.flora.hanako.server;

import com.flora.hanako.core.HanakoEngine;
import com.flora.hanako.server.HanakoServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class HanakoServerTest {

    private HanakoServer server;
    private int port;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        port = 14591;
        HanakoEngine engine = new HanakoEngine(Path.of(System.getProperty("java.io.tmpdir"), "hanako-it-" + System.nanoTime()));
        engine.applyProviders();
        server = new HanakoServer(engine);
        server.start(port);
        // 等待端口就绪
        Thread.sleep(800);
    }

    @AfterEach
    void stop() {
        if (server != null) {
            server.stop();
        }
    }

    private String get(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode(), "GET " + path + " 失败: " + resp.body());
        return resp.body();
    }

    @Test
    void healthOk() throws Exception {
        String body = get("/api/health");
        assertTrue(body.contains("\"status\":\"ok\""));
    }

    @Test
    void defaultAgentPresent() throws Exception {
        String body = get("/api/agents");
        assertTrue(body.contains("hanako"));
    }

    @Test
    void createAndListSession() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/sessions"))
                .POST(HttpRequest.BodyPublishers.ofString("{\"agentId\":\"hanako\"}")).build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, resp.statusCode());
        assertTrue(resp.body().contains("id"));
    }

    @Test
    void staticIndexServed() throws Exception {
        String body = get("/");
        assertTrue(body.contains("Hanako"));
    }
}
