package com.flora.sanctum.model.vault;
import com.flora.sanctum.model.*;

import com.flora.sanctum.crypto.Argon2Kdf;
import com.flora.sanctum.crypto.impl.KeyIdIndex;
import com.flora.sanctum.crypto.impl.SecureRandomSource;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.ObjectStore;

import java.util.List;

/**
 * 库解锁器（见设计 02"解锁流程"）。
 * <p>
 * 流程：扫描块 → 找 manifest（明文块，type=manifest）→ Argon2id 派生 KEK →
 * 验证 manifest MAC → 构建 Vault。root DEK 与文件夹 DEK 由本类内部发现并登记。
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
        byte[] payload = com.flora.sanctum.model.impl.ManifestStore.payloadOf(full);
        Manifest manifest = Manifest.fromJson(payload);
        // 2. 派生 KEK
        byte[] salt = manifest.salt();
        Argon2Kdf kdf = new Argon2Kdf(salt, manifest.memoryKiB(), manifest.iterations(), manifest.parallelism());
        byte[] kek = kdf.derive(masterPassword);
        try {
            // 3. 验证 manifest MAC（覆盖完整信封头 + 时间戳原文 + 负载，尾附于块末）
            verifyMac(full, manifestBlock.timestampText(), manifest, kek);
        } catch (IllegalArgumentException e) {
            java.util.Arrays.fill(kek, (byte) 0);
            throw e;
        }
        KeyIdIndex index = new KeyIdIndex();
        long baseTimestamp = maxBlockTimestamp(blocks);
        Vault vault = new Vault(store, manifest, index, new SecureRandomSource(), kek, baseTimestamp);
        // 4. 解根对象：manifest 记录 rootGroupUuid，O(1) 定位；KEK 试解出 root DEK 与 repoKeyIdSeed
        discoverRootDeks(vault, kek, blocks);
        // KEK 由 Vault 驻留（锁定/关闭时 clearSecrets）
        return vault;
    }

    /**
     * 发现并登记全部文件夹 DEK（见设计 02"解锁流程"）。
     * 根对象由 manifest.rootGroupUuid 定位（O(1)），KEK 试解出 root DEK 后，
     * 用每个已知 DEK 试解各 cipher 块，对 type==group 且含 dek 的登记其 DEK，逐层递归直至无新增。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        // 根对象块用 KEK 加密，但 keyId 由 repoKeyIdSeed 派生（解锁时尚未读出），
        // 无法用 keyId 预筛 → 直接按 manifest 记录的 uuid 定位，KEK 试解（GCM tag 确证）。
        java.util.List<byte[]> known = new java.util.ArrayList<>();
        known.add(kek); // 不 clone：后续以引用是否即 KEK 判断根对象
        java.util.UUID rootUuid = vault.manifest().rootGroupUuid();
        boolean progress = false;
        if (rootUuid != null) {
            for (Block b : blocks) {
                if (!rootUuid.equals(b.uuid())) {
                    continue;
                }
                byte[] plain = tryDecode(vault, kek, b);
                if (plain != null) {
                    com.flora.root.codec.json.model.JsonObject n = parsePlain(plain);
                    if (n != null && n.getString("dek") != null) {
                        // 根对象：manifest 已记录 uuid 并定位，唯一根即 DATA
                        RootTag tag = RootTag.DATA;
                        vault.addRootGroupUuid(tag, b.uuid());
                        // 根对象承载仓库级 keyId 派生种子
                        String seedB64 = n.getString("repoKeyIdSeed");
                        if (seedB64 != null) {
                            vault.setRepoKeyIdSeed(java.util.Base64.getDecoder().decode(seedB64));
                        }
                        byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                        byte[] dek = unwrap(vault, kek, wrapped);
                        if (dek != null) {
                            vault.addRootDek(tag, dek);
                            known.add(dek.clone());
                            progress = true;
                        }
                    }
                }
                break;
            }
        }
        // 逐层发现文件夹 DEK（group 块用父 DEK 包裹）
        boolean any = true;
        while (any) {
            any = false;
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
                        com.flora.root.codec.json.model.JsonObject n = parsePlain(plain);
                        NodeType nt = NodeType.fromTag(n == null ? null : n.getString("type"));
                        if (nt == NodeType.GROUP && n.getString("dek") != null
                                && vault.folderDek(b.uuid()) == null) {
                            byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
                            byte[] dek = unwrap(vault, dk, wrapped);
                            if (dek != null) {
                                vault.addFolderDek(b.uuid(), dek);
                                known.add(dek.clone());
                                any = true;
                                progress = true;
                            }
                        }
                    } catch (Exception ignore) {
                    }
                    break; // 该块已用某 DEK 解开，不再试其它
                }
            }
        }
    }

    private com.flora.root.codec.json.model.JsonObject parsePlain(byte[] plain) {
        try {
            return com.flora.root.codec.JsonUtil.parseObject(
                    new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] tryDecode(Vault vault, byte[] dk, Block b) {
        try {
            byte[] encK = com.flora.sanctum.crypto.KeyDerivation.encKey(dk);
            com.flora.sanctum.crypto.impl.CipherCodec gc = new com.flora.sanctum.crypto.impl.CipherCodec(encK, dk, vault.random());
            return gc.decode(b.obfuscated(), b.timestampText()).plaintext;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] unwrap(Vault vault, byte[] parentDek, byte[] wrapped) {
        try {
            byte[] encK = com.flora.sanctum.crypto.KeyDerivation.encKey(parentDek);
            com.flora.sanctum.crypto.impl.CipherCodec gc = new com.flora.sanctum.crypto.impl.CipherCodec(encK, parentDek, vault.random());
            return gc.decode(wrapped, "0").plaintext;
        } catch (Exception e) {
            return null;
        }
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
                    // 明文块：header + payload + mac(尾附)，负载从中段取
                    byte[] payload = com.flora.sanctum.model.impl.ManifestStore.payloadOf(full);
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

    private void verifyMac(byte[] full, String timestamp, Manifest m, byte[] kek) {
        byte[] macKey = m.manifestMacKey(kek);
        if (!com.flora.sanctum.model.impl.ManifestStore.verifyMac(full, timestamp, macKey)) {
            throw new IllegalArgumentException("manifest MAC mismatch");
        }
    }
}
