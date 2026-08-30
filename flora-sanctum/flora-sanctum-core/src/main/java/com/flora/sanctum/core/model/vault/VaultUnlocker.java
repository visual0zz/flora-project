package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.impl.KeyIdIndex;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.ObjectStore;

import java.util.List;

/**
 * 库解锁器（见设计 02"解锁流程"）。
 * <p>
 * 流程：扫描块 → 找 manifest（明文块，type=manifest）→ Argon2id 派生 KEK →
 * 验证 manifest MAC → 构建 Vault。root DEK 与 group DEK 由本类内部发现并登记：
 * 根对象经 manifest.rootObjectUuid 直接定位（KEK 试解），其余 cipher 块经 keyId 路由定位父 DEK 解开。
 * <p>
 * 失败按阶段抛 {@link VaultUnlockException}（而非统一"解锁失败"），供上层给出针对性提示。
 */
public final class VaultUnlocker {

    private final ObjectStore store;

    public VaultUnlocker(ObjectStore store) {
        this.store = store;
    }

    /**
     * 解锁：返回 Vault；失败抛 {@link VaultUnlockException}（含失败阶段）。
     */
    public Vault unlock(char[] masterPassword) {
        List<Block> blocks = store.scan();
        // 1. 找 manifest 明文块（固定 MANIFEST_UUID 定位）
        Block manifestBlock = findManifest(blocks);
        if (manifestBlock == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.NOT_A_VAULT);
        }
        // 2. 解析 manifest 负载（魔数/长度/JSON 解析失败视为 manifest 损坏）
        byte[] full;
        Manifest manifest;
        try {
            full = manifestBlock.unmasked();
            byte[] payload = com.flora.sanctum.core.model.impl.ManifestStore.payloadOf(full);
            manifest = Manifest.fromJson(payload);
        } catch (Exception e) {
            throw new VaultUnlockException(VaultUnlockException.Phase.MANIFEST_CORRUPT);
        }
        // 3. 派生 KEK
        byte[] salt = manifest.salt();
        Argon2KDF kdf = new Argon2KDF(salt, manifest.memoryKiB(), manifest.iterations(), manifest.parallelism());
        byte[] kek = kdf.derive(masterPassword);
        try {
            // 4. 验证 manifest MAC（覆盖完整信封头 + 时间戳原文 + 负载，尾附于块末）
            verifyMac(full, manifestBlock.timestampText(), manifest, kek);
        } catch (VaultUnlockException e) {
            java.util.Arrays.fill(kek, (byte) 0);
            throw e;
        }
        KeyIdIndex index = new KeyIdIndex();
        long baseTimestamp = maxBlockTimestamp(blocks);
        Vault vault = new Vault(store, manifest, index, new SecureRandomSource(), kek, baseTimestamp);
        // 5. 解根对象：manifest 记录 rootObjectUuid，O(1) 定位；KEK 试解出 root DEK 与 repoKeyIdSeed。
        //    根对象缺失/解不开/不完整均视为解锁失败（必要节点缺失），失败时清理驻留密钥。
        try {
            discoverRootDeks(vault, kek, blocks);
        } catch (VaultUnlockException e) {
            vault.clearSecrets();
            throw e;
        }
        // KEK 由 Vault 驻留（锁定/关闭时 clearSecrets）
        return vault;
    }

    /**
     * 发现并登记全部 group DEK（见设计 02"解锁流程"）。
     * 根对象由 manifest.rootObjectUuid 定位（O(1)），KEK 试解出 root DEK 与 repoKeyIdSeed 后，
     * 后续 cipher 块统一经 keyId 路由（BlockResolver）定位父 DEK 解开，对 type==group 且含 dek 的
     * 用父 DEK 解出子 DEK 并登记，逐层递归直至无新增。
     * <p>根对象缺失/无法解密/内容不完整时抛 {@link VaultUnlockException}（不再静默）。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        // 根对象块用 KEK 加密，但 keyId 由 repoKeyIdSeed 派生（解锁时尚未读出），
        // 无法用 keyId 预筛 → 直接按 manifest 记录的 uuid 定位，KEK 试解（GCM tag 确证）。
        java.util.UUID rootUuid = vault.manifest().rootObjectUuid();
        if (rootUuid == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_MISSING);
        }
        Block rootBlock = null;
        for (Block b : blocks) {
            if (rootUuid.equals(b.uuid())) {
                rootBlock = b;
                break;
            }
        }
        if (rootBlock == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_MISSING);
        }
        byte[] plain = tryDecode(vault, kek, rootBlock);
        if (plain == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_DECRYPT_FAILED);
        }
        com.flora.root.codec.json.model.JsonObject n = parsePlain(plain);
        if (n == null || n.getString("dek") == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
        }
        // 根对象：manifest 已记录 uuid 并定位，唯一根即 DATA
        vault.addRootObjectUuid(rootBlock.uuid());
        // 根对象承载仓库级 keyId 派生种子
        String seedB64 = n.getString("repoKeyIdSeed");
        if (seedB64 != null) {
            vault.setRepoKeyIdSeed(java.util.Base64.getDecoder().decode(seedB64));
        }
        byte[] wrapped = java.util.Base64.getDecoder().decode(n.getString("dek"));
        byte[] dek = unwrap(vault, kek, wrapped);
        if (dek == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_DECRYPT_FAILED);
        }
        vault.addRootDek(dek);
        // 逐层发现 group DEK：repoKeyIdSeed 已读出，cipher 块经 keyId 路由定位父 DEK 解开；
        // 父 DEK 必先于子块登记于 KeyIdIndex（树自顶向下展开），故 keyId 路由始终可命中。
        boolean any = true;
        while (any) {
            any = false;
            for (Block b : blocks) {
                if (!b.isCipher()) {
                    continue;
                }
                com.flora.sanctum.core.crypto.impl.BlockResolver.Decoded d =
                        vault.resolver().decodeKeyed(b.masked(), b.timestampText());
                if (d == null) {
                    continue;
                }
                try {
                    com.flora.root.codec.json.model.JsonObject gn = parsePlain(d.plaintext);
                    StoredNodeType nt = StoredNodeType.fromTag(gn == null ? null : gn.getString("type"));
                    if (nt == StoredNodeType.GROUP && gn.getString("dek") != null
                            && vault.groupDek(b.uuid()) == null) {
                        byte[] gwrapped = java.util.Base64.getDecoder().decode(gn.getString("dek"));
                        byte[] gdek = unwrap(vault, d.dek, gwrapped);
                        if (gdek != null) {
                            vault.addGroupDek(b.uuid(), gdek);
                            any = true;
                        }
                    }
                } catch (Exception ignore) {
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
            byte[] encK = com.flora.sanctum.core.crypto.KeyDerivation.encKey(dk);
            com.flora.sanctum.core.crypto.impl.CipherCodec gc = new com.flora.sanctum.core.crypto.impl.CipherCodec(encK, dk, vault.random());
            return gc.decode(b.masked(), b.timestampText()).plaintext;
        } catch (Exception e) {
            return null;
        }
    }

    private byte[] unwrap(Vault vault, byte[] parentDek, byte[] wrapped) {
        try {
            byte[] encK = com.flora.sanctum.core.crypto.KeyDerivation.encKey(parentDek);
            com.flora.sanctum.core.crypto.impl.CipherCodec gc = new com.flora.sanctum.core.crypto.impl.CipherCodec(encK, parentDek, vault.random());
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

    private Block findManifest(List<Block> blocks) {
        for (Block b : blocks) {
            if (b.isPlaintext() && Manifest.MANIFEST_UUID.equals(b.uuid())) {
                return b;
            }
        }
        return null;
    }

    private void verifyMac(byte[] full, String timestamp, Manifest m, byte[] kek) {
        byte[] macKey = m.manifestMacKey(kek);
        if (!com.flora.sanctum.core.model.impl.ManifestStore.verifyMac(full, timestamp, macKey)) {
            throw new VaultUnlockException(VaultUnlockException.Phase.MANIFEST_CORRUPT);
        }
    }
}
