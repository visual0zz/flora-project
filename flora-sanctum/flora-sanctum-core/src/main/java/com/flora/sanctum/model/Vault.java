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
    private final java.util.List<byte[]> rootDeks = new java.util.ArrayList<>();

    Vault(ObjectStore store, Manifest manifest, KeyIdIndex keyIdIndex, SecureRandomSource random) {
        this.store = store;
        this.manifest = manifest;
        this.keyIdIndex = keyIdIndex;
        this.resolver = new BlockResolver(keyIdIndex);
        this.random = random;
    }

    public void addRootDek(byte[] dek) {
        rootDeks.add(dek.clone());
    }

    public java.util.List<byte[]> rootDeks() {
        java.util.List<byte[]> copy = new java.util.ArrayList<>(rootDeks.size());
        for (byte[] d : rootDeks) {
            copy.add(d.clone());
        }
        return copy;
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
