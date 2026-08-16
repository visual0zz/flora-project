package com.flora.sanctum.model;

import com.flora.root.codec.JsonUtil;
import com.flora.root.codec.json.model.JsonObject;

import com.flora.sanctum.crypto.impl.CipherCodec;
import com.flora.sanctum.crypto.impl.Envelope;
import com.flora.sanctum.store.Block;
import com.flora.sanctum.store.BlockHeader;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 外部密钥加解密服务（见设计 02"外部密钥 = 字段 kind"）。
 * <p>
 * 外部密钥 = 某条目下 {@code kind:"externalKey"} 的字段，value 即密钥材料（base64）。
 * - {@code encrypt(data, fieldUuid)}：用该字段密钥加密，返回标准密文块（含异或混淆）base58。
 * - {@code decrypt(cipherBase58)}：不传 uuid，靠密文头 keyId 在激活 externalKey 集合试解。
 * - {@code list()}：列出激活 externalKey 字段的 uuid + 描述。
 * <p>
 * 防泄漏：解密候选域 = 仅 {@code kind:"externalKey"} 的字段密钥，系统 DEK 不在候选里。
 */
public final class ExternalKeyService {

    private final Sanctum sanctum;

    public ExternalKeyService(Sanctum sanctum) {
        this.sanctum = sanctum;
    }

    /** 列出激活（kind=externalKey 且可解密）的外部密钥字段。 */
    public List<KeyInfo> list() {
        List<KeyInfo> out = new ArrayList<>();
        for (Block b : sanctum.store().scan()) {
            JsonObject n = readNode(b);
            if (n == null || !"field".equals(n.getString("type")) || !"externalKey".equals(n.getString("kind"))) {
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
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(keyMaterial);
        CipherCodec codec = new CipherCodec(encKey, keyMaterial, sanctum.vault().random());
        return codec.encode(fieldUuid, data, codec.makeKeyIdWith(keyMaterial));
    }

    /** 解密：靠密文头 keyId 在激活 externalKey 集合试解。 */
    public byte[] decrypt(String cipherBase58) {
        byte[] obfuscated;
        try {
            obfuscated = com.flora.root.codec.Base58.decode(cipherBase58);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid base58");
        }
        byte[] block = BlockHeader.deobfuscate(obfuscated);
        if (block.length < 26 || !BlockHeader.isBlock(block)) {
            throw new IllegalArgumentException("not a block");
        }
        byte[] keyId = new byte[4];
        System.arraycopy(block, 22, keyId, 0, 4);
        // 试解所有 externalKey 字段
        for (Block b : sanctum.store().scan()) {
            JsonObject n = readNode(b);
            if (n == null || !"field".equals(n.getString("type")) || !"externalKey".equals(n.getString("kind"))) {
                continue;
            }
            byte[] keyMaterial = Base64.getDecoder().decode(n.getString("value"));
            byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(keyMaterial);
            CipherCodec codec = new CipherCodec(encKey, keyMaterial, sanctum.vault().random());
            try {
                return codec.decode(obfuscated).plaintext;
            } catch (IllegalStateException ignore) {
                // tag 不符 → 下一个
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
        field.put("updateTimestamp", System.currentTimeMillis());
        // 外部密钥是条目下的字段，属普通对象树 → 用 data 根 DEK
        byte[] dek = sanctum.vault().dekForRole(RootTag.DATA);
        byte[] encKey = com.flora.sanctum.crypto.KeyDerivation.encKey(dek);
        CipherCodec codec = new CipherCodec(encKey, dek, sanctum.vault().random());
        byte[] block = codec.encode(fieldUuid, JsonUtil.toJsonString(field).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                codec.makeKeyIdWith(dek));
        sanctum.store().put(fieldUuid, block, new com.flora.sanctum.store.impl.RawCodec());
        return fieldUuid;
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
