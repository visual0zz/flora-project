package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远端配置树（根概念 REMOTE）：kind=remote 的 field 对象（RemoteNode）。
 * 加密沿用 data 根 DEK，parent 记根概念 remote。
 */
public final class RemoteTree extends DataTree {

    public RemoteTree(TreeContext ctx) {
        super(RootTag.REMOTE, ctx);
    }

    @Override
    protected boolean belongsTo(String type, String kind) {
        return "field".equals(type) && "remote".equals(kind);
    }

    @Override
    public RemoteNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        return new RemoteNode(uuid, this);
    }

    public List<RemoteNode> remotes() {
        List<RemoteNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            out.add((RemoteNode) n);
        }
        return out;
    }

    /** 按名称查找远端配置；未找到返回 null。 */
    public RemoteNode remote(String name) {
        for (RemoteNode r : remotes()) {
            if (name.equals(r.name())) {
                return r;
            }
        }
        return null;
    }

    public RemoteNode addRemote(String name, String url, String keyRef) {
        UUID remoteUuid = UUID.randomUUID();
        JsonObject remote = new JsonObject();
        remote.put("version", 1);
        remote.put("type", "field");
        remote.put("parent", RootTag.REMOTE.tag());
        remote.put("fieldName", name);
        remote.put("kind", "remote");
        JsonObject value = new JsonObject();
        value.put("url", url);
        if (keyRef != null) {
            value.put("keyRef", keyRef);
        }
        remote.put("value", value);
        byte[] dek = context().vault().dekForRole(RootTag.DATA);
        context().writeWithDek(remoteUuid, remote, dek);
        return new RemoteNode(remoteUuid, this);
    }

    /** 按名称删除远端配置；未找到忽略。 */
    public void removeRemote(String name) {
        RemoteNode r = remote(name);
        if (r != null) {
            r.delete();
        }
    }
}
