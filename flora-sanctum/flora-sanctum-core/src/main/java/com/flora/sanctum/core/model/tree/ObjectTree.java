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
 * 普通对象树：组（GroupNode）→ 条目（EntryNode）→ 字段（FieldNode）。
 * 树的根节点创建操作由本类提供，子节点创建经 GroupNode。
 */
public final class ObjectTree extends DataTree {

    public ObjectTree(TreeContext ctx) {
        super(ViewNodeType.PASSWORD, ctx);
    }

    @Override
    protected boolean belongsTo(StoredNodeType type, String kind) {
        return type == StoredNodeType.GROUP || type == StoredNodeType.ENTRY
                || type == StoredNodeType.PREDEF_FIELD || type == StoredNodeType.CUSTOM_FIELD;
    }

    @Override
    public ObjectNode find(UUID uuid) {
        JsonObject d = context().read(uuid);
        if (!isOwned(d)) {
            return null;
        }
        StoredNodeType nt = StoredNodeType.fromTag(d.getString("type"));
        // 仓库根对象（type=root）是基础设施（持 root DEK），不暴露为普通节点
        if (nt == StoredNodeType.ROOT) {
            return null;
        }
        return switch (nt) {
            case GROUP -> new GroupNode(uuid, this);
            case ENTRY -> new EntryNode(uuid, this);
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

    /** 新建组（parentId=null 为顶层，parent 记根对象 uuid，用 rootDek 加密）。 */
    public GroupNode createGroup(UUID parentId, String name) {
        UUID groupUuid = UUID.randomUUID();
        UUID effectiveParent = parentId != null ? parentId : context().vault().rootObjectUuid();
        byte[] dek = new byte[32];
        context().random().nextBytes(dek);
        // 组块整体用父组 DEK（顶层 rootDek）加密（外层保护），dek 字段直接存明文 base64，无需内层包裹
        JsonObject group = new JsonObject();
        group.put("type", StoredNodeType.GROUP.tag());
        group.put("name", name);
        group.put("parent", effectiveParent.toString());
        group.put("dek", Base64.getEncoder().encodeToString(dek));
        context().write(groupUuid, group, effectiveParent);
        context().vault().addGroupDek(groupUuid, dek);
        return new GroupNode(groupUuid, this);
    }

    /** 新建条目（groupId=null 为顶层，parent 记根对象 uuid，用 rootDek 加密）。 */
    public EntryNode createEntry(UUID groupId, String name, EntryFields fields) {
        UUID entryUuid = UUID.randomUUID();
        UUID effectiveParent = groupId != null ? groupId : context().vault().rootObjectUuid();
        long now = System.currentTimeMillis();
        JsonObject entry = new JsonObject();
        entry.put("type", StoredNodeType.ENTRY.tag());
        entry.put("name", name);
        entry.put("parent", effectiveParent.toString());
        // createTime/updateTime 直接存条目 JSON 内（不再单独成块）
        entry.put("createTime", now);
        entry.put("updateTime", now);
        context().write(entryUuid, entry, effectiveParent);
        // 预设字段独立块（password/url/username/labels 有值才写；createTime/updateTime 已在条目内）
        writePreset(entryUuid, effectiveParent, "password", fields.password());
        writePreset(entryUuid, effectiveParent, "url", fields.url());
        writePreset(entryUuid, effectiveParent, "username", fields.username());
        writePreset(entryUuid, effectiveParent, "labels", EntryFields.labelsToString(fields.labels()));
        return new EntryNode(entryUuid, this);
    }

    /** 写预设字段块（随机 uuid，parent 指向条目，与自定义字段结构一致；value 空则不写）。 */
    private void writePreset(UUID entryUuid, UUID groupId, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        UUID fieldUuid = UUID.randomUUID();
        JsonObject f = new JsonObject();
        f.put("type", StoredNodeType.PREDEF_FIELD.tag());
        f.put("parent", entryUuid.toString());
        f.put("name", name);
        f.put("value", value);
        f.put("updateTime", System.currentTimeMillis());
        context().write(fieldUuid, f, groupId);
    }
}
