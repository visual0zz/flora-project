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
        assertEquals("gcm-siv-1", vault.manifest().crypto());
        // 单根模型：keyId 索引含三条——根对象块用的 KEK，以及被注册为 groupDek 的 rootDek 对
        // （dek1 退役中 + dek2 活跃，二者不同故各占一条）。
        assertEquals(3, vault.keyIdIndex().size());
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
