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
            // 3. 验证 manifest MAC（覆盖信封头 uuid + 负载，含 updateTimestamp）
            verifyMac(manifest, kek, manifestBlock.uuid());
        } catch (IllegalArgumentException e) {
            java.util.Arrays.fill(kek, (byte) 0);
            throw e;
        }
        KeyIdIndex index = new KeyIdIndex();
        Vault vault = new Vault(store, manifest, index, new SecureRandomSource());
        try {
            // 4. 用 KEK 试解各 group，找到 KEK 能解开的顶层 root group，解出并登记其 DEK
            discoverRootDeks(vault, kek, blocks);
        } finally {
            java.util.Arrays.fill(kek, (byte) 0);
        }
        return vault;
    }

    /**
     * 用 KEK 试解各 group（KEK 包裹变体 keyId + GCM-SIV），解出顶层 root group 的 DEK。
     * 生产：还需沿文件夹 DEK 树递归解子文件夹 DEK（此处登记直接可解的 root DEK）。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        byte[] encKey = com.flora.sanctum.crypto.impl.HkdfSha256.derive(kek, null, "sanctum-enc", 32);
        com.flora.sanctum.crypto.CipherCodec codec = new com.flora.sanctum.crypto.CipherCodec(encKey, kek, vault.random());
        for (Block b : blocks) {
            if (!b.isCipher()) {
                continue;
            }
            try {
                byte[] plain = codec.decode(b.obfuscated()).plaintext;
                Json.Node n = Json.parse(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                if ("group".equals(n.str("type")) && n.str("role") != null) {
                    String dekB64 = n.str("dek");
                    if (dekB64 != null) {
                        // dek 字段 = 用 KEK 包裹的 DEK（wrap 产物，本身是 CipherCodec 块）→ 解包得可用 DEK
                        byte[] wrapped = java.util.Base64.getDecoder().decode(dekB64);
                        byte[] dek = codec.decode(wrapped).plaintext;
                        vault.keyIdIndex().register(dek);
                        vault.addRootDek(dek);
                    }
                }
            } catch (Exception ignore) {
                // KEK 解不开 → 不是顶层 root group（普通对象树内由父 DEK 包裹），跳过
            }
        }
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

    private void verifyMac(Manifest m, byte[] kek, java.util.UUID blockUuid) {
        byte[] macKey = m.manifestMacKey(kek);
        byte[] expected = m.computeMac(macKey, blockUuid);
        if (!java.security.MessageDigest.isEqual(expected, m.mac())) {
            throw new IllegalArgumentException("manifest MAC mismatch");
        }
    }
}
