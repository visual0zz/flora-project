package com.flora.sanctum.model;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * SSH 私钥节点（sshKey root DEK 加密）。
 */
public final class SshKeyNode extends TreeNode {

    SshKeyNode(UUID uuid, SshKeyTree tree) {
        super(uuid, tree);
    }

    @Override
    public String type() {
        return "sshKey";
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
