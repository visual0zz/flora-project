package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

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
byte[] payload = new byte[full.length - com.flora.sanctum.crypto.impl.Envelope.PLAINTEXT_HEADER_LEN];
            System.arraycopy(full, com.flora.sanctum.crypto.impl.Envelope.PLAINTEXT_HEADER_LEN, payload, 0, payload.length);
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
        long baseTimestamp = maxBlockTimestamp(blocks);
        Vault vault = new Vault(store, manifest, index, new SecureRandomSource(), kek, baseTimestamp);
        // 4. 用 KEK 试解各 group，找到 KEK 能解开的顶层 root group，解出并登记其 DEK
        discoverRootDeks(vault, kek, blocks);
        // KEK 由 Vault 驻留（锁定/关闭时 clearSecrets）
        return vault;
    }

    /**
     * 递归发现并登记全部文件夹 DEK（见设计 02"解锁流程"）。
     * 工作队列：初为 KEK；解出 root/folder DEK 后，用每个已知 DEK 试解各 cipher 块，
     * 对 type==group 且含 dek 的登记其 DEK，逐层递归直至无新增。
     * <p>
     * 顶层 root group 识别：root group 的块与 dek 均用 KEK 包裹（parent 为根概念 tag，
     * 见设计 05），故以"dk 引用是否即 KEK"区分 root group 与普通文件夹（用户顶层文件夹
     * 虽 parent 也是概念 tag，但其块用 data 根 DEK 加密，KEK 解不开，按普通文件夹登记）。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        // root group 块用 KEK 加密，但 keyId 由 repoKeyIdSeed 派生（解锁时尚未读出），
        // 无法用 keyId 预筛 → 直接对每块用已知 DEK 试解（GCM tag 确证）。
        java.util.List<byte[]> known = new java.util.ArrayList<>();
        known.add(kek); // 不 clone：后续以引用是否即 KEK 判断 root group
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
                        NodeType nt = NodeType.fromTag(n.getString("type"));
                        if ((nt == NodeType.ROOT || nt == NodeType.GROUP) && n.getString("dek") != null) {
                            if (dk == kek) {
                                // root group：parent 为根概念 tag
                                RootTag tag = RootTag.fromTag(n.getString("parent"));
                                vault.addRootGroupUuid(tag, b.uuid());
                                // DATA 根承载仓库级 keyId 派生种子
                                if (tag == RootTag.DATA) {
                                    String seedB64 = n.getString("repoKeyIdSeed");
                                    if (seedB64 != null) {
                                        vault.setRepoKeyIdSeed(java.util.Base64.getDecoder().decode(seedB64));
                                    }
                                }
                                if (vault.rootDek(tag) == null) {
                                    byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                                    byte[] dek = unwrap(vault, dk, wrapped);
                                    if (dek != null) {
                                        vault.addRootDek(tag, dek);
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
            return gc.decode(b.obfuscated(), b.timestamp()).plaintext;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] unwrap(Vault vault, byte[] parentDek, byte[] wrapped) {
        try {
            byte[] encK = com.flora.sanctum.crypto.KeyDerivation.encKey(parentDek);
            com.flora.sanctum.crypto.impl.CipherCodec gc = new com.flora.sanctum.crypto.impl.CipherCodec(encK, parentDek, vault.random());
            return gc.decode(wrapped, 0).plaintext;
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

    /** 会话时钟锚点 = max(全库最大块时间戳, min(最大+1年, 当前毫秒))（见设计 02"仓库时间戳"）。
     *  既避免时间回拨导致时钟倒退，又封顶于真实当前时间，防止锚点被 +1 年无限制前移。 */
    private static long maxBlockTimestamp(List<Block> blocks) {
        long max = 1;
        for (Block b : blocks) {
            if (b.timestamp() > max) {
                max = b.timestamp();
            }
        }
        long plusYear = max + 365L * 24 * 3600 * 1000; // +1 年（毫秒）
        long cappedNow = Math.min(plusYear, System.currentTimeMillis());
        return Math.max(max, cappedNow);
    }

    private Block findManifest(List<Block> blocks) {        for (Block b : blocks) {
            if (b.isPlaintext()) {
                try {
                    byte[] full = b.deobfuscated();
                    // 明文块：magic(8)+version(1)+flags(1)+uuid(16)+payload，负载从 PLAINTEXT_HEADER_LEN 开始
                    byte[] payload = new byte[full.length - com.flora.sanctum.crypto.impl.Envelope.PLAINTEXT_HEADER_LEN];
                    System.arraycopy(full, com.flora.sanctum.crypto.impl.Envelope.PLAINTEXT_HEADER_LEN,
                            payload, 0, payload.length);
                    String json = new String(payload, java.nio.charset.StandardCharsets.UTF_8);
                    com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(json);
                    if (NodeType.MANIFEST == NodeType.fromTag(n.getString("type"))) {
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
