package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.ObjectStore;

import java.nio.charset.StandardCharsets;
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
    private byte[] repoKeyIdSeed; // 本次创建生成的仓库级 keyId 派生种子（写入 DATA 根 json；创建后清除）

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
        // 仓库级 keyId 派生种子（存 DATA 根 json，解锁时读取；见设计"keyId 防关联"）
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        repoKeyIdSeed = seed;
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            // 先定根对象 uuid（manifest 记录，解锁 O(1) 定位）
            java.util.UUID rootUuid = java.util.UUID.randomUUID();
            writeManifestBlock(salt, memoryKiB, iterations, parallelism, macKey, kek, rootUuid);
            // 唯一根对象：data 根（type=root），持 root DEK 与 repoKeyIdSeed（parent 为根概念 tag）
            writeRootGroup(RootTag.DATA, rootUuid, kek, seed);
        } finally {
            if (repoKeyIdSeed != null) {
                java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
                repoKeyIdSeed = null;
            }
            java.util.Arrays.fill(seed, (byte) 0);
            java.util.Arrays.fill(kek, (byte) 0);
        }
    }

    /** 写 manifest 明文块。MAC 覆盖完整信封头 + 时间戳 + 负载，尾附（与密文块结构对齐）。 */
    private void writeManifestBlock(byte[] salt, int m, int i, int p, byte[] macKey, byte[] kek,
                                    java.util.UUID rootGroupUuid) {
        UUID uuid = UUID.randomUUID();
        com.flora.root.codec.json.model.JsonObject manifest = new com.flora.root.codec.json.model.JsonObject();
        manifest.put("version", 1);
        manifest.put("type", NodeType.MANIFEST.tag());
        manifest.put("cryptoVersion", "gcm-siv-1");
        manifest.put("kdf", "argon2id");
        manifest.put("salt", Base64.getEncoder().encodeToString(salt));
        com.flora.root.codec.json.model.JsonObject params = new com.flora.root.codec.json.model.JsonObject();
        params.put("m", m);
        params.put("i", i);
        params.put("p", p);
        manifest.put("params", params);
        manifest.put("rootGroupUuid", rootGroupUuid.toString());
        manifest.put("updateTimestamp", 1);
        byte[] payload = com.flora.root.codec.JsonUtil.toJsonString(manifest).getBytes(StandardCharsets.UTF_8);
        byte[] block = com.flora.sanctum.model.impl.ManifestStore.buildBlock(uuid, payload, 1, macKey);
        store.put(uuid, block, null, 1);
    }

    private void writeRootGroup(RootTag tag, java.util.UUID rootUuid, byte[] kek, byte[] repoKeyIdSeed) {
        com.flora.root.codec.json.model.JsonObject group = new com.flora.root.codec.json.model.JsonObject();
        group.put("type", NodeType.ROOT.tag());
        // 生成独立随机 DEK，用 KEK 包裹（存于根对象密文块内）
        byte[] dek = new byte[32];
        random.nextBytes(dek);
        byte[] wrapped = wrap(dek, kek);
        group.put("dek", Base64.getEncoder().encodeToString(wrapped));
        // 根对象承载仓库级 keyId 派生种子（仅存一份）
        group.put("repoKeyIdSeed", Base64.getEncoder().encodeToString(repoKeyIdSeed));
        writeCipherBlock(rootUuid, group, kek, 1);
    }

    /** 用 KEK 包裹一个 DEK（AES-GCM-SIV，nonce 随机；内部信封无块时间戳，timestamp=0）。 */
    private byte[] wrap(byte[] dek, byte[] kek) {
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(kek);
        com.flora.sanctum.crypto.impl.CipherCodec codec =
                new com.flora.sanctum.crypto.impl.CipherCodec(encKey, kek, repoKeyIdSeed, random);
        return codec.encode(UUID.randomUUID(), dek, 0);
    }

    private void writeCipherBlock(java.util.UUID uuid, com.flora.root.codec.json.model.JsonObject payload,
                                  byte[] keyMaterial, long timestamp) {
        byte[] json = com.flora.root.codec.JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(keyMaterial);
        com.flora.sanctum.crypto.impl.CipherCodec codec =
                new com.flora.sanctum.crypto.impl.CipherCodec(encKey, keyMaterial, repoKeyIdSeed, random);
        byte[] block = codec.encode(uuid, json, timestamp);
        store.put(uuid, block, null, timestamp);
    }
}
