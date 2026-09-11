package com.flora.sanctum.core.model;
import com.flora.sanctum.core.model.tree.*;
import com.flora.sanctum.core.model.vault.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.Argon2KDF;
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
     * 构造一个可完整解锁的最小仓库：manifest 明文块（随机 uuid，扫描 type=="manifest" 定位）
     * + 根对象块（随机 uuid，直接以 KEK 加密，仅持 repoKeyIdSeed / dek1 / dek2）。
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

        // manifest 用普通随机 uuid；新格式：仓库级 keyId 种子经 KEK 加密写入 manifest.seed
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
        byte[] repoSeed = new byte[32];
        rng.nextBytes(repoSeed);
        manifest.put("seed", Base64.getEncoder().encodeToString(
                com.flora.sanctum.core.model.impl.ManifestStore.encryptSeed(repoSeed, kek)));

        ObjectStore store = new MarkdownObjectStore(dir);
        UUID manifestUuid = UUID.randomUUID();
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] block = ManifestStore.buildBlock(
                com.flora.sanctum.core.crypto.impl.CipherCodec.uuidBytes(manifestUuid), payload, "1", macKey);
        store.put(manifestUuid, block, null, "1");

        if (!withRoot) {
            return store;
        }
        // 根对象块：{type:root, repoKeyIdSeed, dek1, dek2}，直接用 KEK 加密；dek 为明文随机 rootDek。
        // 新格式根对象 uuid 为随机值，解锁时按 type=="root" 扫描定位，故此处写任意随机 uuid 即可。
        byte[] rootDek = new byte[32];
        rng.nextBytes(rootDek);
        com.flora.sanctum.core.crypto.impl.CipherCodec rootCodec = new com.flora.sanctum.core.crypto.impl.CipherCodec(
                com.flora.sanctum.core.crypto.KeyDerivation.encKey(kek), kek, repoSeed, rng);
        JsonObject root = new JsonObject();
        root.put("type", "root");
        root.put("repoKeyIdSeed", Base64.getEncoder().encodeToString(repoSeed));
        root.put("dek1", Base64.getEncoder().encodeToString(rootDek));
        root.put("dek2", Base64.getEncoder().encodeToString(rootDek));
        byte[] rootJson = JsonUtil.toJsonString(root).getBytes(StandardCharsets.UTF_8);
        UUID rootUuid = UUID.randomUUID();
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
        // category 层落地后，缺 categories 映射的旧格式根对象被硬性拒绝（见设计：不支持就地升级，
        // 逃生通道为「导出旧库 → 新建库 → 导入」）。新格式解锁的正确路径由 VaultCreatorTest.createThenUnlock 覆盖。
        VaultUnlockException ex = assertThrows(VaultUnlockException.class, () -> unlocker.unlock(pw));
        assertEquals(VaultUnlockException.Phase.OLD_FORMAT_REJECTED, ex.phase());
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
