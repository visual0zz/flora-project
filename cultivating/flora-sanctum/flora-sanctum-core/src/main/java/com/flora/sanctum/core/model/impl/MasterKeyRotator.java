package com.flora.sanctum.core.model.impl;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.crypto.Argon2KDF;
import com.flora.sanctum.core.store.Block;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 换主密码：以新 KEK 重加密根对象块并更新 manifest MAC（见设计 02"换主密码"）。
 * <p>
 * 新格式根对象 uuid 随机、不依赖主密码，故换密码只改变根块的 KEK 加密层，根对象路径与
 * 内嵌的 rootDek 对（dek1/dek2）明文值均不变；顶层对象（parent 指向根对象 uuid）以活跃 rootDek
 * （dek2）加密（非 KEK），rootDek 与父指均不变，无需重写。更深层级以分组 DEK 加解密、parent 指向
 * 分组 uuid，均不受密码轮换影响。
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
            // rootDek 对（dek1/dek2）明文值换主密码时不变：先取出，迁移全程复用同一对，结尾重挂到根 uuid
            Vault.GroupKeys rk = vault.groupKeys(vault.rootObjectUuid());
            if (rk == null) {
                throw new IllegalStateException("root DEK unavailable");
            }
            // 仓库级 keyId 种子（解锁时已在 vault 中），用于 keyId 派生；解码侧 keyId 取自块头，种子仅供编码。
            // 换新主密码时种子值不变，仅改用新 KEK 加密后写入 manifest 的 seed 字段。
            byte[] seed = vault.repoKeyIdSeed();

            // 新格式：根对象 uuid 随机、不依赖主密码，换密码不变；仅根块以新 KEK 重加密（同 uuid 覆盖）。
            // 顶层块 parent=根 uuid、以 rootDek 加密，二者均不变 ⇒ 无需重写。
            UUID rootUuid = vault.rootObjectUuid();
            migrateRootObject(rootUuid, rootUuid, newKek, rk);

            // 更新 manifest：新 KDF 参数 + seed（用新 KEK 加密）；MAC 用新 KEK 重算
            Manifest updated = new Manifest(m.version(), m.crypto(), m.kdf(),
                    m.salt(), memoryKiB, iterations, parallelism,
                    ManifestStore.encryptSeed(seed, newKek));
            byte[] macKey = updated.manifestMacKey(newKek);
            // manifest 经 ManifestStore 直接 store.put 落盘（绕过 writeCipherBlock），需回写时间戳上限，
            // 否则缓存会低于该块时间戳，后续写入可能复用同一时间戳。
            long manifestTs = ctx.nextTimestamp();
            new ManifestStore(ctx.store(), ctx.random()).write(updated, macKey, Long.toString(manifestTs));
            ctx.noteTimestamp(manifestTs);
            vault.replaceManifest(updated);
            vault.replaceKek(newKek);
            // 根级密钥仍即 KEK（用于加密 root 块）；rootDek 对值不变，重挂到根 uuid
            vault.addRootDek(newKek);
            vault.addRootObjectUuid(rootUuid);
            vault.addGroupDek(rootUuid, rk.dek1(), rk.dek2());
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

    private Vault vault() {
        return ctx.vault();
    }
}
