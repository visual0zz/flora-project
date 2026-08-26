package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.UUID;

/**
 * 自定义图标节点（icon root DEK 加密）。
 */
public final class IconNode extends TreeNode {

    IconNode(UUID uuid, IconTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.ICON;
    }

    /** 图标名称（导入文件名；无则 null）。 */
    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    public byte[] iconData() {
        JsonObject d = data();
        if (d == null || d.getString("data") == null) {
            return new byte[0];
        }
        return java.util.Base64.getDecoder().decode(d.getString("data"));
    }

    public String format() {
        JsonObject d = data();
        return d == null ? null : d.getString("format");
    }
}
