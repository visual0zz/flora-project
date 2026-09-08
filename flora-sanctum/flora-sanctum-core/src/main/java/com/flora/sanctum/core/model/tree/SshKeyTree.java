package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * SSH 密钥树：SSH 私钥对象（SshKeyNode）。
 * 用唯一根（data）DEK 加密，parent 指向根对象 uuid。
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
        out.sort((a, b) -> Long.compare(context().orderOf(a.uuid()), context().orderOf(b.uuid())));
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
        UUID keyUuid = context().random().nextUuid();
        JsonObject key = new JsonObject();
        key.put("type", StoredNodeType.SSH_KEY.tag());
        key.put("parent", com.flora.sanctum.core.util.UuidHex.toHex(context().vault().rootObjectUuid()));
        key.put("name", name);
        key.put("value", privateKeyPem);
        // 小数索引：追加到根下末尾（max + D，溢出时由 appendOrder 内部先重排）
        key.put("order", context().appendOrder(context().vault().rootObjectUuid()));
        byte[] dek = context().vault().rootDek();
        context().writeWithDek(keyUuid, key, dek);
        return new SshKeyNode(keyUuid, this);
    }

    /**
     * 重排顺序：把 self 移到 beforeUuid 之前（beforeUuid=null 移到末尾）。
     * 密钥统一挂在根对象下，复用组/条目的小数索引机制。
     */
    public void reorder(UUID self, UUID beforeUuid) {
        long order = context().computeRootSiblingOrder(self, beforeUuid);
        JsonObject d = context().read(self);
        if (d == null) {
            return;
        }
        d.put("order", order);
        context().writeWithDek(self, d, context().vault().rootDek());
    }
}
