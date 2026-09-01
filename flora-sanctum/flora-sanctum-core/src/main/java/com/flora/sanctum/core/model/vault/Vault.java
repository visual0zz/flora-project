package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.impl.BlockResolver;
import com.flora.sanctum.core.crypto.impl.KeyIdIndex;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.crypto.impl.HkdfSha256;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.ObjectStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

/**
 * 解锁后的库状态（见设计 02"解锁流程"）。
 * <p>
 * 持有 KEK、manifest、keyId 索引与存储引用；锁定即丢弃（不持有 DEK 明文持久态）。
 */
public final class Vault {

    private final ObjectStore store;
    private Manifest manifest;
    private final KeyIdIndex keyIdIndex;
    private final BlockResolver resolver;
    private final SecureRandomSource random;
    private WarehouseClock clock;
    private byte[] dataDek; // 根级密钥（即 KEK）：根对象直接用 KEK 加解密，无独立根 DEK
    private java.util.UUID rootObjectUuid; // 唯一根对象 uuid（由 KEK 单向推导，解锁后登记）
    private final java.util.Map<java.util.UUID, byte[]> groupDeks = new java.util.LinkedHashMap<>();
    private byte[] kek; // 解锁期间驻留内存，锁定/关闭时清除
    private byte[] repoKeyIdSeed; // 仓库级 keyId 派生种子（DATA 根 json 存储），锁定/关闭时清除

    Vault(ObjectStore store, Manifest manifest, KeyIdIndex keyIdIndex, SecureRandomSource random, byte[] kek, long baseTimestamp) {
        this.store = store;
        this.manifest = manifest;
        this.keyIdIndex = keyIdIndex;
        this.resolver = new BlockResolver(keyIdIndex, this::repoKeyIdSeed);
        this.random = random;
        this.clock = new WarehouseClock(baseTimestamp);
        this.kek = kek == null ? null : kek.clone();
    }

    public WarehouseClock clock() {
        return clock;
    }

    /** 登记根级密钥（根对象以 KEK 直接加解密，故根级密钥即 KEK；同时登记进 keyId 索引）。 */
    public void addRootDek(byte[] dek) {
        this.dataDek = dek.clone();
        // 同时登记进 keyId 索引
        keyIdIndex.register(dek);
    }

    /** 登记根对象的 uuid（由 KEK 单向推导得到，解锁后登记）。 */
    public void addRootObjectUuid(java.util.UUID groupUuid) {
        this.rootObjectUuid = groupUuid;
    }

    /** 取根对象的 uuid。 */
    public java.util.UUID rootObjectUuid() {
        return rootObjectUuid;
    }

    /**
     * 根级密钥（即 KEK）。用于加密 root 对象块本身，并作为无独立密钥场景的兜底。
     * 注意：顶层对象（parent 指向 root 的组/条目/图标/SSH/远程）实际用 {@link #rootDek()} 加密，
     * 而非本 KEK（见设计"root DEK"）。
     */
    public byte[] dataDek() {
        if (dataDek == null) {
            throw new IllegalStateException("no root DEK");
        }
        return dataDek.clone();
    }

    /**
     * 根对象的实际子树密钥 rootDek：注册为 {@code groupDek(rootObjectUuid())}。
     * 顶层对象及其直接子分组 DEK 的加密都用它；换主密码时 rootDek 值不变，
     * 仅根对象块整体改以新 KEK 重加密。返回 null 表示尚未解锁登记。
     */
    public byte[] rootDek() {
        byte[] d = groupDek(rootObjectUuid());
        return d == null ? null : d.clone();
    }

    /** 登记 group DEK（group uuid → DEK，供目录/递归解锁/创建路由）。 */
    public void addGroupDek(java.util.UUID groupUuid, byte[] dek) {
        groupDeks.put(groupUuid, dek.clone());
        keyIdIndex.register(dek);
    }

    /** 取某 group 的 DEK。 */
    public byte[] groupDek(java.util.UUID groupUuid) {
        byte[] d = groupDeks.get(groupUuid);
        return d == null ? null : d.clone();
    }

    /** 驻留内存的 KEK（解锁期间；锁定后为 null）。 */
    public byte[] kek() {
        return kek == null ? null : kek.clone();
    }

    /** 仓库级 keyId 派生种子（解锁期间；未存储则 null）。 */
    public byte[] repoKeyIdSeed() {
        return repoKeyIdSeed == null ? null : repoKeyIdSeed.clone();
    }

    /** 登记仓库级 keyId 派生种子（解锁时从 DATA 根 json 读取）。 */
    public void setRepoKeyIdSeed(byte[] seed) {
        if (repoKeyIdSeed != null) {
            java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
        }
        this.repoKeyIdSeed = seed == null ? null : seed.clone();
    }

    /** 换主密码后更新驻留 KEK。 */
    public void replaceKek(byte[] newKek) {
        if (kek != null) {
            java.util.Arrays.fill(kek, (byte) 0);
        }
        this.kek = newKek == null ? null : newKek.clone();
    }

    /** 清除驻留密钥（锁定/关闭时）。 */
    public void clearSecrets() {
        if (kek != null) {
            java.util.Arrays.fill(kek, (byte) 0);
            kek = null;
        }
        if (dataDek != null) {
            java.util.Arrays.fill(dataDek, (byte) 0);
            dataDek = null;
        }
        for (byte[] d : groupDeks.values()) {
            java.util.Arrays.fill(d, (byte) 0);
        }
        groupDeks.clear();
        if (repoKeyIdSeed != null) {
            java.util.Arrays.fill(repoKeyIdSeed, (byte) 0);
            repoKeyIdSeed = null;
        }
        keyIdIndex.clear();
    }

    public ObjectStore store() {
        return store;
    }

    public Manifest manifest() {
        return manifest;
    }

    /** 换主密码/更新 KDF 参数后替换内存 manifest（供后续 close/写回使用新值）。 */
    public void replaceManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    public KeyIdIndex keyIdIndex() {
        return keyIdIndex;
    }

    public BlockResolver resolver() {
        return resolver;
    }

    public SecureRandomSource random() {
        return random;
    }

    /**
     * 解密一个密文块为负载字节；非本库可解返回 null。
     *
     * @param uuid      块的对象 uuid（不存于块内；文件块由块文件路径反推，重建 AAD）
     * @param timestamp 块级时间戳原文（落盘前缀字符串，重建 AAD）
     */
    public byte[] resolve(byte[] obfuscatedBlock, java.util.UUID uuid, String timestamp) {
        return resolver.decode(obfuscatedBlock, uuid, timestamp);
    }
}
