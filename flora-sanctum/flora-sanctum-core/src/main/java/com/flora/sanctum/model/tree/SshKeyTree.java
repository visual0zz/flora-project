package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

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
        return out;
    }

    public SshKeyNode createSshKey(String name, String privateKeyPem) {
        UUID keyUuid = UUID.randomUUID();
        JsonObject key = new JsonObject();
        key.put("type", StoredNodeType.SSH_KEY.tag());
        key.put("parent", context().vault().rootObjectUuid().toString());
        key.put("name", name);
        key.put("value", privateKeyPem);
        byte[] dek = context().vault().dataDek();
        context().writeWithDek(keyUuid, key, dek);
        return new SshKeyNode(keyUuid, this);
    }
}
