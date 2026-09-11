package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.root.container.order.RocicorpFractionalIndex;
import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 远程配置树（StoredNodeType.REMOTE）：远程仓库配置对象（RemoteNode）。
 * 类型 type=remote，直接存 name/url/keyRef，parent 指向 remote category 节点 uuid。
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
        out.sort((a, b) -> RocicorpFractionalIndex.INSTANCE.compare(context().orderOf(a.uuid()), context().orderOf(b.uuid())));
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
        UUID cat = context().vault().categoryUuid(CategoryDisc.REMOTE.tag());
        JsonObject remote = new JsonObject();
        remote.put("type", StoredNodeType.REMOTE.tag());
        remote.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(cat));
        remote.put("name", name);
        remote.put("url", url);
        if (keyRef != null) {
            remote.put("keyRef", keyRef.toJson());
        }
        // 小数索引：追加到 remote category 下末尾（取当前最大 order 的后继）
        remote.put("order", context().appendOrder(cat));
        // 远程块以 remote category 的活跃 DEK 加密（外层保护），parent 指向 remote category 节点；
        // 走 write 以触发 category 惰性轮换，与 group/entry 统一
        context().write(remoteUuid, remote, cat);
        return new RemoteNode(remoteUuid, this);
    }

    /**
     * 重排顺序：把 self 移到 beforeUuid 之前（beforeUuid=null 移到末尾）。
     * 远程统一挂在 remote category 下，复用组/条目的小数索引机制。
     */
    public void reorder(UUID self, UUID beforeUuid) {
        UUID cat = context().vault().categoryUuid(CategoryDisc.REMOTE.tag());
        String order = context().computeSiblingOrder(self, beforeUuid, cat);
        JsonObject d = context().read(self);
        if (d == null) {
            return;
        }
        d.put("order", order);
        context().write(self, d, cat);
    }

    /** 按名称删除远程配置；未找到忽略。 */
    public void removeRemote(String name) {
        RemoteNode r = remote(name);
        if (r != null) {
            r.delete();
        }
    }
}
