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
 * 验证 manifest MAC → 构建 Vault。根密钥与 group/entry DEK 由本类内部发现并登记：
 * 种子优先取自 manifest 的 seed 字段（KEK 解密）；根对象为随机 uuid、不可定位，扫描全部块经
 * keyId 命中 type==root 定位，本身以 KEK 解密；其余 cipher 块经 keyId 路由定位父 DEK 解开。
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
     * <p>流程：扫描块 → 找 manifest → Argon2id 派生 KEK → 验证 manifest MAC → 发现根/组 DEK 构建 Vault。
     * 会话期内已缓存 KEK 时，用 {@link #unlockWithKek(byte[])} 跳过昂贵的 Argon2 派生。
     */
    public Vault unlock(char[] masterPassword) {
        LocatedManifest m = locateManifest();
        byte[] kek = deriveKek(m.manifest, masterPassword);
        try {
            return buildVault(m, kek);
        } catch (VaultUnlockException e) {
            // kek 为本方法局部派生，失败时立即清零，避免主密码残留于内存
            java.util.Arrays.fill(kek, (byte) 0);
            throw e;
        }
    }

    /**
     * 用已派生的 KEK 解锁（跳过 Argon2 派生），供会话期内缓存 KEK 后重开仓库（如云同步后重建会话）复用，
     * 避免重复昂贵的 Argon2 计算。失败按阶段抛 {@link VaultUnlockException}（含失败阶段）。
     * <p>传入的 kek 由调用方持有与管理（本方法不会清零它）；调用方应在解锁失败时自行处置缓存。
     */
    public Vault unlockWithKek(byte[] kek) {
        if (kek == null) {
            throw new IllegalArgumentException("kek is null");
        }
        LocatedManifest m = locateManifest();
        return buildVault(m, kek);
    }

    /** 定位并解析 manifest 引导块（扫描一次，顺便保留全块列表供后续发现 keyId 路由使用）。 */
    private LocatedManifest locateManifest() {
        List<Block> blocks = store.scan();
        Block manifestBlock = findManifest(blocks);
        if (manifestBlock == null) {
            throw new VaultUnlockException(VaultUnlockException.Phase.NOT_A_VAULT);
        }
        byte[] full;
        Manifest manifest;
        try {
            full = manifestBlock.unmasked();
            byte[] payload = com.flora.sanctum.core.model.impl.ManifestStore.payloadOf(full);
            manifest = Manifest.fromJson(payload);
        } catch (Exception e) {
            throw new VaultUnlockException(VaultUnlockException.Phase.MANIFEST_CORRUPT);
        }
        return new LocatedManifest(blocks, manifestBlock, full, manifest);
    }

    /** Argon2id 派生 KEK（见设计 02"解锁流程"步骤 3）。 */
    private byte[] deriveKek(Manifest manifest, char[] masterPassword) {
        byte[] salt = manifest.salt();
        Argon2KDF kdf = new Argon2KDF(salt, manifest.memoryKiB(), manifest.iterations(), manifest.parallelism());
        return kdf.derive(masterPassword);
    }

    /** 用 KEK 完成校验与密钥发现，构建并返回 Vault（manifest 已定位）。 */
    private Vault buildVault(LocatedManifest m, byte[] kek) {
        // 验证 manifest MAC（覆盖完整信封头 + 时间戳原文 + 负载，尾附于块末）
        verifyMac(com.flora.sanctum.core.crypto.impl.CipherCodec.uuidBytes(m.manifestBlock.uuid()),
                m.full, m.manifestBlock.timestampText(), m.manifest, kek);
        KeyIdIndex index = new KeyIdIndex();
        long baseTimestamp = maxBlockTimestamp(m.blocks);
        Vault vault = new Vault(store, m.manifest, index, new SecureRandomSource(), kek, baseTimestamp);
        // 发现根对象与全部 group/entry DEK：种子优先取自 manifest 的 seed 字段（KEK 解密），
        // 根对象（随机 uuid、不可定位）扫描全部块经 keyId 命中 type==root 定位。
        // 根对象缺失/解不开/不完整均视为解锁失败（必要节点缺失），失败时清理驻留密钥。
        try {
            discoverRootDeks(vault, kek, m);
        } catch (VaultUnlockException e) {
            vault.clearSecrets();
            throw e;
        }
        // KEK 由 Vault 驻留（锁定/关闭时 clearSecrets）
        return vault;
    }

    /** manifest 定位结果（块列表 + 引导块 + 明文完整块 + 解析后 manifest）。 */
    private record LocatedManifest(List<Block> blocks, Block manifestBlock, byte[] full, Manifest manifest) {
    }

    /**
     * 发现并登记根密钥与全部 group / entry DEK（见设计"root DEK"）。
     * <p>种子来源：新格式自 manifest 的 {@code seed} 字段（经 KEK 加密，解锁时由 KEK 解密）；
     * 旧格式 manifest 无 seed，回退到根对象块内（kek 直解）。根对象 uuid 新格式为随机、不可定位，
     * 扫描全部块经 keyId 命中 {@code type==root} 即定位；旧格式仍由 KEK 单向推导定位。
     * 根级密钥即 KEK（dataDek），用于加密 root 块本身，登记进 keyId 索引后，后续 cipher 块统一经
     * keyId 路由（BlockResolver）定位父 DEK 解开，对 type==group / type==entry 且含 dek 的
     * 取块内明文子 DEK（外层块已由父 DEK 加密保护）并登记，逐层递归直至无新增。
     * <p>根对象缺失/无法解密/内容不完整（缺 dek）时抛 {@link VaultUnlockException}。
     */
    private void discoverRootDeks(Vault vault, byte[] kek, LocatedManifest m) {
        List<Block> blocks = m.blocks();
        Manifest manifest = m.manifest();
        // 1) 取得 repoKeyIdSeed：新格式自 manifest.seed（KEK 解密）；旧格式为 null，回退根对象块内。
        byte[] seed = null;
        if (manifest.encryptedSeed() != null) {
            try {
                seed = com.flora.sanctum.core.model.impl.ManifestStore.decryptSeed(manifest.encryptedSeed(), kek);
            } catch (Exception ignore) {
                seed = null; // 解密失败（如种子被篡改）→ 视为旧格式/损坏，下方走 kek 直解或报缺
            }
        }
        if (seed != null) {
            vault.setRepoKeyIdSeed(seed);
        }
        // 2) 登记 KEK：根对象块以 KEK 加密，其 keyId = makeKeyId(seed, nonce, kek)，
        //    需先将 kek 注册进 keyId 索引，扫描时方能经 keyId 命中根对象。
        vault.addRootDek(kek);

        if (seed != null) {
            // 新格式：根对象随机 uuid、不可定位，扫描全部块经 keyId 解出 type==root 即定位。
            for (Block b : blocks) {
                if (!b.isCipher()) {
                    continue;
                }
                com.flora.sanctum.core.crypto.impl.BlockResolver.Decoded d =
                        vault.resolver().decodeKeyed(b.masked(), b.uuid(), b.timestampText());
                if (d == null) {
                    continue;
                }
                com.flora.root.codec.json.model.JsonObject n = parsePlain(d.plaintext);
                if (n != null && StoredNodeType.ROOT == StoredNodeType.fromTag(n.getString("type"))) {
                    Vault.GroupKeys rk = readGroupKeys(n);
                    if (rk == null) {
                        throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
                    }
                    vault.addRootObjectUuid(b.uuid());
                    vault.addGroupDek(b.uuid(), rk.dek1(), rk.dek2());
                    break;
                }
            }
            if (vault.rootObjectUuid() == null) {
                throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_MISSING);
            }
        } else {
            // 旧格式：根对象 uuid 由 KEK 推导、块以 kek 直解，种子存于根对象块内。
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
            if (n == null || n.getString("repoKeyIdSeed") == null) {
                throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
            }
            vault.setRepoKeyIdSeed(java.util.Base64.getDecoder().decode(n.getString("repoKeyIdSeed")));
            Vault.GroupKeys rk = readGroupKeys(n);
            if (rk == null) {
                throw new VaultUnlockException(VaultUnlockException.Phase.ROOT_INCOMPLETE);
            }
            vault.addRootObjectUuid(rootUuid);
            vault.addGroupDek(rootUuid, rk.dek1(), rk.dek2());
        }

        // 3) 逐层发现 group / entry DEK：repoKeyIdSeed 已读出，cipher 块经 keyId 路由定位父 DEK 解开；
        //    父 DEK 必先于子块登记于 KeyIdIndex（树自顶向下展开），故 keyId 路由始终可命中。
        //    entry 亦为密钥持有者（持 dek1/dek2），其字段块经 entry DEK 加密，keyId 指向父（条目）的密钥。
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
                    if (nt == StoredNodeType.GROUP || nt == StoredNodeType.ENTRY) {
                        // 组/条目块整体以父 DEK 加密（外层保护），dek1/dek2 字段直接存明文 base64
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
