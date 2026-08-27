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

    public String privateKey() {
        JsonObject d = data();
        return d == null ? null : d.getString("privateKey");
    }
}
