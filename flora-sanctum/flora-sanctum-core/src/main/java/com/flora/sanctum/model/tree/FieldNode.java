package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.Base32;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.crypto.Totp;

import java.util.UUID;

/**
 * 字段节点：字段名/值/kind 的读取与更新（就地重写，保持对象局部性）。
 * TOTP 字段可生成当前验证码。
 */
public final class FieldNode extends ObjectNode {

    FieldNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.PREDEF_FIELD;
    }

    public String fieldName() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    public String value() {
        JsonObject d = data();
        return d == null ? null : d.getString("value");
    }

    public String kind() {
        JsonObject d = data();
        return d == null ? null : d.getString("kind");
    }

    public void updateValue(String value) {
        JsonObject field = data();
        if (field == null) {
            throw new IllegalArgumentException("field not found");
        }
        field.put("value", value);
        touchAndWrite(field);
    }

    /** 修改 kind（kind 为自由字符串，未知 kind 也可写入，见设计 05）。 */
    public void updateKind(String kind) {
        JsonObject field = data();
        if (field == null) {
            throw new IllegalArgumentException("field not found");
        }
        field.put("kind", kind);
        touchAndWrite(field);
    }

    /** 从 kind:totp 字段生成当前验证码（种子为 value，见设计 02"TOTP"）。 */
    public String totpCode() {
        JsonObject field = data();
        if (field == null || !"totp".equals(field.getString("kind"))) {
            throw new IllegalArgumentException("not a totp field");
        }
        byte[] secret = Base32.decode(field.getString("value"));
        return Totp.generate(secret, 6, 30);
    }

    private void touchAndWrite(JsonObject field) {
        ctx().write(uuid(), field, groupIdOf(field));
    }

    /** 所属组（field → parent(entry) → parent(group)），用于 DEK 路由。 */
    private UUID groupIdOf(JsonObject field) {
        String entryId = field.getString("parent");
        if (entryId == null) {
            return null;
        }
        JsonObject entry = ctx().read(UUID.fromString(entryId));
        return ctx().parentGroupUuid(entry);
    }
}
