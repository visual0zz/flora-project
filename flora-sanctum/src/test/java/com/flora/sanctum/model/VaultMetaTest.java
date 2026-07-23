package com.flora.sanctum.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaultMetaTest {

    @Test
    void vaultMetaDefaults() {
        VaultMeta meta = new VaultMeta();
        assertEquals(VaultMeta.FORMAT_VERSION, meta.getFormatVersion());
        assertEquals(VaultMeta.DEFAULT_KDF, meta.getKdf());
        assertEquals(VaultMeta.DEFAULT_ITERATIONS, meta.getIterations());
        assertTrue(meta.getRemotes().isEmpty());
    }

    @Test
    void vaultMetaToMapRoundTrip() {
        VaultMeta meta = new VaultMeta();
        meta.setSalt("c2FsdHlzYWx0c2FsdHlzYWx0"); // base64 "saltysaltsaltysalt"
        meta.setIterations(600_000);

        Map<String, Object> map = meta.toMap();
        VaultMeta restored = VaultMeta.fromMap(map);

        assertEquals(meta.getFormatVersion(), restored.getFormatVersion());
        assertEquals(meta.getKdf(), restored.getKdf());
        assertEquals(meta.getIterations(), restored.getIterations());
        assertEquals(meta.getSalt(), restored.getSalt());
        assertEquals(meta.getCreatedAt(), restored.getCreatedAt());
    }

    @Test
    void vaultMetaWithRemotes() {
        VaultMeta meta = new VaultMeta();
        meta.setSalt("c2FsdHlzYWx0c2FsdHlzYWx0");
        ArrayList<RemoteInfo> remotes = new ArrayList<>();
        remotes.add(new RemoteInfo("https://github.com/user/vault.git", "HTTPS", "user"));
        meta.setRemotes(remotes);

        Map<String, Object> map = meta.toMap();
        VaultMeta restored = VaultMeta.fromMap(map);

        assertEquals(1, restored.getRemotes().size());
        assertEquals("https://github.com/user/vault.git", restored.getRemotes().get(0).getUrl());
        assertEquals("HTTPS", restored.getRemotes().get(0).getProtocol());
    }
}
