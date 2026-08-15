package com.flora.sanctum.model;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.SecureRandomSource;
import com.flora.root.codec.Base58;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.ObjectStore;
import com.flora.sanctum.store.impl.MarkdownObjectStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
        manifest.put("warehouseTime", 1);
        manifest.put("updateTimestamp", 1);

        // 先定 uuid，再计算覆盖 uuid + 全部负载字段的 MAC（与 Manifest.canonical 一致）
        UUID uuid = UUID.randomUUID();
        byte[] payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        String canonical = uuid + "|1|manifest|gcm-siv-1|argon2id|" + Base64.getEncoder().encodeToString(salt)
                + "|65536,3,4|1|1|";
        byte[] mac = hmac(macKey, canonical.getBytes(StandardCharsets.UTF_8));
        manifest.put("mac", Base64.getEncoder().encodeToString(mac));
        payload = JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);

        // 明文块信封：magic+version+flags(0x02)+uuid+payload
        byte[] block = new byte[6 + 16 + payload.length];
        System.arraycopy(com.flora.sanctum.crypto.Envelope.MAGIC, 0, block, 0, 4);
        block[4] = 1;
        block[5] = 2;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, 6, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(payload, 0, block, 22, payload.length);
        byte xor = rng.nextByte();
        byte[] obf = BlockHeader.obfuscate(block, xor);

        ObjectStore store = new MarkdownObjectStore(dir);
        java.nio.file.Path f = dir.resolve(uuid + ".md");
        try {
            java.nio.file.Files.writeString(f, Base58.encode(obf) + "\n");
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

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
