package com.flora.sanctum.model;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.BlockResolver;
import com.flora.sanctum.crypto.KeyIdIndex;
import com.flora.sanctum.crypto.SecureRandomSource;
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
    private final Manifest manifest;
    private final KeyIdIndex keyIdIndex;
    private final BlockResolver resolver;
    private final SecureRandomSource random;
    private final WarehouseClock clock;
    private final java.util.Map<String, byte[]> rootDeksByRole = new java.util.LinkedHashMap<>();
    private final java.util.Map<java.util.UUID, byte[]> folderDeks = new java.util.LinkedHashMap<>();
    private byte[] kek; // 解锁期间驻留内存，锁定/关闭时清除

    Vault(ObjectStore store, Manifest manifest, KeyIdIndex keyIdIndex, SecureRandomSource random, byte[] kek) {
        this.store = store;
        this.manifest = manifest;
        this.keyIdIndex = keyIdIndex;
        this.resolver = new BlockResolver(keyIdIndex);
        this.random = random;
        this.clock = new WarehouseClock(manifest.warehouseTime());
        this.kek = kek == null ? null : kek.clone();
    }

    public WarehouseClock clock() {
        return clock;
    }

    /** 登记 root DEK（按 role：objects/icon/sshKey）。 */
    public void addRootDek(String role, byte[] dek) {
        rootDeksByRole.put(role, dek.clone());
        // 同时登记进 keyId 索引
        keyIdIndex.register(dek);
    }

    /** 取某 role 的 root DEK。 */
    public byte[] rootDek(String role) {
        byte[] d = rootDeksByRole.get(role);
        return d == null ? null : d.clone();
    }

    /** 按 role 路由加密归属（设计 05）：普通对象→objects，图标→icon，SSH 密钥→sshKey。 */
    public byte[] dekForRole(String role) {
        byte[] dek = rootDeksByRole.get(role);
        if (dek == null) {
            throw new IllegalStateException("no DEK for role: " + role);
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
        java.util.List<byte[]> copy = new java.util.ArrayList<>(rootDeksByRole.size());
        for (byte[] d : rootDeksByRole.values()) {
            copy.add(d.clone());
        }
        return copy;
    }

    /** 驻留内存的 KEK（解锁期间；锁定后为 null）。 */
    public byte[] kek() {
        return kek == null ? null : kek.clone();
    }

    /** 清除驻留密钥（锁定/关闭时）。 */
    public void clearSecrets() {
        if (kek != null) {
            java.util.Arrays.fill(kek, (byte) 0);
            kek = null;
        }
        for (byte[] d : rootDeksByRole.values()) {
            java.util.Arrays.fill(d, (byte) 0);
        }
        rootDeksByRole.clear();
        for (byte[] d : folderDeks.values()) {
            java.util.Arrays.fill(d, (byte) 0);
        }
        folderDeks.clear();
        keyIdIndex.clear();
    }

    public ObjectStore store() {
        return store;
    }

    public Manifest manifest() {
        return manifest;
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
    public byte[] resolve(byte[] obfuscatedBlock) {
        return resolver.decode(obfuscatedBlock);
    }
}
