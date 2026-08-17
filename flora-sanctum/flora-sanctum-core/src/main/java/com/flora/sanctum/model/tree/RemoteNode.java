package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * 远端配置节点（kind=remote 字段，value 含 url + keyRef）。
 */
public final class RemoteNode extends TreeNode {

    RemoteNode(UUID uuid, RemoteTree tree) {
        super(uuid, tree);
    }

    @Override
    public String type() {
        return "remote"; // 语义类型（存储 type=field, kind=remote）
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("fieldName");
    }

    public String url() {
        JsonObject value = value();
        return value == null ? null : value.getString("url");
    }

    public String keyRef() {
        JsonObject value = value();
        return value == null ? null : value.getString("keyRef");
    }

    /** 更新远端配置（url/keyRef 可 null）。 */
    public void update(String url, String keyRef) {
        JsonObject remote = data();
        if (remote == null) {
            throw new IllegalArgumentException("remote not found");
        }
        JsonObject value = new JsonObject();
        value.put("url", url);
        if (keyRef != null) {
            value.put("keyRef", keyRef);
        }
        remote.put("value", value);
        byte[] dek = ctx().vault().dekForRole(RootTag.DATA);
        ctx().writeWithDek(uuid(), remote, dek);
    }

    private JsonObject value() {
        JsonObject d = data();
        return d == null ? null : d.getObject("value");
    }
}
