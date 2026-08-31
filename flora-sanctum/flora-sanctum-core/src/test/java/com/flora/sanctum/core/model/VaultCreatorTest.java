package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.sanctum.core.store.ObjectStore;
import com.flora.sanctum.core.store.impl.MarkdownObjectStore;
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
        // 单根模型：唯一根对象 DEK 对应 1 条 dekId 索引（KEK 不入索引，见设计"keyId 防关联"）
        assertEquals(1, vault.keyIdIndex().size());
    }

    @Test
    void unlockWrongPasswordFails() {
        ObjectStore store = new MarkdownObjectStore(dir);
        new VaultCreator(store).create("pw".toCharArray(), 65536, 3, 4);
        VaultUnlockException ex = assertThrows(VaultUnlockException.class,
                () -> new VaultUnlocker(store).unlock("wrong".toCharArray()));
        assertEquals(VaultUnlockException.Phase.MANIFEST_CORRUPT, ex.phase());
    }
}
