package com.flora.sanctum.model;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.KeyDerivation;
import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.KeyIdIndex;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.BlockFormat;
import com.flora.sanctum.store.BlockHeader;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 外部密钥加解密服务（见设计 02"外部密钥 = 字段 kind"）。
 * <p>
 * 外部密钥 = 某条目下 {@code kind:"externalKey"} 的字段，value 即密钥材料（base64）。
 * - {@code encrypt(data, fieldUuid)}：用该字段密钥加密，返回标准密文块（含异或混淆）base58。
 * - {@code decrypt(cipherBase58)}：不传 uuid，靠密文头 keyId 在 externalKey 密钥索引中定位候选，
 *   再以 GCM-SIV tag 试解密确证（与系统块同一 keyId 定位机制，见设计 02"可定位"）。
 * - {@code list()}：列出 externalKey 字段的 uuid + 描述。
 * <p>
 * 防泄漏：解密候选域 = 仅 {@code kind:"externalKey"} 的字段密钥（懒构建 keyId 索引），
 * 系统 DEK 不在候选里，外部即使传入系统块密文也解不出（见设计 02"隔离与防泄漏"）。
 */
public final class ExternalKeyService {

    private final Sanctum sanctum;
    /** externalKey 密钥材料的 keyId 索引（懒构建；设计 02"可定位"机制）。 */
    private final KeyIdIndex keyIndex = new KeyIdIndex();
    private boolean indexed = false;

    public ExternalKeyService(Sanctum sanctum) {
        this.sanctum = sanctum;
    }

    /** 列出 externalKey 字段（uuid + 名称 + 描述）。 */
    public List<KeyInfo> list() {
        ensureIndex();
        List<KeyInfo> out = new ArrayList<>();
        for (Block b : sanctum.store().scan()) {
            JsonObject n = readNode(b);
            if (n == null || !"externalKey".equals(n.getString("kind"))) {
                continue;
            }
            String description = n.getString("description");
            out.add(new KeyInfo(b.uuid(), n.getString("fieldName"), description == null ? "" : description));
        }
        return out;
    }

    /** 用指定外部密钥加密。 */
    public byte[] encrypt(byte[] data, UUID fieldUuid) {
        byte[] keyMaterial = externalKeyMaterial(fieldUuid);
        byte[] encKey = KeyDerivation.encKey(keyMaterial);
        CipherCodec codec = new CipherCodec(encKey, keyMaterial, sanctum.vault().random());
        return codec.encode(fieldUuid, data, codec.makeKeyIdWith(keyMaterial));
    }

    /** 解密：靠密文头 keyId 在 externalKey 密钥索引定位候选，再 tag 试解确证（不遍历全部密钥）。 */
    public byte[] decrypt(String cipherBase58) {
        byte[] obfuscated;
        try {
            obfuscated = com.flora.root.codec.Base58.decode(cipherBase58);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid base58");
        }
        byte[] block = BlockHeader.deobfuscate(obfuscated);
        int keyIdOff = BlockFormat.MAGIC_LEN + 2 + 16;
        if (block.length < keyIdOff + 4 || !BlockHeader.isBlock(block)) {
            throw new IllegalArgumentException("not a block");
        }
        byte[] keyId = new byte[4];
        System.arraycopy(block, keyIdOff, keyId, 0, 4);
        ensureIndex();
        for (byte[] keyMaterial : keyIndex.lookup(keyId)) {
            byte[] encKey = KeyDerivation.encKey(keyMaterial);
            CipherCodec codec = new CipherCodec(encKey, keyMaterial, sanctum.vault().random());
            try {
                return codec.decode(obfuscated).plaintext;
            } catch (IllegalStateException ignore) {
                // tag 不符 → 试下一个候选（同 keyId 碰撞）
            }
        }
        throw new IllegalArgumentException("decrypt failed");
    }

    /** 在某个条目下创建外部密钥字段。 */
    public UUID createExternalKey(UUID entryUuid, String fieldName, byte[] keyMaterial, String description) {
        UUID fieldUuid = UUID.randomUUID();
        JsonObject field = new JsonObject();
        field.put("version", 1);
        field.put("type", "field");
        field.put("parent", entryUuid.toString());
        field.put("fieldName", fieldName);
        field.put("kind", "externalKey");
        field.put("value", Base64.getEncoder().encodeToString(keyMaterial));
        if (description != null) {
            field.put("description", description);
        }
        // 外部密钥是条目下的字段，属普通对象树 → 用 data 根 DEK 加密、仓库时间戳（经 TreeContext）
        byte[] dek = sanctum.vault().dekForRole(RootTag.DATA);
        TreeContext ctx = sanctum.objectTree().context();
        field.put("updateTimestamp", ctx.nextTimestamp());
        ctx.writeWithDek(fieldUuid, field, dek);
        keyIndex.register(keyMaterial); // 新密钥立即入索引，无需全量重建
        return fieldUuid;
    }

    /**
     * 懒构建 externalKey 密钥索引：首次加密/解密/列表前扫描库中 externalKey 字段，
     * 取各自密钥材料登记进 keyId 索引（设计 02"可定位"）。字段增删后调用方重新触发。
     */
    private void ensureIndex() {
        if (indexed) {
            return;
        }
        for (Block b : sanctum.store().scan()) {
            JsonObject n = readNode(b);
            if (n != null && "externalKey".equals(n.getString("kind"))) {
                keyIndex.register(Base64.getDecoder().decode(n.getString("value")));
            }
        }
        indexed = true;
    }

    private byte[] externalKeyMaterial(UUID fieldUuid) {
        for (Block b : sanctum.store().scan()) {
            if (!b.uuid().equals(fieldUuid)) {
                continue;
            }
            JsonObject n = readNode(b);
            if (n == null || !"externalKey".equals(n.getString("kind"))) {
                throw new IllegalArgumentException("not an external key");
            }
            return Base64.getDecoder().decode(n.getString("value"));
        }
        throw new IllegalArgumentException("external key not found");
    }

    private JsonObject readNode(Block b) {
        byte[] plain = sanctum.vault().resolve(b.obfuscated());
        if (plain == null) {
            return null;
        }
        try {
            return JsonUtil.parseObject(new String(plain, java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /** 外部密钥信息。 */
    public static final class KeyInfo {
        public final UUID uuid;
        public final String name;
        public final String description;

        KeyInfo(UUID uuid, String name, String description) {
            this.uuid = uuid;
            this.name = name;
            this.description = description;
        }
    }
}
