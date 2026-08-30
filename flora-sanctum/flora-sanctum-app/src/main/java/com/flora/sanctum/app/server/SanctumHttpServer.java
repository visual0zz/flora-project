package com.flora.sanctum.app.server;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonArray;

import com.flora.sanctum.core.model.ExternalKeyService;
import com.flora.sanctum.core.model.Sanctum;
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

    private final java.util.function.Supplier<Sanctum> sanctumSupplier;
    private final HttpServer server;
    private final java.util.concurrent.ExecutorService executor;

    /**
     * @param sanctumSupplier 当前 Sanctum 提供者（未解锁/锁定时可为 null）；应用常驻时用可变持有器。
     * @param port            监听端口（0 = 随机）
     */
    public SanctumHttpServer(java.util.function.Supplier<Sanctum> sanctumSupplier, int port) throws IOException {
        this.sanctumSupplier = sanctumSupplier;
        this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        this.executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        this.server.setExecutor(executor);
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
        executor.shutdown();
    }

    /** 实际绑定的端口（port=0 时随机）。 */
    public int port() {
        return server.getAddress().getPort();
    }

    private void handleList(HttpExchange ex) throws IOException {
        requirePost(ex);
        JsonObject resp = new JsonObject();
        Sanctum s = sanctumSupplier.get();
        if (s == null || !s.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        ExternalKeyService svc = new ExternalKeyService(s);
        JsonArray keys = new JsonArray();
        for (ExternalKeyService.ExternalKeyInfo k : svc.list()) {
            JsonObject item = new JsonObject();
            item.put("uuid", k.uuid().toString());
            item.put("name", k.name());
            item.put("description", k.description());
            keys.add(item);
        }
        resp.put("ok", true);
        resp.put("keys", keys);
        respond(ex, 200, JsonUtil.toJsonString(resp));
    }

    private void handleEncrypt(HttpExchange ex) throws IOException {
        requirePost(ex);
        Sanctum s = sanctumSupplier.get();
        if (s == null || !s.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        JsonObject req = readJson(ex);
        String uuidStr = req.getString("uuid");
        String dataB64 = req.getString("plaintextB64");
        if (uuidStr == null || dataB64 == null) {
            error(ex, "bad_request", "uuid and plaintextB64 required");
            return;
        }
        try {
            UUID uuid = UUID.fromString(uuidStr);
            byte[] data = Base64.getDecoder().decode(dataB64);
            byte[] cipher = new ExternalKeyService(s).encrypt(data, uuid);
            JsonObject resp = new JsonObject();
            resp.put("ok", true);
            resp.put("ciphertextB58", com.flora.root.codec.Base58.encode(cipher));
            respond(ex, 200, JsonUtil.toJsonString(resp));
        } catch (Exception e) {
            error(ex, "encrypt_failed", e.getMessage());
        }
    }

    private void handleDecrypt(HttpExchange ex) throws IOException {
        requirePost(ex);
        Sanctum s = sanctumSupplier.get();
        if (s == null || !s.isUnlocked()) {
            error(ex, "locked", "vault is locked");
            return;
        }
        JsonObject req = readJson(ex);
        String cipher = req.getString("ciphertextB58");
        if (cipher == null) {
            error(ex, "bad_request", "ciphertextB58 required");
            return;
        }
        try {
            byte[] data = new ExternalKeyService(s).decrypt(cipher);
            JsonObject resp = new JsonObject();
            resp.put("ok", true);
            resp.put("plaintextB64", Base64.getEncoder().encodeToString(data));
            respond(ex, 200, JsonUtil.toJsonString(resp));
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

    private JsonObject readJson(HttpExchange ex) throws IOException {
        byte[] body = ex.getRequestBody().readAllBytes();
        return JsonUtil.parseObject(new String(body, StandardCharsets.UTF_8));
    }

    private void error(HttpExchange ex, String code, String message) throws IOException {
        JsonObject resp = new JsonObject();
        resp.put("ok", false);
        resp.put("error", code);
        resp.put("message", message);
        respond(ex, 200, JsonUtil.toJsonString(resp));
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
