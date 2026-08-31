package com.flora.sanctum.core.model.tree;
import com.flora.sanctum.core.model.*;
import com.flora.sanctum.core.model.impl.*;
import com.flora.sanctum.core.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 图标树：自定义图标对象（IconNode）。
 * 用唯一根（data）DEK 加密，parent 指向根对象 uuid。
 */
public final class IconTree extends DataTree {

    public IconTree(TreeContext ctx) {
        super(ViewNodeType.ICON, ctx);
    }

    @Override
    protected boolean belongsTo(StoredNodeType type, String kind) {
        return type == StoredNodeType.ICON;
    }

    @Override
    public IconNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        return new IconNode(uuid, this);
    }

    public List<IconNode> icons() {
        List<IconNode> out = new ArrayList<>();
        for (TreeNode n : nodes()) {
            out.add((IconNode) n);
        }
        return out;
    }

    public IconNode createIcon(String name, byte[] data, String format) {
        UUID iconUuid = UUID.randomUUID();
        JsonObject icon = new JsonObject();
        icon.put("type", StoredNodeType.ICON.tag());
        icon.put("parent", context().vault().rootObjectUuid().toString());
        if (name != null && !name.isBlank()) {
            icon.put("name", name);
        }
        icon.put("data", Base64.getEncoder().encodeToString(data));
        icon.put("format", format);
        byte[] dek = context().vault().rootDek();
        context().writeWithDek(iconUuid, icon, dek);
        return new IconNode(iconUuid, this);
    }
}
