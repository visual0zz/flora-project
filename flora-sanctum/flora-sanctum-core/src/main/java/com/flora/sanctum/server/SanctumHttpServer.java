package com.flora.sanctum.server;

import com.flora.sanctum.model.ExternalKeyService;
import com.flora.sanctum.model.Json;
import com.flora.sanctum.model.Sanctum;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * 本地 HTTP + JSON 传输（见设计 02"HTTP 传输"）。
 * <p>
 * 仅绑定 127.0.0.1；只暴露外部密钥加解密服务端点，不暴露任何编辑/仓库管理能力。
 * 跟随应用启动；锁定时所有端点返回 {@code locked} 错误，不泄露任何密钥能力。
 */
public final class SanctumHttpServer {

    private final Sanctum sanctum;
    private final HttpServer server;

    public SanctumHttpServer(Sanctum sanctum, int port) throws IOException {
        this.sanctum = sanctum;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(2));
        registerRoutes();
    }

    private void registerRoutes() {
        server.createContext("/keys/list", this::handleList);
        server.createContext("/crypt/encrypt", this::handleEncrypt);
        server.createContext("/crypt/decrypt", this::handleDecrypt);
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
    }

    /** 实际绑定的端口（port=0 时随机）。 */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handleList(HttpExchange ex) throws IOException {
        requirePost(ex);
        Json.Node resp = Json.obj();
        if (!sanctum.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        ExternalKeyService svc = new ExternalKeyService(sanctum);
        Json.Node keys = Json.arr();
        for (ExternalKeyService.KeyInfo k : svc.list()) {
            Json.Node item = Json.obj();
            Json.put(item, "uuid", Json.of(k.uuid.toString()));
            Json.put(item, "name", Json.of(k.name));
            Json.put(item, "description", Json.of(k.description));
            keys.asArray().add(item);
        }
        Json.put(resp, "ok", Json.of(true));
        Json.put(resp, "keys", keys);
        respond(ex, 200, Json.stringify(resp));
    }

    private void handleEncrypt(HttpExchange ex) throws IOException {
        requirePost(ex);
        if (!sanctum.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        Json.Node req = readJson(ex);
        String uuidStr = req.str("uuid");
        String dataB64 = req.str("data");
        if (uuidStr == null || dataB64 == null) {
            error(ex, "bad_request", "uuid and data required");
            return;
        }
        try {
            UUID uuid = UUID.fromString(uuidStr);
            byte[] data = Base64.getDecoder().decode(dataB64);
            byte[] cipher = new ExternalKeyService(sanctum).encrypt(data, uuid);
            Json.Node resp = Json.obj();
            Json.put(resp, "ok", Json.of(true));
            Json.put(resp, "cipher", Json.of(com.flora.sanctum.store.Base58.encode(cipher)));
            respond(ex, 200, Json.stringify(resp));
        } catch (Exception e) {
            error(ex, "encrypt_failed", e.getMessage());
        }
    }

    private void handleDecrypt(HttpExchange ex) throws IOException {
        requirePost(ex);
        if (!sanctum.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        Json.Node req = readJson(ex);
        String cipher = req.str("cipher");
        if (cipher == null) {
            error(ex, "bad_request", "cipher required");
            return;
        }
        try {
            byte[] data = new ExternalKeyService(sanctum).decrypt(cipher);
            Json.Node resp = Json.obj();
            Json.put(resp, "ok", Json.of(true));
            Json.put(resp, "data", Json.of(Base64.getEncoder().encodeToString(data)));
            respond(ex, 200, Json.stringify(resp));
        } catch (Exception e) {
            error(ex, "decrypt_failed", "decryption failed");
        }
    }

    private void requirePost(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            error(ex, "bad_request", "POST required");
            throw new IllegalStateException("POST required");
        }
    }

    private Json.Node readJson(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        return Json.parse(new String(body, StandardCharsets.UTF_8));
    }

    private void error(HttpExchange ex, String code, String message) throws IOException {
        Json.Node resp = Json.obj();
        Json.put(resp, "ok", Json.of(false));
        Json.put(resp, "error", Json.of(code));
        Json.put(resp, "message", Json.of(message));
        respond(ex, 200, Json.stringify(resp));
    }

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
