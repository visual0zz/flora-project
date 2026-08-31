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
 * 换主密码：以新 KEK 迁移根对象与全部根级块、重包根级分组 DEK，并更新 manifest MAC。
 * <p>
 * 根对象直接以 KEK 加解密，且其 uuid 由 KEK 单向推导（见 {@link RootUuid#derive}），
 * 因此换主密码后 KEK 变化会连带三件事：
 * <ol>
 *   <li>根对象 uuid 改变 ⇒ 根对象块改写至新分片路径、旧路径删除；</li>
 *   <li>根级密钥即 KEK、无独立根 DEK 可供"只换包裹" ⇒ 所有以根对象为 parent 的块
 *       都要以新 KEK 重新加密，并把 parent 改指新的根对象 uuid；</li>
 *   <li>根级分组的 DEK 原由旧 KEK 包裹 ⇒ 改用新 KEK 重新包裹。</li>
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
            byte[] oldEnc = KeyDerivation.encKey(oldKek);
            // 解旧块用的编解码器：根块与根级块都以 KEK 加密，无需 keyId 路由
            CipherCodec oldCodec = new CipherCodec(oldEnc, oldKek, ctx.random());
            migrateRootObject(oldCodec, oldRootUuid, newRootUuid, newKek);
            migrateRootLevelBlocks(oldCodec, oldRootUuid, newRootUuid, newKek);
            // 更新 manifest 的 MAC（用新 KEK）；manifest 不记录根对象 uuid，无根相关字段需改
            Manifest updated = new Manifest(m.version(), m.cryptoVersion(), m.kdf(),
                    m.salt(), memoryKiB, iterations, parallelism, m.updateTimestamp());
            byte[] macKey = updated.manifestMacKey(newKek);
            new ManifestStore(ctx.store(), ctx.random()).write(updated, macKey);
            vault.replaceManifest(updated);
            vault.replaceKek(newKek);
            // 根级密钥即 KEK：换 KEK 后同步登记（含 keyId 索引），根对象 uuid 也随之更新
            vault.addRootDek(newKek);
            vault.addRootObjectUuid(newRootUuid);
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }

    /** 把根对象从旧 uuid 路径迁移到新 uuid 路径（旧 KEK 解出、新 KEK 重写、删除旧块）。 */
    private void migrateRootObject(CipherCodec oldCodec, UUID oldRootUuid, UUID newRootUuid, byte[] newKek) {
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
        ctx.writeWithDek(newRootUuid, n, newKek);
        if (!newRootUuid.equals(oldRootUuid)) {
            ctx.delete(oldRootUuid);
        }
    }

    /**
     * 迁移根级块（parent 指向旧根 uuid）：parent 改指新根 uuid、以新 KEK 重新加密；
     * 根级分组还要把其 DEK 的包裹从旧 KEK 换成新 KEK。
     * 非根级块（以分组 DEK 加密）无法用旧 KEK 解开，自然跳过。
     */
    private void migrateRootLevelBlocks(CipherCodec oldCodec, UUID oldRootUuid, UUID newRootUuid, byte[] newKek) {
        String oldRootStr = oldRootUuid.toString();
        for (Block b : new ArrayList<>(ctx.store().scan())) {
            if (!b.isCipher()) {
                continue;
            }
            byte[] plain;
            try {
                plain = oldCodec.decode(b.masked(), b.uuid(), b.timestampText());
            } catch (Exception e) {
                continue; // 不是以 KEK 加密的根级块
            }
            JsonObject n;
            try {
                n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
            } catch (Exception e) {
                continue;
            }
            String parent = n.getString("parent");
            if (parent == null || !oldRootStr.equals(parent)) {
                continue; // 不是根级块
            }
            n.put("parent", newRootUuid.toString());
            String dekB64 = n.getString("dek");
            if (dekB64 != null) {
                byte[] groupDek = oldCodec.decode(Base64.getDecoder().decode(dekB64),
                        CipherCodec.EMBEDDED_UUID, "0");
                n.put("dek", Base64.getEncoder().encodeToString(ctx.wrapDek(groupDek, newKek)));
            }
            ctx.writeWithDek(b.uuid(), n, newKek);
        }
    }
}
