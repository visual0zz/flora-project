package com.flora.sanctum.model;

import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class VaultCreatorTest {

    @TempDir
    Path dir;

    @Test
    void createThenUnlock() {
        ObjectStore store = new MarkdownObjectStore(dir);
        char[] pw = "master password 123".toCharArray();
        VaultCreator creator = new VaultCreator(store);
        creator.create(pw, 65536, 3, 4);

        VaultUnlocker unlocker = new VaultUnlocker(store);
        Vault vault = unlocker.unlock(pw);
        assertNotNull(vault);
        assertEquals("gcm-siv-1", vault.manifest().cryptoVersion());
        // 三个顶层 root group 的 DEK 应已登记进索引（每 DEK 256 个 keyId，共约 768，可能有极少数碰撞）
        int size = vault.keyIdIndex().size();
        assertTrue(size >= 700 && size <= 768, "expected ~768 keyIds, got " + size);
    }

    @Test
    void unlockWrongPasswordFails() {
        ObjectStore store = new MarkdownObjectStore(dir);
        new VaultCreator(store).create("pw".toCharArray(), 65536, 3, 4);
        assertThrows(IllegalArgumentException.class,
                () -> new VaultUnlocker(store).unlock("wrong".toCharArray()));
    }
}
