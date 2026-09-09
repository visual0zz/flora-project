package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.collect.order.FractionalIndex;
import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远程配置树（StoredNodeType.REMOTE）：远程仓库配置对象（RemoteNode）。
 * 类型 type=remote，直接存 name/url/keyRef，parent 指向仓库根对象 uuid。
 */
public final class RemoteTree extends DataTree {

    public RemoteTree(TreeContext ctx) {
        super(ViewNodeType.REMOTE, ctx);
    }

    @Override
    protected boolean belongsTo(StoredNodeType type, String kind) {
        return type == StoredNodeType.REMOTE;
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
        // 按 order 升序渲染（小数索引），保证列表顺序稳定、可重排
        out.sort((a, b) -> FractionalIndex.compare(context().orderOf(a.uuid()), context().orderOf(b.uuid())));
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

    /** 引用了指定密钥的远程配置（用于删除密钥时清理悬空 keyRef）。 */
    public List<RemoteNode> remotesWithKeyRef(UUID keyUuid) {
        List<RemoteNode> out = new ArrayList<>();
        for (RemoteNode r : remotes()) {
            Ref ref = r.keyRef();
            if (ref != null && "node".equals(ref.scheme()) && keyUuid.equals(ref.nodeUuid())) {
                out.add(r);
            }
        }
        return out;
    }

    public RemoteNode addRemote(String name, String url, Ref keyRef) {
        UUID remoteUuid = context().random().nextUuid();
        JsonObject remote = new JsonObject();
        remote.put("type", StoredNodeType.REMOTE.tag());
        remote.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(context().vault().rootObjectUuid()));
        remote.put("name", name);
        remote.put("url", url);
        if (keyRef != null) {
            remote.put("keyRef", keyRef.toJson());
        }
        // 小数索引：追加到根下末尾（取当前最大 order 的后继）
        remote.put("order", context().appendOrder(context().vault().rootObjectUuid()));
        byte[] dek = context().vault().rootDek();
        context().writeWithDek(remoteUuid, remote, dek);
        return new RemoteNode(remoteUuid, this);
    }

    /**
     * 重排顺序：把 self 移到 beforeUuid 之前（beforeUuid=null 移到末尾）。
     * 远程统一挂在根对象下，复用组/条目的小数索引机制。
     */
    public void reorder(UUID self, UUID beforeUuid) {
        String order = context().computeRootSiblingOrder(self, beforeUuid);
        JsonObject d = context().read(self);
        if (d == null) {
            return;
        }
        d.put("order", order);
        context().writeWithDek(self, d, context().vault().rootDek());
    }

    /** 按名称删除远程配置；未找到忽略。 */
    public void removeRemote(String name) {
        RemoteNode r = remote(name);
        if (r != null) {
            r.delete();
        }
    }
}
