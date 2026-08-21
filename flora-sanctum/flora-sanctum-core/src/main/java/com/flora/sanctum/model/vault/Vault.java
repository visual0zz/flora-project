package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.impl.BlockResolver;
import com.flora.sanctum.crypto.impl.KeyIdIndex;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.crypto.impl.HkdfSha256;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;

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
    private final java.util.Map<RootTag, byte[]> rootDeksByTag = new java.util.LinkedHashMap<>();
    private final java.util.Map<RootTag, java.util.UUID> rootGroupUuidByTag = new java.util.LinkedHashMap<>();
    private final java.util.Map<java.util.UUID, byte[]> folderDeks = new java.util.LinkedHashMap<>();
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

    /** 登记 root DEK（按根概念 tag：data/icon/sshKey）。 */
    public void addRootDek(RootTag tag, byte[] dek) {
        rootDeksByTag.put(tag, dek.clone());
        // 同时登记进 keyId 索引
        keyIdIndex.register(dek);
    }

    /** 登记某根概念的顶层 group uuid（供新对象定位所属 root）。 */
    public void addRootGroupUuid(RootTag tag, java.util.UUID groupUuid) {
        rootGroupUuidByTag.put(tag, groupUuid);
    }

    /** 取某根概念的顶层 group uuid。 */
    public java.util.UUID rootGroupUuid(RootTag tag) {
        return rootGroupUuidByTag.get(tag);
    }

    /** 取某根概念的 root DEK。 */
    public byte[] rootDek(RootTag tag) {
        byte[] d = rootDeksByTag.get(tag);
        return d == null ? null : d.clone();
    }

    /** 按根概念路由加密归属（设计 05）：普通对象→data，图标→icon，SSH 密钥→sshKey。 */
    public byte[] dekForRole(RootTag tag) {
        byte[] dek = rootDeksByTag.get(tag);
        if (dek == null) {
            throw new IllegalStateException("no DEK for root tag: " + tag);
        }
        return dek.clone();
    }

    /** 登记文件夹 DEK（group uuid → DEK，供目录/递归解锁/创建路由）。 */
    public void addFolderDek(java.util.UUID groupUuid, byte[] dek) {
        folderDeks.put(groupUuid, dek.clone());
        keyIdIndex.register(dek);
    }

    /** 取某文件夹的 DEK。 */
    public byte[] folderDek(java.util.UUID groupUuid) {
        byte[] d = folderDeks.get(groupUuid);
        return d == null ? null : d.clone();
    }

    /** 全部 root DEK（兼容旧接口）。 */
    public java.util.List<byte[]> rootDeks() {
        java.util.List<byte[]> copy = new java.util.ArrayList<>(rootDeksByTag.size());
        for (byte[] d : rootDeksByTag.values()) {
            copy.add(d.clone());
        }
        return copy;
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
        for (byte[] d : rootDeksByTag.values()) {
            java.util.Arrays.fill(d, (byte) 0);
        }
        rootDeksByTag.clear();
        for (byte[] d : folderDeks.values()) {
            java.util.Arrays.fill(d, (byte) 0);
        }
        folderDeks.clear();
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
     */
    public byte[] resolve(byte[] obfuscatedBlock, long timestamp) {
        return resolver.decode(obfuscatedBlock, timestamp);
    }
}
