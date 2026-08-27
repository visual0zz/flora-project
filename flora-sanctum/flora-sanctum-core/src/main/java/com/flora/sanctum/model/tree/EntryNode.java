package com.flora.sanctum.model.tree;
import com.flora.sanctum.model.*;
import com.flora.sanctum.model.impl.*;
import com.flora.sanctum.model.vault.*;

import com.flora.root.codec.json.model.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 条目节点：内置预设字段（名称/密码/URL/用户名/标签/创建时间/更新时间）+ 自定义字段。
 * <p>
 * 预设字段以独立块存储（type=field，fieldName 固定为预设名，随机 uuid，parent=条目），
 * 不再是 entry 负载 JSON 中的字段。自定义字段块 type 为 customField。
 * 新建/编辑/删除等操作由节点承担（见设计 05）。
 */
public final class EntryNode extends ObjectNode {

    EntryNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public StoredNodeType type() {
        return StoredNodeType.ENTRY;
    }

    public String name() {
        JsonObject d = data();
        return d == null ? null : d.getString("name");
    }

    public String password() {
        return presetValue("password");
    }

    public String url() {
        return presetValue("url");
    }

    public String username() {
        return presetValue("username");
    }

    public List<String> labels() {
        String v = presetValue("labels");
        if (v == null || v.isBlank()) {
            return List.of();
        }
        return EntryFields.parseLabels(v);
    }

    /** 自定义图标引用（可 null）。 */
    public Ref iconRef() {
        JsonObject d = data();
        return d == null ? null : Ref.parse(d.get("iconRef"), "icon");
    }

    /** 创建时间（本地毫秒，只读）。 */
    public Long createTime() {
        String v = presetValue("createTime");
        return v == null ? null : Long.parseLong(v);
    }

    /** 更新时间（本地毫秒，只读）。 */
    public Long updateTime() {
        String v = presetValue("updateTime");
        return v == null ? null : Long.parseLong(v);
    }

    /**
     * 读预设字段块的值；预设字段以条目子节点形式存储（随机 uuid，parent=条目，name=预设名），
     * 与自定义字段结构一致，经 childrenOf 按 name 定位。不回退到 entry 旧字段（不兼容旧库）。
     */
    private String presetValue(String name) {
        FieldNode f = presetChild(name);
        return f == null ? null : f.value();
    }

    /** 按预设名在条目直接子节点中定位预设字段块（type=field、parent=本条目且 name 命中）。 */
    private FieldNode presetChild(String name) {
        for (UUID u : ctx().childrenOf(uuid())) {
            FieldNode f = tree().field(u);
            if (f != null && f.type() == StoredNodeType.PREDEF_FIELD && name.equals(f.fieldName())) {
                return f;
            }
        }
        return null;
    }

    public void rename(String newName) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        entry.put("name", newName);
        ctx().write(uuid(), entry, groupId);
    }

    /** 更新内置预设字段（password/url/username/labels）+ updateTime（独立块）。不回退 entry 旧字段（不兼容旧库）。 */
    public void updateBuiltins(EntryFields fields) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        long now = System.currentTimeMillis();
        writePreset("password", fields.password(), groupId);
        writePreset("url", fields.url(), groupId);
        writePreset("username", fields.username(), groupId);
        writePreset("labels", EntryFields.labelsToString(fields.labels()), groupId);
        writePreset("updateTime", String.valueOf(now), groupId);
    }

    /** 设置/清除自定义图标引用（iconUuid=null 清除）。 */
    public void setIcon(UUID iconUuid) {
        setIcon(iconUuid == null ? null : Ref.nodeIcon(iconUuid));
    }

    /** 设置内置图标引用（name 为内置资源名；null 清除）。 */
    public void setBuiltinIcon(String name) {
        setIcon(name == null ? null : Ref.builtinIcon(name));
    }

    /** 设置/清除图标引用（Ref；null 清除）。 */
    public void setIcon(Ref ref) {
        JsonObject entry = data();
        if (entry == null) {
            throw new IllegalArgumentException("entry not found");
        }
        UUID groupId = ctx().parentGroupUuid(entry);
        if (ref == null) {
            entry.remove("iconRef");
        } else {
            entry.put("iconRef", ref.toJson());
        }
        ctx().write(uuid(), entry, groupId);
    }

    /** 在此条目下新建自定义字段（kind 可为 null；块 type 为 customField）。 */
    public FieldNode createField(String fieldName, String value, String kind) {
        if (EntryFields.isPreset(fieldName)) {
            throw new IllegalArgumentException("预设字段名不可用于自定义字段: " + fieldName);
        }
        UUID groupId = ctx().parentGroupUuid(data());
        UUID fieldUuid = UUID.randomUUID();
        JsonObject field = new JsonObject();
        field.put("type", StoredNodeType.CUSTOM_FIELD.tag());
        field.put("parent", uuid().toString());
        field.put("name", fieldName);
        field.put("value", value);
        if (kind != null) {
            field.put("kind", kind);
        }
        ctx().write(fieldUuid, field, groupId);
        return tree().field(fieldUuid);
    }

    /** 直接自定义字段（type=customField；不含预设字段 type=field）。 */
    public List<FieldNode> fields() {
        List<FieldNode> out = new ArrayList<>();
        for (UUID u : ctx().childrenOf(uuid())) {
            FieldNode f = tree().field(u);
            if (f != null && f.type() == StoredNodeType.CUSTOM_FIELD) {
                out.add(f);
            }
        }
        return out;
    }

    /** 按字段名查找直接自定义字段；未找到返回 null。 */
    public FieldNode field(String fieldName) {
        for (FieldNode f : fields()) {
            if (fieldName.equals(f.fieldName())) {
                return f;
            }
        }
        return null;
    }

    /** 删除条目及其全部字段（预设子节点 + 自定义字段）。 */
    @Override
    public void delete() {
        UUID groupId = ctx().parentGroupUuid(data());
        for (FieldNode f : childrenFields()) {
            ctx().delete(f.uuid());
        }
        for (FieldNode f : fields()) {
            f.delete();
        }
        super.delete();
    }

    /** 全部直接字段子节点（预设 + 自定义），含未列入 fields() 的预设。 */
    private List<FieldNode> childrenFields() {
        List<FieldNode> out = new ArrayList<>();
        for (UUID u : ctx().childrenOf(uuid())) {
            FieldNode f = tree().field(u);
            if (f != null) {
                out.add(f);
            }
        }
        return out;
    }

    /** 写预设字段块（value 空则删除块；复用同名已有块 uuid，否则随机 uuid；parent 指向本条目）。 */
    void writePreset(String name, String value, UUID groupId) {
        FieldNode existing = presetChild(name);
        if (value == null || value.isEmpty()) {
            if (existing != null) {
                ctx().delete(existing.uuid());
            }
            return;
        }
        UUID pu = existing == null ? UUID.randomUUID() : existing.uuid();
        JsonObject f = new JsonObject();
        f.put("type", StoredNodeType.PREDEF_FIELD.tag());
        f.put("parent", uuid().toString());
        f.put("name", name);
        f.put("value", value);
        ctx().write(pu, f, groupId);
    }
}
