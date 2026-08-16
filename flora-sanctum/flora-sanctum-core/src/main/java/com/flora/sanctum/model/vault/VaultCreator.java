package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.root.codec.Base58;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.ObjectStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.util.Base64;
import java.util.UUID;

/**
 * 新建库（见设计 02"manifest"与"文件夹 DEK"）。
 * <p>
 * 生成 salt、manifest（明文块 + MAC）、三个顶层 group（普通对象/icon/sshKey root，
 * 各持独立随机 KEK 包裹的 root DEK），写入库根。
 */
public final class VaultCreator {

    private final ObjectStore store;
    private final SecureRandomSource random = new SecureRandomSource();

    public VaultCreator(ObjectStore store) {
        this.store = store;
    }

    /**
     * 创建新库。默认高安全档 Argon2id 参数。
     */
    public void create(char[] masterPassword) {
        create(masterPassword, Argon2Kdf.DEFAULT_MEMORY_KIB, Argon2Kdf.DEFAULT_ITERATIONS, Argon2Kdf.DEFAULT_PARALLELISM);
    }

    public void create(char[] masterPassword, int memoryKiB, int iterations, int parallelism) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Argon2Kdf kdf = new Argon2Kdf(salt, memoryKiB, iterations, parallelism);
        byte[] kek = kdf.derive(masterPassword);
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            writeManifestBlock(salt, memoryKiB, iterations, parallelism, macKey, kek);
            // 三个顶层 group：各持独立随机 DEK（用 KEK 包裹），parent 为根概念 tag
            for (RootTag tag : new RootTag[]{RootTag.DATA, RootTag.ICON, RootTag.SSH_KEY}) {
                writeRootGroup(tag, kek);
            }
        } finally {
            java.util.Arrays.fill(kek, (byte) 0);
        }
    }

    /** 写 manifest 明文块。先定 uuid，MAC 覆盖信封头 uuid + 负载（含 updateTimestamp）。 */
    private void writeManifestBlock(byte[] salt, int m, int i, int p, byte[] macKey, byte[] kek) {
        UUID uuid = UUID.randomUUID();
        com.flora.root.codec.json.model.JsonObject manifest = new com.flora.root.codec.json.model.JsonObject();
        manifest.put("version", 1);
        manifest.put("type", "manifest");
        manifest.put("parent", RootTag.MANIFEST.tag());
        manifest.put("cryptoVersion", "gcm-siv-1");
        manifest.put("kdf", "argon2id");
        manifest.put("salt", Base64.getEncoder().encodeToString(salt));
        com.flora.root.codec.json.model.JsonObject params = new com.flora.root.codec.json.model.JsonObject();
        params.put("m", m);
        params.put("i", i);
        params.put("p", p);
        manifest.put("params", params);
        manifest.put("warehouseTime", 1);
        manifest.put("updateTimestamp", 1);
        // 用临时 Manifest 计算覆盖 uuid+负载的 MAC
        Manifest tmp = new Manifest(1, RootTag.MANIFEST.tag(), "gcm-siv-1", "argon2id", salt, m, i, p, 1, 1, new byte[0]);
        byte[] mac = tmp.computeMac(macKey, uuid);
        manifest.put("mac", Base64.getEncoder().encodeToString(mac));
        writePlaintextBlock(uuid, manifest, Envelope.FLAG_PLAINTEXT);
    }

    private void writeRootGroup(RootTag tag, byte[] kek) {
        com.flora.root.codec.json.model.JsonObject group = new com.flora.root.codec.json.model.JsonObject();
        group.put("version", 1);
        group.put("type", "group");
        group.put("parent", tag.tag());
        // 生成独立随机 DEK，用 KEK 包裹（存于 group 密文块内）
        byte[] dek = new byte[32];
        random.nextBytes(dek);
        byte[] wrapped = wrap(dek, kek);
        group.put("dek", Base64.getEncoder().encodeToString(wrapped));
        group.put("updateTimestamp", 1);
        writeCipherBlock(group, kek);
    }

    /** 用 KEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机）。 */
    private byte[] wrap(byte[] dek, byte[] kek) {
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(kek);
        com.flora.sanctum.crypto.impl.CipherCodec codec = new com.flora.sanctum.crypto.impl.CipherCodec(encKey, dek, random);
        return codec.encode(UUID.randomUUID(), dek, codec.makeKeyIdWith(kek));
    }

    private void writeCipherBlock(com.flora.root.codec.json.model.JsonObject payload, byte[] keyMaterial) {
        byte[] json = com.flora.root.codec.JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(keyMaterial);
        com.flora.sanctum.crypto.impl.CipherCodec codec = new com.flora.sanctum.crypto.impl.CipherCodec(encKey, keyMaterial, random);
        UUID uuid = UUID.randomUUID();
        byte[] block = codec.encode(uuid, json, codec.makeKeyIdWith(keyMaterial));
        store.put(uuid, block, null);
    }

    private void writePlaintextBlock(UUID uuid, com.flora.root.codec.json.model.JsonObject payload, byte flags) {
        byte[] json = com.flora.root.codec.JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] block = new byte[Envelope.PLAINTEXT_HEADER_LEN + json.length];
        System.arraycopy(Envelope.MAGIC, 0, block, 0, Envelope.MAGIC_LEN);
        block[Envelope.MAGIC_LEN] = Envelope.VERSION_1;
        block[Envelope.MAGIC_LEN + 1] = flags;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(block, Envelope.MAGIC_LEN + 2, 16);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        System.arraycopy(json, 0, block, Envelope.PLAINTEXT_HEADER_LEN, json.length);
        byte xor = random.nextByte();
        byte[] obf = BlockHeader.obfuscate(block, xor);
        store.put(uuid, obf, null);
    }
}
