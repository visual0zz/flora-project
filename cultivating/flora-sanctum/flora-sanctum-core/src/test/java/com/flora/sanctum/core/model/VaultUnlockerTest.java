package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.RootUuid;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.ObjectStore;
import com.flora.sanctum.core.store.impl.MarkdownObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultUnlockerTest {

    @TempDir
    Path dir;

    /**
     * 构造一个可完整解锁的最小仓库：manifest 明文块（普通随机 uuid，扫描 type=="manifest" 定位）
     * + 根对象块（uuid 由 KEK 单向推导，直接用 KEK 加密，仅持 repoKeyIdSeed）。
     *
     * @param withRoot false 时只写 manifest，用于验证根对象缺失的解锁失败阶段
     */
    private ObjectStore createVault(char[] password, boolean withRoot) {
        SecureRandomSource rng = new SecureRandomSource();
        byte[] salt = new byte[16];
        rng.nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt, 65536, 3, 4); // 用较低内存加速测试
        byte[] kek = kdf.derive(password);
        byte[] macKey = kdf.manifestMacKey(kek);

        // manifest 不记录根对象 uuid（根 uuid 由 KEK 单向推导得出）；manifest 用普通随机 uuid
        JsonObject manifest = new JsonObject();
        manifest.put("version", 1);
        manifest.put("type", "manifest");
        manifest.put("crypto", "gcm-siv-1");
        manifest.put("kdf", "argon2id");
        manifest.put("salt", Base64.getEncoder().encodeToString(salt));
        JsonObject params = new JsonObject();
        params.put("memoryKiB", 65536);
        params.put("iterations", 3);
        params.put("parallelism", 4);
        manifest.put("params", params);

        ObjectStore store = new MarkdownObjectStore(dir);
        UUID manifestUuid = UUID.randomUUID();
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] block = ManifestStore.buildBlock(
                com.flora.sanctum.core.crypto.impl.CipherCodec.uuidBytes(manifestUuid), payload, "1", macKey);
        store.put(manifestUuid, block, null, "1");

        if (!withRoot) {
            return store;
        }
        // 根对象块：{type:root, repoKeyIdSeed, dek}，直接用 KEK 加密；dek 为明文随机 rootDek
        byte[] repoSeed = new byte[32];
        rng.nextBytes(repoSeed);
        byte[] rootDek = new byte[32];
        rng.nextBytes(rootDek);
        com.flora.sanctum.core.crypto.impl.CipherCodec rootCodec = new com.flora.sanctum.core.crypto.impl.CipherCodec(
                com.flora.sanctum.core.crypto.KeyDerivation.encKey(kek), kek, repoSeed, rng);
        JsonObject root = new JsonObject();
        root.put("type", "root");
        root.put("repoKeyIdSeed", Base64.getEncoder().encodeToString(repoSeed));
        root.put("dek", Base64.getEncoder().encodeToString(rootDek));
        byte[] rootJson = JsonUtil.toJsonString(root).getBytes(StandardCharsets.UTF_8);
        UUID rootUuid = RootUuid.derive(kek);
        byte[] rootBlock = rootCodec.encode(rootUuid, rootJson, "1");
        store.put(rootUuid, rootBlock, null, "1");
        return store;
    }

    private ObjectStore createVault(char[] password) {
        return createVault(password, true);
    }

    @Test
    void unlockWithCorrectPassword() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createVault(pw);
        VaultUnlocker unlocker = new VaultUnlocker(store);
        Vault vault = unlocker.unlock(pw);
        assertNotNull(vault);
        assertEquals("gcm-siv-1", vault.manifest().crypto());
        // 根对象 uuid 由 KEK 推导，解锁后登记在 vault 上（manifest 未记录）
        assertNotNull(vault.rootObjectUuid());
    }

    @Test
    void unlockFailsWithWrongPassword() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createVault(pw);
        VaultUnlocker unlocker = new VaultUnlocker(store);
        VaultUnlockException ex = assertThrows(VaultUnlockException.class,
                () -> unlocker.unlock("wrong password".toCharArray()));
        assertEquals(VaultUnlockException.Phase.MANIFEST_CORRUPT, ex.phase());
    }

    @Test
    void unlockReportsMissingManifest() {
        ObjectStore store = new MarkdownObjectStore(dir);
        VaultUnlocker unlocker = new VaultUnlocker(store);
        VaultUnlockException ex = assertThrows(VaultUnlockException.class,
                () -> unlocker.unlock("pw".toCharArray()));
        assertEquals(VaultUnlockException.Phase.NOT_A_VAULT, ex.phase());
    }

    @Test
    void unlockReportsMissingRootObject() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createVault(pw, false);
        VaultUnlocker unlocker = new VaultUnlocker(store);
        VaultUnlockException ex = assertThrows(VaultUnlockException.class,
                () -> unlocker.unlock(pw));
        assertEquals(VaultUnlockException.Phase.ROOT_MISSING, ex.phase());
    }
}
