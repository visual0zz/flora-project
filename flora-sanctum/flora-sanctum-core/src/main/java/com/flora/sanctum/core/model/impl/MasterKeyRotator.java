package com.flora.sanctum.core.model.impl;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.KeyDerivation;
import com.flora.sanctum.core.crypto.RootUuid;
import com.flora.sanctum.core.crypto.impl.CipherCodec;
import com.flora.sanctum.core.store.Block;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 换主密码：以新 KEK 迁移根对象与全部根级块、用新 KEK 重加密根块，并更新 manifest MAC。
 * <p>
 * 根对象 uuid 由 KEK 单向推导（见 {@link RootUuid#derive}），故换主密码后 KEK 变化会连带：
 * <ol>
 *   <li>根对象 uuid 改变 ⇒ 根对象块改写至新分片路径、旧路径删除；</li>
 *   <li>根对象本身仍直接以 KEK 加解密，其内嵌的 rootDek 明文值不变（根块整体改以新 KEK 加密）；</li>
 *   <li>顶层对象（parent 指向根对象 uuid）以 rootDek 加密（非 KEK），rootDek 值不变，
 *       故仅把 parent 改指新根 uuid 并以 rootDek 重写即可，无需用新 KEK 重加密；
 *       顶层 group 的 DEK 由 rootDek 加密（外层块），值不变。</li>
 * </ol>
 * 更深层级以分组 DEK 加解密、parent 指向分组 uuid，均不受密码轮换影响。
 */
public final class MasterKeyRotator {

    private final TreeContext ctx;

    public MasterKeyRotator(TreeContext ctx) {
        this.ctx = ctx;
    }

    public void rotate(char[] newPassword, int memoryKiB, int iterations, int parallelism) {
        Vault vault = ctx.vault();
        byte[] oldKek = vault.kek();
        // salt 终身不变；用传入的新 KDF 参数派生新 KEK（参数可升级）
        Manifest m = vault.manifest();
        Argon2KDF kdf = new Argon2KDF(m.salt(), memoryKiB, iterations, parallelism);
        byte[] newKek = kdf.derive(newPassword);
        try {
            UUID oldRootUuid = RootUuid.derive(oldKek);
            UUID newRootUuid = RootUuid.derive(newKek);
            // rootDek 明文值换主密码时不变：先取出，迁移全程复用同一值，结尾重挂到新根 uuid
            byte[] rootDek = vault.rootDek();
            if (rootDek == null) {
                throw new IllegalStateException("root DEK unavailable");
            }
            // 仓库级 keyId 种子（解锁时已在 vault 中），用于 keyId 派生；解码侧 keyId 取自块头，种子仅供编码
            byte[] seed = vault.repoKeyIdSeed();
            // 两个解码器：根对象块以 KEK 加密；顶层块（parent=根）以 rootDek 加密
            CipherCodec oldKekCodec = new CipherCodec(KeyDerivation.encKey(oldKek), oldKek, seed, ctx.random());
            CipherCodec oldRootDekCodec = new CipherCodec(KeyDerivation.encKey(rootDek), rootDek, seed, ctx.random());
            migrateRootObject(oldKekCodec, oldRootUuid, newRootUuid, newKek, rootDek);
            migrateRootLevelBlocks(oldRootDekCodec, oldRootUuid, newRootUuid, rootDek);
            // 更新 manifest 的 MAC（用新 KEK）；manifest 不记录根对象 uuid，无根相关字段需改
            Manifest updated = new Manifest(m.version(), m.crypto(), m.kdf(),
                    m.salt(), memoryKiB, iterations, parallelism);
            byte[] macKey = updated.manifestMacKey(newKek);
            new ManifestStore(ctx.store(), ctx.random()).write(updated, macKey,
                    Long.toString(ctx.nextTimestamp()));
            vault.replaceManifest(updated);
            vault.replaceKek(newKek);
            // 根级密钥仍即 KEK（用于加密 root 块）；rootDek 值不变，重挂到新根 uuid
            vault.addRootDek(newKek);
            vault.addRootObjectUuid(newRootUuid);
            vault.addGroupDek(newRootUuid, rootDek);
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }

    /** 把根对象从旧 uuid 路径迁移到新 uuid 路径（旧 KEK 解出、新 KEK 重写、删除旧块）。 */
    private void migrateRootObject(CipherCodec oldCodec, UUID oldRootUuid, UUID newRootUuid, byte[] newKek, byte[] rootDek) {
        Block rootBlock = null;
        for (Block b : ctx.store().scan()) {
            if (oldRootUuid.equals(b.uuid())) {
                rootBlock = b;
                break;
            }
        }
        if (rootBlock == null) {
            throw new IllegalStateException("root object not found");
        }
        byte[] plain = oldCodec.decode(rootBlock.masked(), oldRootUuid, rootBlock.timestampText());
        JsonObject n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
        // rootDek 明文值不变；根块整体改以新 KEK 加密（外层保护），dek 字段直接存明文 base64
        n.put("dek", Base64.getEncoder().encodeToString(rootDek));
        ctx.writeWithDek(newRootUuid, n, newKek);
        if (!newRootUuid.equals(oldRootUuid)) {
            ctx.delete(oldRootUuid);
        }
    }

    /**
     * 迁移根级块（parent 指向旧根 uuid）：parent 改指新根 uuid、以 rootDek 重写（rootDek 值不变）。
     * 顶层 group 的 dek 字段存明文 DEK（值不变），直接以 rootDek 重写即可。
     * 非根级块（以分组 DEK 加密、parent 指向分组 uuid）无法用 rootDek 解开，自然跳过。
     */
    private void migrateRootLevelBlocks(CipherCodec codec, UUID oldRootUuid, UUID newRootUuid, byte[] dek) {
        String oldRootStr = oldRootUuid.toString();
        for (Block b : new ArrayList<>(ctx.store().scan())) {
            if (!b.isCipher()) {
                continue;
            }
            byte[] plain;
            try {
                plain = codec.decode(b.masked(), b.uuid(), b.timestampText());
            } catch (Exception e) {
                continue; // 非以 rootDek 加密的根级块（深层块/根对象外的其它）
            }
            JsonObject n;
            try {
                n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
            } catch (Exception e) {
                continue;
            }
            String parent = n.getString("parent");
            if (parent == null || !oldRootStr.equals(parent)) {
                continue; // 非顶层块
            }
            n.put("parent", newRootUuid.toString());
            // dek 字段（如顶层 group）存明文 DEK，值不变，无需重写；直接以 rootDek 重写
            ctx.writeWithDek(b.uuid(), n, dek);
        }
    }
}
