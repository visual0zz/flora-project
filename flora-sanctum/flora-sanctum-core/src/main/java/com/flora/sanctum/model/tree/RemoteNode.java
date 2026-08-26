package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * 远程配置节点（type=remote，扁平存 name/url/keyRef）。
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

    public String keyRef() {
        JsonObject d = data();
        return d == null ? null : d.getString("keyRef");
    }

    /** 更新远程配置（url/keyRef 可 null）。 */
    public void update(String url, String keyRef) {
        JsonObject remote = data();
        if (remote == null) {
            throw new IllegalArgumentException("remote not found");
        }
        remote.put("url", url);
        if (keyRef != null) {
            remote.put("keyRef", keyRef);
        } else {
            remote.remove("keyRef");
        }
        byte[] dek = ctx().vault().dekForRole(RootTag.DATA);
        ctx().writeWithDek(uuid(), remote, dek);
    }
}
