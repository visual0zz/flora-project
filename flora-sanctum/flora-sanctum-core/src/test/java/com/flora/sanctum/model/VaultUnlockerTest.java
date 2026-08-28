package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.Argon2KDF;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.root.codec.Base58;
import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;
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

    /** 构造一个 manifest 明文块并写入独立文件，返回 [store, manifest payload]。 */
    private ObjectStore createManifest(char[] password) {
        return createManifest(password, "rootObjectUuid");
    }

    /** 构造 manifest 明文块；rootKey 指定写入的根对象 uuid 持久化键名（含旧 key 兼容性）。
     *  同时写入按该 uuid 定位的根对象块（KEK 包裹的 DEK + repoKeyIdSeed），构成可完整解锁的最小仓库。 */
    private ObjectStore createManifest(char[] password, String rootKey) {
        SecureRandomSource rng = new SecureRandomSource();
        byte[] salt = new byte[16];
        rng.nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt, 65536, 3, 4); // 用较低内存加速测试
        byte[] kek = kdf.derive(password);
        byte[] macKey = kdf.manifestMacKey(kek);

        UUID rootObjectUuid = UUID.randomUUID();
        JsonObject manifest = new JsonObject();
        manifest.put("version", 1);
        manifest.put("type", "manifest");
        manifest.put("cryptoVersion", "gcm-siv-1");
        manifest.put("kdf", "argon2id");
        manifest.put("salt", Base64.getEncoder().encodeToString(salt));
        JsonObject params = new JsonObject();
        params.put("memoryKiB", 65536);
        params.put("iterations", 3);
        params.put("parallelism", 4);
        manifest.put("params", params);
        manifest.put(rootKey, rootObjectUuid.toString());
        manifest.put("updateTimestamp", 1);

        // 块格式与密文对齐：header + payload + mac(尾附)，MAC 覆盖 header+timestamp+payload
        UUID uuid = Manifest.MANIFEST_UUID;
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] block = com.flora.sanctum.model.impl.ManifestStore.buildBlock(uuid, payload, "1", macKey);

        ObjectStore store = new MarkdownObjectStore(dir);
        java.nio.file.Path f = dir.resolve(uuid + ".md");
        try {
            java.nio.file.Files.writeString(f, "1:" + Base58.encode(block) + "\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }

        // 根对象块：{type:root, dek(KEK 包裹), repoKeyIdSeed}，KEK 加密、时间戳 1
        byte[] repoSeed = new byte[32];
        rng.nextBytes(repoSeed);
        com.flora.sanctum.crypto.impl.CipherCodec rootCodec = new com.flora.sanctum.crypto.impl.CipherCodec(
                com.flora.sanctum.crypto.KeyDerivation.encKey(kek), kek, repoSeed, rng);
        JsonObject root = new JsonObject();
        root.put("type", "root");
        byte[] dek = new byte[32];
        rng.nextBytes(dek);
        byte[] wrapped = rootCodec.encode(UUID.randomUUID(), dek, "0"); // 内部信封（DEK 包裹）无块时间戳
        root.put("dek", Base64.getEncoder().encodeToString(wrapped));
        root.put("repoKeyIdSeed", Base64.getEncoder().encodeToString(repoSeed));
        byte[] rootJson = JsonUtil.toJsonString(root).getBytes(StandardCharsets.UTF_8);
        byte[] rootBlock = rootCodec.encode(rootObjectUuid, rootJson, "1");
        store.put(rootObjectUuid, rootBlock, null, "1");
        return store;
    }

    @Test
    void unlockWithCorrectPassword() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createManifest(pw);
        VaultUnlocker unlocker = new VaultUnlocker(store);
        Vault vault = unlocker.unlock(pw);
        assertNotNull(vault);
        assertEquals("gcm-siv-1", vault.manifest().cryptoVersion());
    }

    @Test
    void unlockFailsWithWrongPassword() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createManifest(pw);
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
    void unlockReadsLegacyRootGroupUuidKey() {
        char[] pw = "correct horse battery".toCharArray();
        ObjectStore store = createManifest(pw, "rootGroupUuid");
        VaultUnlocker unlocker = new VaultUnlocker(store);
        Vault vault = unlocker.unlock(pw);
        assertNotNull(vault);
        assertEquals("gcm-siv-1", vault.manifest().cryptoVersion());
    }
}
