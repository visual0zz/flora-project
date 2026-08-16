package com.flora.sanctum.server;

import com.flora.sanctum.model.ExternalKeyService;
import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.model.Sanctum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SanctumHttpServerTest {

    @TempDir
    Path dir;

    private String post(String url, String body) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        String resp = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        return resp;
    }

    @Test
    void httpEncryptDecryptRoundTrip() throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        ExternalKeyService svc = new ExternalKeyService(s);
        UUID entry = s.createEntry(null, "holder", com.flora.sanctum.model.EntryFields.EMPTY);
        UUID keyField = svc.createExternalKey(entry, "k", "material".getBytes(), "for app");

        SanctumHttpServer http = new SanctumHttpServer(() -> s, 0);
        http.start();
        try {
            int port = http.port();
            String listResp = post("http://127.0.0.1:" + port + "/keys/list", "{}");
            JsonObject list = JsonUtil.parseObject(listResp);
            assertTrue(list.getBool("ok"));
            assertFalse(list.getArray("keys").isEmpty());

            String dataB64 = Base64.getEncoder().encodeToString("hello http".getBytes(StandardCharsets.UTF_8));
            String encReq = "{\"uuid\":\"" + keyField + "\",\"data\":\"" + dataB64 + "\"}";
            String encResp = post("http://127.0.0.1:" + port + "/crypt/encrypt", encReq);
            JsonObject enc = JsonUtil.parseObject(encResp);
            assertTrue(enc.getBool("ok"));
            String cipher = enc.getString("cipher");

            String decReq = "{\"cipher\":\"" + cipher + "\"}";
            String decResp = post("http://127.0.0.1:" + port + "/crypt/decrypt", decReq);
            JsonObject dec = JsonUtil.parseObject(decResp);
            assertTrue(dec.getBool("ok"));
            assertEquals("hello http", new String(Base64.getDecoder().decode(dec.getString("data")), StandardCharsets.UTF_8));
        } finally {
            http.stop();
        }
    }

    @Test
    void lockedReturnsLockedError() throws Exception {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray());
        s.lock();
        SanctumHttpServer http = new SanctumHttpServer(() -> s, 0);
        http.start();
        try {
            int port = http.port();
            String resp = post("http://127.0.0.1:" + port + "/crypt/encrypt", "{\"uuid\":\"x\",\"data\":\"eA==\"}");
            JsonObject n = JsonUtil.parseObject(resp);
            assertFalse(n.getBool("ok"));
            assertEquals("locked", n.getString("error"));
        } finally {
            http.stop();
        }
    }
}
