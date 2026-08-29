package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * SSH 私钥节点（唯一根 DEK 加密）。
 */
public final class SshKeyNode extends TreeNode {

    SshKeyNode(UUID uuid, SshKeyTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.SSH_KEY;
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    /** 私钥 PEM 文本（与 FieldNode 的 value 字段命名统一）。 */
    public String value() {
        JsonObject d = data();
        return d == null ? null : d.getString("value");
    }

    /** 改名（不改 uuid，远程的 keyRef 不受影响）。 */
    public void rename(String name) {
        JsonObject d = data();
        if (d == null) {
            throw new IllegalArgumentException("ssh key not found");
        }
        d.put("name", name);
        byte[] dek = ctx().vault().dataDek();
        ctx().writeWithDek(uuid(), d, dek);
    }
}
