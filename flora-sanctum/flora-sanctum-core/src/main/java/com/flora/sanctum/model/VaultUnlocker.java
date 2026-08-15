package com.flora.sanctum.model;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.impl.KeyIdIndex;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
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
        Vault vault = new Vault(store, manifest, index, new SecureRandomSource(), kek);
        // 4. 用 KEK 试解各 group，找到 KEK 能解开的顶层 root group，解出并登记其 DEK
        discoverRootDeks(vault, kek, blocks);
        // KEK 由 Vault 驻留（锁定/关闭时 clearSecrets）
        return vault;
    }

    /**
     * 递归发现并登记全部文件夹 DEK（见设计 02"解锁流程"）。
     * 工作队列：初为 KEK；解出 root/folder DEK 后，用每个已知 DEK 试解各 cipher 块，
     * 对 type==group 且含 dek 的登记其 DEK，逐层递归直至无新增。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        java.util.List<byte[]> known = new java.util.ArrayList<>();
        known.add(kek.clone());
        boolean progress = true;
        while (progress) {
            progress = false;
            for (Block b : blocks) {
                if (!b.isCipher()) {
                    continue;
                }
                for (byte[] dk : known) {
                    byte[] plain = tryDecode(vault, dk, b);
                    if (plain == null) {
                        continue;
                    }
                    try {
                        com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                                new String(plain, java.nio.charset.StandardCharsets.UTF_8));
                        if ("group".equals(n.getString("type")) && n.getString("dek") != null) {
                            if (n.getString("role") != null) {
                                // root group
                                vault.addRootGroupUuid(n.getString("role"), b.uuid());
                                if (vault.rootDek(n.getString("role")) == null) {
                                    byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                                    byte[] dek = unwrap(vault, dk, wrapped);
                                    if (dek != null) {
                                        vault.addRootDek(n.getString("role"), dek);
                                        known.add(dek.clone());
                                        progress = true;
                                    }
                                }
                            } else if (vault.folderDek(b.uuid()) == null) {
                                byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                                byte[] dek = unwrap(vault, dk, wrapped);
                                if (dek != null) {
                                    vault.addFolderDek(b.uuid(), dek);
                                    known.add(dek.clone());
                                    progress = true;
                                }
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    break; // 该块已用某 DEK 解开，不再试其它
                }
            }
        }
    }

    private byte[] tryDecode(Vault vault, byte[] dk, Block b) {
        try {
            byte[] encK = com.flora.sanctum.crypto.KeyDerivation.encKey(dk);
            com.flora.sanctum.crypto.impl.CipherCodec gc = new com.flora.sanctum.crypto.impl.CipherCodec(encK, dk, vault.random());
            return gc.decode(b.obfuscated()).plaintext;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] unwrap(Vault vault, byte[] parentDek, byte[] wrapped) {
        try {
            byte[] encK = com.flora.sanctum.crypto.KeyDerivation.encKey(parentDek);
            com.flora.sanctum.crypto.impl.CipherCodec gc = new com.flora.sanctum.crypto.impl.CipherCodec(encK, parentDek, vault.random());
            return gc.decode(wrapped).plaintext;
        } catch (Exception e) {
            return null;
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
                    com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(json);
                    if ("manifest".equals(n.getString("type"))) {
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
