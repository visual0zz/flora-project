package com.flora.sanctum.model;
import com.flora.sanctum.model.tree.*;
import com.flora.sanctum.model.vault.*;
import com.flora.sanctum.model.impl.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.Argon2Kdf;
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
        SecureRandomSource rng = new SecureRandomSource();
        byte[] salt = new byte[16];
        rng.nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt, 65536, 3, 4); // 用较低内存加速测试
        byte[] kek = kdf.derive(password);
        byte[] macKey = kdf.manifestMacKey(kek);

        JsonObject manifest = new JsonObject();
        manifest.put("version", 1);
        manifest.put("type", "manifest");
        manifest.put("cryptoVersion", "gcm-siv-1");
        manifest.put("kdf", "argon2id");
        manifest.put("salt", Base64.getEncoder().encodeToString(salt));
        JsonObject params = new JsonObject();
        params.put("m", 65536);
        params.put("i", 3);
        params.put("p", 4);
        manifest.put("params", params);
        UUID rootGroupUuid = UUID.randomUUID();
        manifest.put("rootGroupUuid", rootGroupUuid.toString());
        manifest.put("updateTimestamp", 1);

        // 块格式与密文对齐：header + payload + mac(尾附)，MAC 覆盖 header+timestamp+payload
        UUID uuid = UUID.randomUUID();
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] block = com.flora.sanctum.model.impl.ManifestStore.buildBlock(uuid, payload, 1, macKey);

        ObjectStore store = new MarkdownObjectStore(dir);
        java.nio.file.Path f = dir.resolve(uuid + ".md");
        try {
            java.nio.file.Files.writeString(f, "1:" + Base58.encode(block) + "\n");
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
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
        assertThrows(IllegalArgumentException.class, () -> unlocker.unlock("wrong password".toCharArray()));
    }
}
