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
        // 解锁后 keyId 索引应含 KEK、rootDek 对、以及 4 个 category 分隔层节点的 DEK 对；
        // 类别层落地后顶层对象的 parent 指向 category 而非 root，故 category DEK 必须已注册。
        assertNotNull(vault.rootDek());
        for (String disc : new String[]{"password", "icon", "sshKey", "remote"}) {
            java.util.UUID cu = vault.categoryUuid(disc);
            assertNotNull(cu, "category 未注册: " + disc);
            assertNotNull(vault.groupDek(cu), "category DEK 未注册: " + disc);
        }
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
