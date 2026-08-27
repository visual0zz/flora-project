package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * 远程配置节点（type=remote，直接存 name/url/keyRef）。
 */
public final class RemoteNode extends TreeNode {

    RemoteNode(UUID uuid, RemoteTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.REMOTE;
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    public String url() {
        JsonObject d = data();
        return d == null ? null : d.getString("url");
    }

    /** 远程配置引用的密钥（可 null）。 */
    public Ref keyRef() {
        JsonObject d = data();
        return d == null ? null : Ref.parse(d.get("keyRef"), "key");
    }

    /** 更新远程配置（url/keyRef 可 null）。 */
    public void update(String url, Ref keyRef) {
        JsonObject remote = data();
        if (remote == null) {
            throw new IllegalArgumentException("remote not found");
        }
        remote.put("url", url);
        if (keyRef != null) {
            remote.put("keyRef", keyRef.toJson());
        } else {
            remote.remove("keyRef");
        }
        byte[] dek = ctx().vault().dataDek();
        ctx().writeWithDek(uuid(), remote, dek);
    }
}
