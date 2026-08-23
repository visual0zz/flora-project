package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远程配置树（NodeType.REMOTE）：远程仓库配置对象（RemoteNode）。
 * 独立落盘类型 type=remote，直接存 name/url/keyRef，parent 指向仓库根对象 uuid。
 */
public final class RemoteTree extends DataTree {

    public RemoteTree(TreeContext ctx) {
        super(NodeType.REMOTE, ctx);
    }

    @Override
    protected boolean belongsTo(String type, String kind) {
        return NodeType.fromTag(type) == NodeType.REMOTE;
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

    /** 按名称查找远程配置；未找到返回 null。 */
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
        remote.put("type", NodeType.REMOTE.tag());
        remote.put("parent", context().vault().rootGroupUuid(RootTag.DATA).toString());
        remote.put("name", name);
        remote.put("url", url);
        if (keyRef != null) {
            remote.put("keyRef", keyRef);
        }
        byte[] dek = context().vault().dekForRole(RootTag.DATA);
        context().writeWithDek(remoteUuid, remote, dek);
        return new RemoteNode(remoteUuid, this);
    }

    /** 按名称删除远程配置；未找到忽略。 */
    public void removeRemote(String name) {
        RemoteNode r = remote(name);
        if (r != null) {
            r.delete();
        }
    }
}
