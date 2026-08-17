package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * 图标树（根概念 ICON）：自定义图标对象（IconNode）。
 * 用 icon root DEK 加密，parent 指向 icon root group。
 */
public final class IconTree extends DataTree {

    public IconTree(TreeContext ctx) {
        super(RootTag.ICON, ctx);
    }

    @Override
    protected boolean belongsTo(String type, String kind) {
        return "icon".equals(type);
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

    public IconNode createIcon(byte[] data, String format) {
        UUID iconUuid = UUID.randomUUID();
        JsonObject icon = new JsonObject();
        icon.put("version", 1);
        icon.put("type", "icon");
        icon.put("parent", context().vault().rootGroupUuid(RootTag.ICON).toString());
        icon.put("data", Base64.getEncoder().encodeToString(data));
        icon.put("format", format);
        byte[] dek = context().vault().dekForRole(RootTag.ICON);
        context().writeWithDek(iconUuid, icon, dek);
        return new IconNode(iconUuid, this);
    }
}
