package com.flora.sanctum.core.model.impl;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.crypto.RootUuid;
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
 *   <li>根对象本身仍直接以 KEK 加解密，其内嵌的 rootDek 对（dek1/dek2）明文值不变
 *       （根块整体改以新 KEK 加密）；</li>
 *   <li>顶层对象（parent 指向根对象 uuid）以活跃 rootDek（dek2）加密（非 KEK），rootDek 值不变，
 *       故仅把 parent 改指新根 uuid 并以活跃 rootDek 重写即可，无需用新 KEK 重加密；
 *       顶层 group 的 DEK 对由活跃 rootDek 加密（外层块），值不变。</li>
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
            // rootDek 对（dek1/dek2）明文值换主密码时不变：先取出，迁移全程复用同一对，结尾重挂到新根 uuid
            Vault.GroupKeys rk = vault.groupKeys(vault.rootObjectUuid());
            if (rk == null) {
                throw new IllegalStateException("root DEK unavailable");
            }
            // 仓库级 keyId 种子（解锁时已在 vault 中），用于 keyId 派生；解码侧 keyId 取自块头，种子仅供编码
            byte[] seed = vault.repoKeyIdSeed();
            // 两个解码器：根对象块以 KEK 加密；顶层块（parent=根）以 rootDek 加密
            migrateRootObject(oldRootUuid, newRootUuid, newKek, rk);
            migrateRootLevelBlocks(oldRootUuid, newRootUuid);
            // 更新 manifest 的 MAC（用新 KEK）；manifest 不记录根对象 uuid，无根相关字段需改
            Manifest updated = new Manifest(m.version(), m.crypto(), m.kdf(),
                    m.salt(), memoryKiB, iterations, parallelism);
            byte[] macKey = updated.manifestMacKey(newKek);
            new ManifestStore(ctx.store(), ctx.random()).write(updated, macKey,
                    Long.toString(ctx.nextTimestamp()));
            vault.replaceManifest(updated);
            vault.replaceKek(newKek);
            // 根级密钥仍即 KEK（用于加密 root 块）；rootDek 对值不变，重挂到新根 uuid
            vault.addRootDek(newKek);
            vault.addRootObjectUuid(newRootUuid);
            vault.addGroupDek(newRootUuid, rk.dek1(), rk.dek2());
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }

    /** 把根对象从旧 uuid 路径迁移到新 uuid 路径（旧 KEK 解出、新 KEK 重写、删除旧块）。 */
    private void migrateRootObject(UUID oldRootUuid, UUID newRootUuid, byte[] newKek, Vault.GroupKeys rk) {
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
        byte[] plain = vault().resolve(rootBlock.masked(), oldRootUuid, rootBlock.timestampText());
        if (plain == null) {
            throw new IllegalStateException("root object undecryptable");
        }
        JsonObject n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
        // rootDek 对明文值不变；根块整体改以新 KEK 加密（外层保护），dek1/dek2 字段直接存明文 base64
        n.put("dek1", Base64.getEncoder().encodeToString(rk.dek1()));
        n.put("dek2", Base64.getEncoder().encodeToString(rk.dek2()));
        n.remove("dek");
        ctx.writeWithDek(newRootUuid, n, newKek);
        if (!newRootUuid.equals(oldRootUuid)) {
            ctx.delete(oldRootUuid);
        }
    }

    /**
     * 迁移根级块（parent 指向旧根 uuid）：parent 改指新根 uuid、以活跃 rootDek 重写（rootDek 值不变）。
     * 以 vault.resolve 解码（尝试全部已登记 DEK，兼容 root 双 dek）；非根级块（以分组 DEK 加密）
     * 自然跳过滤过。用 writeWithDek 重写以避免触发组密钥轮换（换密码期间 KEK 尚未完全切换）。
     */
    private void migrateRootLevelBlocks(UUID oldRootUuid, UUID newRootUuid) {
        String oldRootStr = oldRootUuid.toString();
        byte[] rootDek = vault().rootDek();
        for (Block b : new ArrayList<>(ctx.store().scan())) {
            if (!b.isCipher()) {
                continue;
            }
            byte[] plain = vault().resolve(b.masked(), b.uuid(), b.timestampText());
            if (plain == null) {
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
            // dek1/dek2 字段（如顶层 group）存明文 DEK，值不变，无需重写；以活跃 rootDek 重写
            ctx.writeWithDek(b.uuid(), n, rootDek);
        }
    }

    private Vault vault() {
        return ctx.vault();
    }
}
