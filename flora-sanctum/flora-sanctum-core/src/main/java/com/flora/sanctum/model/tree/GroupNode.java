package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 组节点（文件夹）：新建子组/条目、重命名、递归删除、图标引用。
 * 每个文件夹绑定一个 DEK（子组 DEK 用父 DEK 包裹，见设计 02"文件夹 DEK"）。
 */
public final class GroupNode extends ObjectNode {

    GroupNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.GROUP;
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    /** 自定义图标引用（可 null）。 */
    public Ref iconRef() {
        JsonObject d = data();
        return d == null ? null : Ref.parse(d.get("iconRef"), "icon");
    }

    public void rename(String newName) {
        JsonObject group = data();
        if (group == null) {
            throw new IllegalArgumentException("group not found");
        }
        UUID parentId = ctx().parentGroupUuid(group);
        group.put("name", newName);
        ctx().write(uuid(), group, parentId);
    }

    /** 设置/清除文件夹图标引用（iconUuid=null 清除）。 */
    public void setIcon(UUID iconUuid) {
        setIcon(iconUuid == null ? null : Ref.nodeIcon(iconUuid));
    }

    /** 设置内置图标引用（name 为内置资源名；null 清除）。 */
    public void setBuiltinIcon(String name) {
        setIcon(name == null ? null : Ref.builtinIcon(name));
    }

    /** 设置/清除图标引用（Ref；null 清除）。 */
    public void setIcon(Ref ref) {
        JsonObject group = data();
        if (group == null) {
            throw new IllegalArgumentException("group not found");
        }
        UUID parentId = ctx().parentGroupUuid(group);
        if (ref == null) {
            group.remove("iconRef");
        } else {
            group.put("iconRef", ref.toJson());
        }
        ctx().write(uuid(), group, parentId);
    }

    /** 在此组下新建子组。 */
    public GroupNode createChildGroup(String name) {
        return tree().createGroup(uuid(), name);
    }

    /** 在此组下新建条目。 */
    public EntryNode createEntry(String name, EntryFields fields) {
        return tree().createEntry(uuid(), name, fields);
    }

    /** 直接子节点（组 + 条目）。 */
    public List<ObjectNode> children() {
        List<ObjectNode> out = new ArrayList<>();
        for (UUID u : ctx().childrenOf(uuid())) {
            ObjectNode n = tree().find(u);
            if (n != null) {
                out.add(n);
            }
        }
        return out;
    }

    /** 直接子组。 */
    public List<GroupNode> childGroups() {
        List<GroupNode> out = new ArrayList<>();
        for (ObjectNode n : children()) {
            if (n instanceof GroupNode g) {
                out.add(g);
            }
        }
        return out;
    }

    /** 直接条目。 */
    public List<EntryNode> entries() {
        List<EntryNode> out = new ArrayList<>();
        for (ObjectNode n : children()) {
            if (n instanceof EntryNode e) {
                out.add(e);
            }
        }
        return out;
    }

    /** 递归删除本组及其全部子节点。 */
    @Override
    public void delete() {
        for (ObjectNode c : children()) {
            c.delete();
        }
        super.delete();
    }
}
