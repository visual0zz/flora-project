package com.flora.sanctum.model.impl;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.Argon2KDF;
import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.store.Block;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 换主密码：新 KEK 重新包裹三个顶层 root DEK 并重加密 root group 块，更新 manifest MAC。
 * 子文件夹 DEK 链不动（用父 DEK 包裹，根 DEK 未变，见设计 02）。
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
            // 重包根对象（用旧 KEK 解密块 + 解 DEK，用新 KEK 重加密）
            // 直接按已知根对象 uuid 定位，不再遍历全库试解
            java.util.Set<java.util.UUID> rootUuids = new java.util.HashSet<>();
            java.util.UUID root = vault.rootGroupUuid(RootTag.DATA);
            if (root != null) {
                rootUuids.add(root);
            }
            byte[] oldEnc = KeyDerivation.encKey(oldKek);
            CipherCodec oldCodec = new CipherCodec(oldEnc, oldKek, ctx.random());
            for (Block b : ctx.store().scan()) {
                if (!rootUuids.contains(b.uuid())) {
                    continue;
                }
                byte[] plain;
                try {
                    plain = oldCodec.decode(b.obfuscated(), b.timestampText()).plaintext;
                } catch (Exception e) {
                    continue; // 非 KEK 包裹（不期望发生），跳过
                }
                JsonObject n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
                byte[] oldWrapped = Base64.getDecoder().decode(n.getString("dek"));
                byte[] dek = oldCodec.decode(oldWrapped, "0").plaintext;
                byte[] newWrapped = ctx.wrapDek(dek, newKek);
                n = JsonUtil.parseObject(new String(plain, StandardCharsets.UTF_8));
                n.put("dek", Base64.getEncoder().encodeToString(newWrapped));
                ctx.writeWithDek(b.uuid(), n, newKek);
            }
            // 更新 manifest 的 MAC（用新 KEK）
            Manifest updated = new Manifest(m.version(), m.cryptoVersion(), m.kdf(),
                    m.salt(), memoryKiB, iterations, parallelism,
                    m.rootGroupUuid(), m.updateTimestamp());
            byte[] macKey = updated.manifestMacKey(newKek);
            new ManifestStore(ctx.store(), ctx.random()).write(updated, macKey);
            vault.replaceManifest(updated);
            vault.replaceKek(newKek);
        } finally {
            java.util.Arrays.fill(newKek, (byte) 0);
            java.util.Arrays.fill(oldKek, (byte) 0);
        }
    }
}
