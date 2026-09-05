package com.flora.sanctum.core.model.vault;
import com.flora.sanctum.core.model.*;

import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.RootUuid;
import com.flora.sanctum.core.crypto.impl.KeyIdIndex;
import com.flora.sanctum.core.crypto.impl.SecureRandomSource;
import com.flora.sanctum.core.store.Block;
import com.flora.sanctum.core.store.ObjectStore;

import java.util.List;

/**
 * 库解锁器（见设计 02"解锁流程"）。
 * <p>
 * 流程：扫描块 → 找 manifest（明文块，type=manifest）→ Argon2id 派生 KEK →
 * 验证 manifest MAC → 构建 Vault。根密钥与 group DEK 由本类内部发现并登记：
 * 根对象 uuid 由 KEK 单向推导（见 {@link com.flora.sanctum.core.crypto.RootUuid}）后直接定位，
 * 根对象本身以 KEK 解密并取出 repoKeyIdSeed；其余 cipher 块经 keyId 路由定位父 DEK 解开。
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
        // 1. 找 manifest 明文块（扫描全部明文块，按 type=="manifest" 识别）
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
            verifyMac(com.flora.sanctum.core.crypto.impl.CipherCodec.uuidBytes(manifestBlock.uuid()),
                    full, manifestBlock.timestampText(), manifest, kek);
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
     * 发现并登记根密钥与全部 group DEK（见设计"root DEK"）。
     * 根对象 uuid 由 KEK 单向推导定位（O(1)），根对象直接以 KEK 解密，取出 repoKeyIdSeed 与
     * 明文 rootDek；rootDek 注册为 {@code groupDek(rootUuid)}，作为顶层子树加密根。
     * 根级密钥仍即 KEK（dataDek），用于加密 root 块本身；登记进 keyId 索引后，
     * 后续 cipher 块统一经 keyId 路由（BlockResolver）定位父 DEK 解开，对 type==group 且含 dek 的
     * 取组块内明文子 DEK（外层块已由父 DEK 加密保护）并登记，逐层递归直至无新增。
     * <p>根对象缺失/无法解密/内容不完整（缺 repoKeyIdSeed 或 dek）时抛 {@link VaultUnlockException}。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, List<Block> blocks) {
        // 根对象块用 KEK 加密，但 keyId 由 repoKeyIdSeed 派生（解锁时尚未读出），
        // 无法用 keyId 预筛 → 按 KEK 推导出的 uuid 定位，KEK 试解（GCM tag 确证）。
        java.util.UUID rootUuid = RootUuid.derive(kek);
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
        // 根对象以 KEK 加解密；必要内容是仓库级 keyId 派生种子与明文 rootDek 对（dek1/dek2）
        if (n == null || n.getString("repoKeyIdSeed") == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
        }
        Vault.GroupKeys rk = readGroupKeys(n);
        if (rk == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
        }
        vault.addRootObjectUuid(rootUuid);
        vault.setRepoKeyIdSeed(java.util.Base64.getDecoder().decode(n.getString("repoKeyIdSeed")));
        // dataDek 仍是 KEK（用于加密 root 块）；rootDek 对明文解出后注册为 groupDek(rootUuid)，
        // 顶层对象与顶层分组 DEK 的加密改由活跃 rootDek(dek2) 承担
        vault.addRootDek(kek);
        vault.addGroupDek(rootUuid, rk.dek1(), rk.dek2());
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
                        vault.resolver().decodeKeyed(b.masked(), b.uuid(), b.timestampText());
                if (d == null) {
                    continue;
                }
                try {
                    com.flora.root.codec.json.model.JsonObject gn = parsePlain(d.plaintext);
                    StoredNodeType nt = StoredNodeType.fromTag(gn == null ? null : gn.getString("type"));
                    if (nt == StoredNodeType.GROUP) {
                        // 组块整体以父 DEK 加密（外层保护），dek1/dek2 字段直接存明文 base64
                        Vault.GroupKeys gk = readGroupKeys(gn);
                        if (gk != null && vault.groupKeys(b.uuid()) == null) {
                            vault.addGroupDek(b.uuid(), gk.dek1(), gk.dek2());
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

    /**
     * 从组/根对象 JSON 读取密钥对：新格式取 dek1/dek2；旧格式单 dek 则 dek1==dek2==dek。
     * 两者均缺失返回 null（必要字段不完整）。
     */
    private static Vault.GroupKeys readGroupKeys(com.flora.root.codec.json.model.JsonObject gn) {
        String s1 = gn.getString("dek1");
        String s2 = gn.getString("dek2");
        if (s1 != null && s2 != null) {
            return new Vault.GroupKeys(
                    java.util.Base64.getDecoder().decode(s1),
                    java.util.Base64.getDecoder().decode(s2));
        }
        String sd = gn.getString("dek"); // 旧格式单 dek 回退
        if (sd == null) {
            return null;
        }
        byte[] d = java.util.Base64.getDecoder().decode(sd);
        return new Vault.GroupKeys(d, d);
    }

    private byte[] tryDecode(Vault vault, byte[] dk, Block b) {
        try {
            byte[] encK = com.flora.sanctum.core.crypto.KeyDerivation.encKey(dk);
            com.flora.sanctum.core.crypto.impl.CipherCodec gc = new com.flora.sanctum.core.crypto.impl.CipherCodec(encK, dk, vault.random());
            return gc.decode(b.masked(), b.uuid(), b.timestampText());
        } catch (Exception e) {
            return null;
        }
    }

    /** 全库块时间戳上限（见设计 02"仓库时间戳"）。仅取已落盘块的最大值，
     *  具体锚点（与当前时间取大、并封顶）由 {@link WarehouseClock} 在构造时与 startNanos 同源计算。 */
    private static long maxBlockTimestamp(List<Block> blocks) {
        long max = 1;
        for (Block b : blocks) {
            if (b.timestamp() > max) {
                max = b.timestamp();
            }
        }
        return max;
    }

    /**
     * 扫描全部明文块定位 manifest 引导块（按负载 {@code type=="manifest"} 识别，无特殊 uuid）。
     * 不存在返回 null。
     */
    private Block findManifest(List<Block> blocks) {
        for (Block b : blocks) {
            if (!b.isPlaintext()) {
                continue;
            }
            try {
                byte[] payload = com.flora.sanctum.core.model.impl.ManifestStore.payloadOf(b.unmasked());
                com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                        new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                if (StoredNodeType.MANIFEST == StoredNodeType.fromTag(n.getString("type"))) {
                    return b;
                }
            } catch (Exception ignore) {
                // 非 manifest 明文块或损坏块，跳过
            }
        }
        return null;
    }

    private void verifyMac(byte[] uuidBytes, byte[] full, String timestamp, Manifest m, byte[] kek) {
        byte[] macKey = m.manifestMacKey(kek);
        if (!com.flora.sanctum.core.model.impl.ManifestStore.verifyMac(uuidBytes, full, timestamp, macKey)) {
            throw new VaultUnlockException(VaultUnlockException.Phase.MANIFEST_CORRUPT);
        }
    }
}
