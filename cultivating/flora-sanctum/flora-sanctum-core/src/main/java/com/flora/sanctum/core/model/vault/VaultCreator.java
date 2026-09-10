package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.ObjectStore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 新建库（见设计 02"manifest"与"文件夹 DEK"）。
 * <p>
 * 生成 salt、manifest（明文块 + MAC，并携带经 KEK 加密的 {@code seed} blob：仓库级 keyId 派生种子
 * repoKeyIdSeed）、唯一根对象（data 根，type=root，持明文 rootDek 对，经 KEK 直接加密），写入库根。
 * 根对象 uuid 为随机、不可定位：不记入 manifest、不由 KEK 推导；解锁时扫描全部块经 keyId 命中
 * {@code type==root} 定位（见设计"seed 进 manifest"与"根对象不可定位"）。
 */
public final class VaultCreator {

    private final ObjectStore store;
    private final SecureRandomSource random = new SecureRandomSource();
    private byte[] repoKeyIdSeed; // 本次创建生成的仓库级 keyId 派生种子（KEK 加密后写入 manifest.seed；创建后清除）

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
        // 仓库级 keyId 派生种子（经 KEK 加密后写入 manifest 的 seed 字段；解锁时由 KEK 解密，见设计"seed 进 manifest"）
        byte[] seed = new byte[32];
        random.nextBytes(seed);
        repoKeyIdSeed = seed;
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            // 根对象 uuid 为随机、不可定位：不记入 manifest、不由 KEK 推导；解锁时扫描全部块经 keyId 命中 type==root。
            java.util.UUID rootUuid = random.nextUuid();
            // 初始块统一打真实当前时间戳，避免新建库所有块落在哨兵值 1 上，
            // 导致解锁时钟锚点被钉在 1971（见 VaultUnlocker.maxBlockTimestamp）。
            long created = System.currentTimeMillis();
            writeManifestBlock(salt, memoryKiB, iterations, parallelism, macKey, kek, seed, created);
            // 唯一根对象：data 根（type=root），持明文 rootDek 对，直接用 KEK 加密（其 keyId 经 seed 派生）
            writeRootGroup(rootUuid, kek, seed, created);
        } finally {
            if (repoKeyIdSeed != null) {
                java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
                repoKeyIdSeed = null;
            }
            java.util.Arrays.fill(seed, (byte) 0);
            java.util.Arrays.fill(kek, (byte) 0);
        }
    }

    /** 写 manifest 明文块（随机 uuid，经 ManifestStore 落盘；MAC 覆盖 uuid + 完整信封头 + 时间戳 + 负载）。
     *  manifest 额外携带 seed：repoKeyIdSeed 经 KEK 加密的 blob，解锁时由 KEK 解密即得种子。 */
    private void writeManifestBlock(byte[] salt, int memKiB, int iterations, int parallelism,
                                   byte[] macKey, byte[] kek, byte[] repoKeyIdSeed, long created) {
        byte[] encryptedSeed = com.flora.sanctum.core.model.impl.ManifestStore.encryptSeed(repoKeyIdSeed, kek);
        Manifest manifest = new Manifest(1, "gcm-siv-1", "argon2id", salt, memKiB, iterations, parallelism, encryptedSeed);
        new com.flora.sanctum.core.model.impl.ManifestStore(store, random).write(manifest, macKey, Long.toString(created));
    }

    private void writeRootGroup(java.util.UUID rootUuid, byte[] kek, byte[] repoKeyIdSeed, long created) {
        // 生成独立 rootDek 明文存入根对象；顶层对象/顶层分组 DEK 均用 rootDek 加密与包裹。
        // 根对象块整体以 KEK 加密（外层保护），dek1/dek2 字段直接存明文 base64，无需内层包裹。
        // 双 DEK：dek1 退役中、dek2 活跃，供惰性轮换（前向保密，见 GroupKeyRotation 设计）。
        // 仓库级 keyId 派生种子已移至 manifest 的 seed 字段，根对象不再承载 repoKeyIdSeed。
        byte[] dek1 = new byte[32];
        byte[] dek2 = new byte[32];
        random.nextBytes(dek1);
        random.nextBytes(dek2);
        try {
            com.flora.root.codec.json.model.JsonObject group = new com.flora.root.codec.json.model.JsonObject();
            group.put("type", StoredNodeType.ROOT.tag());
            group.put("dek1", Base64.getEncoder().encodeToString(dek1));
            group.put("dek2", Base64.getEncoder().encodeToString(dek2));
            group.remove("dek");
            group.remove("repoKeyIdSeed");
            writeCipherBlock(rootUuid, group, kek, created);
        } finally {
            java.util.Arrays.fill(dek1, (byte) 0);
            java.util.Arrays.fill(dek2, (byte) 0);
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
