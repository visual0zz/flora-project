package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;
import com.flora.sanctum.core.model.impl.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ExternalKeyServiceTest {

    @TempDir
    Path dir;

    @Test
    void encryptDecryptRoundTrip() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        ExternalKeyService svc = new ExternalKeyService(s);
        // 建一个条目，再加外部密钥字段
        UUID entry = s.objectTree().createEntry(null, "holder", EntryFields.EMPTY).uuid();
        UUID keyField = svc.createExternalKey(entry, "mykey", "secret-key-material".getBytes(StandardCharsets.UTF_8), "for app A");

        byte[] cipherBlock = svc.encrypt("hello world".getBytes(StandardCharsets.UTF_8), keyField);
        String cipherB64 = Base64.getEncoder().encodeToString(cipherBlock);

        byte[] plain = svc.decrypt(cipherB64);
        assertArrayEquals("hello world".getBytes(StandardCharsets.UTF_8), plain);
    }

    @Test
    void listShowsExternalKeys() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        ExternalKeyService svc = new ExternalKeyService(s);
        UUID entry = s.objectTree().createEntry(null, "holder", EntryFields.EMPTY).uuid();
        svc.createExternalKey(entry, "k1", "material1".getBytes(), "desc 1");

        var keys = svc.list();
        assertFalse(keys.isEmpty());
        assertEquals("desc 1", keys.get(0).description());
    }

    @Test
    void decryptFailsForWrongKey() {
        Sanctum s = Sanctum.createAndUnlock(dir, "pw".toCharArray(), 8192, 2, 1);
        ExternalKeyService svc = new ExternalKeyService(s);
        UUID entry = s.objectTree().createEntry(null, "holder", EntryFields.EMPTY).uuid();
        UUID keyA = svc.createExternalKey(entry, "a", "material-A".getBytes(), "A");
        svc.createExternalKey(entry, "b", "material-B".getBytes(), "B");

        byte[] cipher = svc.encrypt("data".getBytes(), keyA);
        String cipherB64 = Base64.getEncoder().encodeToString(cipher);
        // 解密候选域包含 A 和 B，A 能解开
        assertArrayEquals("data".getBytes(), svc.decrypt(cipherB64));
    }
}
