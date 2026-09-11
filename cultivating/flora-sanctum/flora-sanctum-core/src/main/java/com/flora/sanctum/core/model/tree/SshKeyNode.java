package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * SSH 私钥节点（块以 sshKey category 活跃 DEK 加密，parent 指向 sshKey category）。
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

    /** 公钥文本（形如 {@code ssh-rsa AAAA...} 的一行），可选；便于查看与配置到远程服务端。 */
    public String publicKey() {
        JsonObject d = data();
        return d == null ? null : d.getString("publicKey");
    }

    /** 改名（不改 uuid，远程的 keyRef 不受影响）。 */
    public void rename(String name) {
        JsonObject d = data();
        if (d == null) {
            throw new IllegalArgumentException("ssh key not found");
        }
        d.put("name", name);
        ctx().write(uuid(), d, ctx().vault().categoryUuid(CategoryDisc.SSH_KEY.tag()));
    }

    /** 更新私钥 PEM 与公钥（解密明文），加密写回；不改 uuid，远程的 keyRef 不受影响。 */
    public void update(String privateKeyPem, String publicKey) {
        JsonObject d = data();
        if (d == null) {
            throw new IllegalArgumentException("ssh key not found");
        }
        d.put("value", privateKeyPem);
        if (publicKey == null || publicKey.isBlank()) {
            d.remove("publicKey");
        } else {
            d.put("publicKey", publicKey.trim());
        }
        ctx().write(uuid(), d, ctx().vault().categoryUuid(CategoryDisc.SSH_KEY.tag()));
    }
}
