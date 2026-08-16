package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 条目节点：内置预设字段（名称/密码/URL/用户名/标签）+ 自定义字段。
 * 新建/编辑/删除等操作由节点承担（见设计 05"数据结构树化"）。
 */
public final class EntryNode extends ObjectNode {

    EntryNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public String type() {
        return "entry";
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    public String password() {
        JsonObject d = data();
        return d == null ? null : d.getString("password");
    }

    public String url() {
        JsonObject d = data();
        return d == null ? null : d.getString("url");
    }

    public String username() {
        JsonObject d = data();
        return d == null ? null : d.getString("username");
    }

    public List<String> labels() {
        return EntryFields.labelsOf(data());
    }

    /** 自定义图标引用 uuid 字符串（可 null）。 */
    public String icon() {
        JsonObject d = data();
        return d == null ? null : d.getString("icon");
    }

    /** 创建时间（本地毫秒，只读）。 */
    public Long createTime() {
        JsonObject d = data();
        return d == null ? null : d.getLong("createTime");
    }

    /** 更新时间（本地毫秒，只读）。 */
    public Long updateTime() {
        JsonObject d = data();
        return d == null ? null : d.getLong("updateTime");
    }

    public void rename(String newName) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        entry.put("name", newName);
        entry.put("updateTimestamp", ctx().nextTimestamp());
        ctx().write(uuid(), entry, groupId);
    }

    /** 更新内置预设字段（password/url/username/labels）+ updateTime。 */
    public void updateBuiltins(EntryFields fields) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        long now = System.currentTimeMillis();
        if (fields.password() != null) {
            entry.put("password", fields.password());
        }
        if (fields.url() != null) {
            entry.put("url", fields.url());
        }
        if (fields.username() != null) {
            entry.put("username", fields.username());
        }
        entry.put("labels", fields.labels());
        entry.put("updateTime", now);
        entry.put("updateTimestamp", ctx().nextTimestamp());
        ctx().write(uuid(), entry, groupId);
    }

    /** 设置/清除自定义图标引用（iconUuid=null 清除）。 */
    public void setIcon(UUID iconUuid) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        if (iconUuid == null) {
            entry.remove("icon");
            entry.remove("iconId");
        } else {
            entry.put("icon", iconUuid.toString());
            entry.remove("iconId");
        }
        entry.put("updateTimestamp", ctx().nextTimestamp());
        ctx().write(uuid(), entry, groupId);
    }

    /** 在此条目下新建自定义字段（kind 可为 null）。 */
    public FieldNode createField(String fieldName, String value, String kind) {
        UUID groupId = ctx().parentGroupUuid(data());
        UUID fieldUuid = UUID.randomUUID();
        JsonObject field = new JsonObject();
        field.put("version", 1);
        field.put("type", "field");
        field.put("parent", uuid().toString());
        field.put("fieldName", fieldName);
        field.put("value", value);
        if (kind != null) {
            field.put("kind", kind);
        }
        field.put("updateTimestamp", ctx().nextTimestamp());
        ctx().write(fieldUuid, field, groupId);
        return tree().field(fieldUuid);
    }

    /** 直接字段（不含 remote，remote 归 REMOTE 树）。 */
    public List<FieldNode> fields() {
        List<FieldNode> out = new ArrayList<>();
        for (UUID u : ctx().childrenOf(uuid())) {
            FieldNode f = tree().field(u);
            if (f != null && !"remote".equals(f.kind())) {
                out.add(f);
            }
        }
        return out;
    }

    /** 按字段名查找直接字段；未找到返回 null。 */
    public FieldNode field(String fieldName) {
        for (FieldNode f : fields()) {
            if (fieldName.equals(f.fieldName())) {
                return f;
            }
        }
        return null;
    }

    /** 删除条目及其全部字段。 */
    @Override
    public void delete() {
        for (FieldNode f : fields()) {
            f.delete();
        }
        super.delete();
    }
}
