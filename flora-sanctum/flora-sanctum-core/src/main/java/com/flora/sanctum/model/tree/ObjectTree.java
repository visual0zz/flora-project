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
 * 普通对象树（根概念 DATA）：组（GroupNode）→ 条目（EntryNode）→ 字段（FieldNode）。
 * 树的根节点创建操作由本类提供，子节点创建经 GroupNode。
 */
public final class ObjectTree extends DataTree {

    public ObjectTree(TreeContext ctx) {
        super(RootTag.DATA, ctx);
    }

    @Override
    protected boolean belongsTo(String type, String kind) {
        return "group".equals(type) || "entry".equals(type)
                || "customField".equals(type)
                || ("field".equals(type) && !"remote".equals(kind));
    }

    @Override
    public ObjectNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        // objects root group 是基础设施（持 root DEK），不暴露为普通节点
        if ("group".equals(d.getString("type"))
                && uuid.equals(context().vault().rootGroupUuid(RootTag.DATA))) {
            return null;
        }
        return switch (d.getString("type")) {
            case "group" -> new GroupNode(uuid, this);
            case "entry" -> new EntryNode(uuid, this);
            default -> new FieldNode(uuid, this);
        };
    }

    public GroupNode group(UUID uuid) {
        ObjectNode n = find(uuid);
        return n instanceof GroupNode g ? g : null;
    }

    public EntryNode entry(UUID uuid) {
        ObjectNode n = find(uuid);
        return n instanceof EntryNode e ? e : null;
    }

    public FieldNode field(UUID uuid) {
        ObjectNode n = find(uuid);
        return n instanceof FieldNode f ? f : null;
    }

    /** 顶层组（parent 为 DATA 根概念）。 */
    public List<GroupNode> rootGroups() {
        List<GroupNode> out = new ArrayList<>();
        for (TreeNode n : roots()) {
            if (n instanceof GroupNode g) {
                out.add(g);
            }
        }
        return out;
    }

    /** 顶层条目（parent 为 DATA 根概念）。 */
    public List<EntryNode> rootEntries() {
        List<EntryNode> out = new ArrayList<>();
        for (TreeNode n : roots()) {
            if (n instanceof EntryNode e) {
                out.add(e);
            }
        }
        return out;
    }

    /** 新建组（parentId=null 为顶层，parent 记根概念 data）。 */
    public GroupNode createGroup(UUID parentId, String name) {
        UUID groupUuid = UUID.randomUUID();
        byte[] dek = new byte[32];
        context().random().nextBytes(dek);
        byte[] parentDek = (parentId != null && context().vault().folderDek(parentId) != null)
                ? context().vault().folderDek(parentId)
                : context().vault().dekForRole(RootTag.DATA);
        byte[] wrapped = context().wrapDek(dek, parentDek);
        JsonObject group = new JsonObject();
        group.put("version", 1);
        group.put("type", "group");
        group.put("name", name);
        group.put("parent", parentId == null ? RootTag.DATA.tag() : parentId.toString());
        group.put("dek", Base64.getEncoder().encodeToString(wrapped));
        context().write(groupUuid, group, parentId);
        context().vault().addFolderDek(groupUuid, dek);
        return new GroupNode(groupUuid, this);
    }

    /** 新建条目（groupId=null 为顶层，parent 记根概念 data）。 */
    public EntryNode createEntry(UUID groupId, String name, EntryFields fields) {
        return createEntry(groupId, name, fields, null, null);
    }

    /** 新建条目（含图标引用）。 */
    public EntryNode createEntry(UUID groupId, String name, EntryFields fields, Integer iconId, UUID iconUuid) {
        UUID entryUuid = UUID.randomUUID();
        long now = System.currentTimeMillis();
        JsonObject entry = new JsonObject();
        entry.put("version", 1);
        entry.put("type", "entry");
        entry.put("name", name);
        entry.put("parent", groupId == null ? RootTag.DATA.tag() : groupId.toString());
        if (iconId != null) {
            entry.put("iconId", iconId);
        }
        if (iconUuid != null) {
            entry.put("icon", iconUuid.toString());
        }
        context().write(entryUuid, entry, groupId);
        // 预设字段独立块（createTime/updateTime 必写；其余有值才写）
        writePreset(entryUuid, groupId, "createTime", String.valueOf(now));
        writePreset(entryUuid, groupId, "updateTime", String.valueOf(now));
        writePreset(entryUuid, groupId, "password", fields.password());
        writePreset(entryUuid, groupId, "url", fields.url());
        writePreset(entryUuid, groupId, "username", fields.username());
        writePreset(entryUuid, groupId, "labels", EntryFields.labelsToString(fields.labels()));
        return new EntryNode(entryUuid, this);
    }

    /** 写预设字段块（确定性 uuid，value 空则不写）。 */
    private void writePreset(UUID entryUuid, UUID groupId, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        JsonObject f = new JsonObject();
        f.put("version", 1);
        f.put("type", "field");
        f.put("parent", entryUuid.toString());
        f.put("fieldName", name);
        f.put("value", value);
        context().write(EntryFields.presetUuid(entryUuid, name), f, groupId);
    }
}
