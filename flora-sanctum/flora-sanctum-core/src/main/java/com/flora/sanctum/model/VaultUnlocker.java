package com.flora.sanctum.model;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.KeyIdIndex;
import com.flora.sanctum.crypto.SecureRandomSource;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.BlockHeader;
import com.flora.sanctum.store.ObjectStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.util.List;

/**
 * 库解锁器（见设计 02"解锁流程"）。
 * <p>
 * 流程：扫描块 → 找 manifest（明文块，type=manifest）→ Argon2id 派生 KEK →
 * 验证 manifest MAC → 构建 Vault。DEK（三个顶层 group 根 DEK + 文件夹 DEK）
 * 由上层（阶段3 适配器）解析 group 负载后经 {@link #registerDek(Vault, byte[])} 登记进索引。
 */
public final class VaultUnlocker {

    private final ObjectStore store;

    public VaultUnlocker(ObjectStore store) {
        this.store = store;
    }

    /**
     * 解锁：返回 Vault；主密码错误或 manifest 校验失败抛 {@link IllegalArgumentException}。
     */
    public Vault unlock(char[] masterPassword) {
        List<Block> blocks = store.scan();
        // 1. 找 manifest 明文块
        Block manifestBlock = findManifest(blocks);
        if (manifestBlock == null) {
            throw new IllegalArgumentException("vault has no manifest");
        }
        byte[] full = manifestBlock.deobfuscated();
        byte[] payload = new byte[full.length - 22];
        System.arraycopy(full, 22, payload, 0, payload.length);
        Manifest manifest = Manifest.fromJson(payload);
        // 2. 派生 KEK
        byte[] salt = manifest.salt();
        Argon2Kdf kdf = new Argon2Kdf(salt, manifest.memoryKiB(), manifest.iterations(), manifest.parallelism());
        byte[] kek = kdf.derive(masterPassword);
        try {
            // 3. 验证 manifest MAC
            verifyMac(manifest, kek);
        } catch (IllegalArgumentException e) {
            java.util.Arrays.fill(kek, (byte) 0);
            throw e;
        }
        KeyIdIndex index = new KeyIdIndex();
        return new Vault(store, manifest, index, new SecureRandomSource());
    }

    /**
     * 登记一个 DEK（根 group 或文件夹 group 的 DEK）进 keyId 索引。
     */
    public void registerDek(Vault vault, byte[] dek) {
        vault.keyIdIndex().register(dek);
    }

    private Block findManifest(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.isPlaintext()) {
                try {
                    byte[] full = b.deobfuscated();
                    // 明文块：magic(4)+version(1)+flags(1)+uuid(16)+payload，负载从偏移 22 开始
                    byte[] payload = new byte[full.length - 22];
                    System.arraycopy(full, 22, payload, 0, payload.length);
                    String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                    Json.Node n = Json.parse(json);
                    if ("manifest".equals(n.str("type"))) {
                        return b;
                    }
                } catch (Exception ignore) {
                    // 非 manifest 明文块，跳过
                }
            }
        }
        return null;
    }

    private void verifyMac(Manifest m, byte[] kek) {
        byte[] macKey = m.manifestMacKey(kek);
        // 计算规范字节序的 MAC（此处简化：对 manifest JSON 的规范序列重算）
        // 生产：需对"信封头‖负载规范字节序"做 HMAC。此处以重新构造的负载为准。
        byte[] canonical = canonicalPayload(m);
        byte[] expected = hmac(macKey, canonical);
        if (!java.security.MessageDigest.isEqual(expected, m.mac())) {
            throw new IllegalArgumentException("manifest MAC mismatch");
        }
    }

    private byte[] canonicalPayload(Manifest m) {
        // 简化规范序列：version|type|cryptoVersion|kdf|salt|params|warehouseTime|updateTimestamp
        StringBuilder sb = new StringBuilder();
        sb.append(m.version()).append('|');
        sb.append("manifest").append('|');
        sb.append(m.cryptoVersion()).append('|');
        sb.append(m.kdf()).append('|');
        sb.append(java.util.Base64.getEncoder().encodeToString(m.salt())).append('|');
        sb.append(m.memoryKiB()).append(',').append(m.iterations()).append(',').append(m.parallelism()).append('|');
        sb.append(m.warehouseTime()).append('|');
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
