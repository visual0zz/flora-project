package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.ObjectStore;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 新建库（见设计 02"manifest"与"文件夹 DEK"，及设计"category 分隔层"）。
 * <p>
 * 生成 salt、manifest（明文块 + MAC，并携带经 KEK 加密的 {@code seed} blob：仓库级 keyId 派生种子
 * repoKeyIdSeed）、唯一根对象（data 根，type=root，持明文 rootDek 对，经 KEK 直接加密，并记载
 * {@code categories} 映射），以及 4 个 category 节点（password/icon/sshKey/remote，各持独立 DEK 对、
 * 以 rootDek 加密外层、parent 指向根对象），写入库根。
 * 根对象与 category 节点 uuid 均为随机、不可定位：不记入 manifest、不由 KEK 推导；解锁时扫描全部块经
 * keyId 命中 {@code type==root}/{@code type==category} 定位（见设计"seed 进 manifest"与"根对象不可定位"）。
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
        // 根级 DEK 对（dek1 退役中 / dek2 活跃）：根对象自身与其下 category 节点外层均以 rootDek(dek2) 加密。
        // 声明于 try 之外，便于 finally 统一清零（category 写完之后）。
        byte[] rootDek1 = new byte[32];
        byte[] rootDek2 = new byte[32];
        random.nextBytes(rootDek1);
        random.nextBytes(rootDek2);
        try {
            byte[] macKey = kdf.manifestMacKey(kek);
            // 根对象 uuid 为随机、不可定位：不记入 manifest、不由 KEK 推导；解锁时扫描全部块经 keyId 命中 type==root。
            java.util.UUID rootUuid = random.nextUuid();
            // 初始块统一打真实当前时间戳，避免新建库所有块落在哨兵值 1 上，
            // 导致解锁时钟锚点被钉在 1971（见 VaultUnlocker.maxBlockTimestamp）。
            long created = System.currentTimeMillis();
            // 4 个 category 节点（数据类分隔层）：各随机 uuid + 独立 DEK 对；password 承载顶层组/条目，
            // icon/sshKey/remote 各承载对应数据类。均 parent=根对象、以 rootDek 加密外层。
            java.util.Map<String, java.util.UUID> cats = new java.util.LinkedHashMap<>();
            java.util.Map<java.util.UUID, byte[][]> catDeks = new java.util.LinkedHashMap<>();
            for (String disc : new String[]{"password", "icon", "sshKey", "remote"}) {
                java.util.UUID cu = random.nextUuid();
                byte[] c1 = new byte[32];
                byte[] c2 = new byte[32];
                random.nextBytes(c1);
                random.nextBytes(c2);
                cats.put(disc, cu);
                catDeks.put(cu, new byte[][]{c1, c2});
            }
            writeManifestBlock(salt, memoryKiB, iterations, parallelism, macKey, kek, seed, created);
            // 唯一根对象：data 根（type=root），持明文 rootDek 对，直接用 KEK 加密（其 keyId 经 seed 派生），
            // 并记载 categories 映射（数据类 → category 节点 uuid）。
            writeRootGroup(rootUuid, kek, seed, rootDek1, rootDek2, cats, created);
            // 4 个 category 节点：各持独立 DEK 对，以 rootDek(dek2) 加密外层、parent 指向根对象。
            for (java.util.Map.Entry<String, java.util.UUID> e : cats.entrySet()) {
                byte[][] d = catDeks.get(e.getValue());
                writeCategoryNode(e.getValue(), e.getKey(), rootUuid, rootDek2, d[0], d[1], created);
            }
        } finally {
            if (repoKeyIdSeed != null) {
                java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
                repoKeyIdSeed = null;
            }
            java.util.Arrays.fill(rootDek1, (byte) 0);
            java.util.Arrays.fill(rootDek2, (byte) 0);
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

    private void writeRootGroup(java.util.UUID rootUuid, byte[] kek, byte[] repoKeyIdSeed,
                                byte[] rootDek1, byte[] rootDek2,
                                java.util.Map<String, java.util.UUID> categories, long created) {
        // 根对象自身以 KEK 加密（外层保护）；dek1/dek2 字段直接存明文 base64，无需内层包裹。
        // 双 DEK：dek1 退役中、dek2 活跃（rootDek），供惰性轮换（前向保密，见 GroupKeyRotation 设计）。
        // 其下 category 节点外层以 rootDek(dek2) 加密，故根对象记载 categories 映射（数据类 → category uuid），
        // 供解锁时 O(1) 定位。仓库级 keyId 种子已移至 manifest 的 seed 字段，根对象不再承载 repoKeyIdSeed。
        // 注意：rootDek1/rootDek2 不可在此处清零——下方 4 个 category 节点仍以 rootDek2 加密外层，
        // 提前清零会使 category 用全 0 密钥加密、解锁后无法解密。清零统一推迟到 create 的 finally（category 写完之后）。
        com.flora.root.codec.json.model.JsonObject group = new com.flora.root.codec.json.model.JsonObject();
        group.put("type", StoredNodeType.ROOT.tag());
        group.put("dek1", Base64.getEncoder().encodeToString(rootDek1));
        group.put("dek2", Base64.getEncoder().encodeToString(rootDek2));
        com.flora.root.codec.json.model.JsonObject cats = new com.flora.root.codec.json.model.JsonObject();
        for (java.util.Map.Entry<String, java.util.UUID> e : categories.entrySet()) {
            cats.put(e.getKey(), com.flora.sanctum.core.util.UuidHex.toHex(e.getValue()));
        }
        group.put("categories", cats);
        group.remove("dek");
        group.remove("repoKeyIdSeed");
        writeCipherBlock(rootUuid, group, kek, created);
    }

    /**
     * 写单个 category 节点：parent=根对象、以 rootDek(dek2) 加密外层；块内持该数据类的独立 DEK 对，
     * 其下数据对象以该 category 的活跃 DEK 加密（parent=category uuid）。
     */
    private void writeCategoryNode(java.util.UUID catUuid, String discriminator, java.util.UUID rootUuid,
                                   byte[] rootDek, byte[] catDek1, byte[] catDek2, long created) {
        try {
            com.flora.root.codec.json.model.JsonObject node = new com.flora.root.codec.json.model.JsonObject();
            node.put("type", StoredNodeType.CATEGORY.tag());
            node.put("category", discriminator);
            node.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(rootUuid));
            node.put("dek1", Base64.getEncoder().encodeToString(catDek1));
            node.put("dek2", Base64.getEncoder().encodeToString(catDek2));
            // category 节点外层以 rootDek 加密：keyId 由 rootDek 派生，解锁时经已登记的 rootDek 命中。
            writeCipherBlock(catUuid, node, rootDek, created);
        } finally {
            java.util.Arrays.fill(catDek1, (byte) 0);
            java.util.Arrays.fill(catDek2, (byte) 0);
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
