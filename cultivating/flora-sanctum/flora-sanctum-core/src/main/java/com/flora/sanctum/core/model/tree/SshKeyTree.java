package com.flora.sanctum.core.model.tree;
import com.flora.root.container.order.RocicorpFractionalIndex;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SSH 密钥树：SSH 私钥对象（SshKeyNode）。
 * 用 sshKey category 的活跃 DEK 加密，parent 指向 sshKey category 节点 uuid。
 */
public final class SshKeyTree extends DataTree {

    public SshKeyTree(TreeContext ctx) {
        super(ViewNodeType.SSH_KEY, ctx);
    }

    @Override
    protected boolean belongsTo(StoredNodeType type, String kind) {
        return type == StoredNodeType.SSH_KEY;
    }

    @Override
    public SshKeyNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        return new SshKeyNode(uuid, this);
    }

    public List<SshKeyNode> keys() {
        List<SshKeyNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            out.add((SshKeyNode) n);
        }
        // 按 order 升序渲染（小数索引），保证列表顺序稳定、可重排
        out.sort((a, b) -> RocicorpFractionalIndex.INSTANCE.compare(context().orderOf(a.uuid()), context().orderOf(b.uuid())));
        return out;
    }

    /** 按名称查找 SSH 密钥；未找到返回 null（供唯一性校验）。 */
    public SshKeyNode key(String name) {
        for (SshKeyNode k : keys()) {
            if (name.equals(k.name())) {
                return k;
            }
        }
        return null;
    }

    public SshKeyNode createSshKey(String name, String privateKeyPem) {
        return createSshKey(name, privateKeyPem, null);
    }

    public SshKeyNode createSshKey(String name, String privateKeyPem, String publicKey) {
        UUID keyUuid = context().random().nextUuid();
        UUID cat = context().vault().categoryUuid(CategoryDisc.SSH_KEY.tag());
        JsonObject key = new JsonObject();
        key.put("type", StoredNodeType.SSH_KEY.tag());
        key.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(cat));
        key.put("name", name);
        key.put("value", privateKeyPem);
        if (publicKey != null && !publicKey.isBlank()) {
            key.put("publicKey", publicKey.trim());
        }
        // 小数索引：追加到 sshKey category 下末尾（取当前最大 order 的后继）
        key.put("order", context().appendOrder(cat));
        // 密钥块以 sshKey category 的活跃 DEK 加密（外层保护），parent 指向 sshKey category 节点；
        // 走 write 以触发 category 惰性轮换，与 group/entry 统一
        context().write(keyUuid, key, cat);
        return new SshKeyNode(keyUuid, this);
    }

    /**
     * 重排顺序：把 self 移到 beforeUuid 之前（beforeUuid=null 移到末尾）。
     * 密钥统一挂在 sshKey category 下，复用组/条目的小数索引机制。
     */
    public void reorder(UUID self, UUID beforeUuid) {
        UUID cat = context().vault().categoryUuid(CategoryDisc.SSH_KEY.tag());
        String order = context().computeSiblingOrder(self, beforeUuid, cat);
        JsonObject d = context().read(self);
        if (d == null) {
            return;
        }
        d.put("order", order);
        context().write(self, d, cat);
    }
}
