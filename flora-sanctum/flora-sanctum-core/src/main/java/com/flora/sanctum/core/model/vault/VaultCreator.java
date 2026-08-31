package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.RootUuid;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.ObjectStore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 新建库（见设计 02"manifest"与"文件夹 DEK"）。
 * <p>
 * 生成 salt、manifest（明文块 + MAC）、唯一根对象（data 根，type=root，
 * 持 repoKeyIdSeed，经 KEK 直接加密），写入库根。
 * 根对象 uuid 由 KEK 单向推导（见 {@link RootUuid#derive}），不落盘也不记入 manifest。
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
        create(masterPassword, Argon2KDF.DEFAULT_MEMORY_KIB, Argon2KDF.DEFAULT_ITERATIONS, Argon2KDF.DEFAULT_PARALLELISM);
    }

    public void create(char[] masterPassword, int memoryKiB, int iterations, int parallelism) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        Argon2KDF kdf = new Argon2KDF(salt, memoryKiB, iterations, parallelism);
        byte[] kek = kdf.derive(masterPassword);
        // 仓库级 keyId 派生种子（存 DATA 根 json，解锁时读取；见设计"keyId 防关联"）
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        repoKeyIdSeed = seed;
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            // 根对象 uuid 由 KEK 单向推导：不落盘、不记入 manifest，解锁时以同一 KEK 重算定位
            java.util.UUID rootUuid = RootUuid.derive(kek);
            writeManifestBlock(salt, memoryKiB, iterations, parallelism, macKey);
            // 唯一根对象：data 根（type=root），持 repoKeyIdSeed，直接用 KEK 加密
            writeRootGroup(rootUuid, kek, seed);
        } finally {
            if (repoKeyIdSeed != null) {
                java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
                repoKeyIdSeed = null;
            }
            java.util.Arrays.fill(seed, (byte) 0);
            java.util.Arrays.fill(kek, (byte) 0);
        }
    }

    /** 写 manifest 明文块（随机 uuid，经 ManifestStore 落盘；MAC 覆盖 uuid + 完整信封头 + 时间戳 + 负载）。 */
    private void writeManifestBlock(byte[] salt, int memKiB, int iterations, int parallelism, byte[] macKey) {
        Manifest manifest = new Manifest(1, "gcm-siv-1", "argon2id", salt, memKiB, iterations, parallelism);
        new com.flora.sanctum.core.model.impl.ManifestStore(store, random).write(manifest, macKey, "1");
    }

    private void writeRootGroup(java.util.UUID rootUuid, byte[] kek, byte[] repoKeyIdSeed) {
        // 生成独立 rootDek，用 KEK 包裹存入根对象；顶层对象/顶层分组 DEK 均用 rootDek 加密与包裹
        byte[] rootDek = new byte[32];
        random.nextBytes(rootDek);
        try {
            com.flora.sanctum.core.crypto.impl.CipherCodec codec = new com.flora.sanctum.core.crypto.impl.CipherCodec(
                    com.flora.sanctum.core.crypto.KeyDerivation.encKey(kek), kek, repoKeyIdSeed, random);
            byte[] wrapped = codec.encode(com.flora.sanctum.core.crypto.impl.CipherCodec.EMBEDDED_UUID, rootDek, "0");
            com.flora.root.codec.json.model.JsonObject group = new com.flora.root.codec.json.model.JsonObject();
            group.put("type", StoredNodeType.ROOT.tag());
            // 根对象仍由 KEK 直接加解密；仅承载仓库级 keyId 派生种子与（KEK 包裹的）rootDek
            group.put("repoKeyIdSeed", Base64.getEncoder().encodeToString(repoKeyIdSeed));
            group.put("dek", Base64.getEncoder().encodeToString(wrapped));
            writeCipherBlock(rootUuid, group, kek, 1);
        } finally {
            java.util.Arrays.fill(rootDek, (byte) 0);
        }
    }

    private void writeCipherBlock(java.util.UUID uuid, com.flora.root.codec.json.model.JsonObject payload,
                                  byte[] keyMaterial, long timestamp) {
        byte[] json = com.flora.root.codec.JsonUtil.toJsonString(payload).getBytes(StandardCharsets.UTF_8);
        byte[] encKey = com.flora.sanctum.core.crypto.KeyDerivation.encKey(keyMaterial);
        com.flora.sanctum.core.crypto.impl.CipherCodec codec =
                new com.flora.sanctum.core.crypto.impl.CipherCodec(encKey, keyMaterial, repoKeyIdSeed, random);
        String tsText = Long.toString(timestamp);
        byte[] block = codec.encode(uuid, json, tsText);
        store.put(uuid, block, null, tsText);
    }
}
