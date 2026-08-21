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
 * 预设字段自 2026-08 起以独立块存储（type=field，fieldName 固定为预设名，确定性 uuid），
 * 不再是 entry 负载 JSON 中的字段；读取时若预设块缺失回退到 entry 旧字段（迁移兼容）。
 * 自定义字段块 type 为 customField。新建/编辑/删除等操作由节点承担（见设计 05）。
 */
public final class EntryNode extends ObjectNode {

    EntryNode(UUID uuid, ObjectTree tree) {
        super(uuid, tree);
    }

    @Override
    public NodeType type() {
        return NodeType.ENTRY;
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

    /** 自定义图标引用 uuid 字符串（可 null）。 */
    public String icon() {
        JsonObject d = data();
        return d == null ? null : d.getString("icon");
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

    /** 读取预设字段块的值；块不存在回退到 entry 旧字段（迁移兼容）。 */
    private String presetValue(String name) {
        JsonObject f = ctx().read(EntryFields.presetUuid(uuid(), name));
        if (f != null) {
            return f.getString("value");
        }
        JsonObject d = data();
        if (d == null) {
            return null;
        }
        if ("labels".equals(name)) {
            return com.flora.sanctum.model.EntryFields.labelsToString(
                    com.flora.sanctum.model.EntryFields.labelsOf(d));
        }
        return d.getString(name);
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

    /** 更新内置预设字段（password/url/username/labels）+ updateTime（独立块），并清理 entry 旧字段。 */
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
        // 迁移：清理 entry JSON 中的旧预设字段，避免双份
        boolean dirty = false;
        for (String key : List.of("password", "url", "username", "labels", "createTime", "updateTime")) {
            if (entry.containsKey(key)) {
                entry.remove(key);
                dirty = true;
            }
        }
        if (dirty) {
            ctx().write(uuid(), entry, groupId);
        }
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
        field.put("version", 1);
        field.put("type", NodeType.CUSTOM_FIELD.tag());
        field.put("parent", uuid().toString());
        field.put("fieldName", fieldName);
        field.put("value", value);
        if (kind != null) {
            field.put("kind", kind);
        }
        ctx().write(fieldUuid, field, groupId);
        return tree().field(fieldUuid);
    }

    /** 直接自定义字段（不含预设字段与 remote；remote 归 REMOTE 树）。 */
    public List<FieldNode> fields() {
        List<FieldNode> out = new ArrayList<>();
        for (UUID u : ctx().childrenOf(uuid())) {
            FieldNode f = tree().field(u);
            if (f != null && isCustomField(f)) {
                out.add(f);
            }
        }
        return out;
    }

    private static boolean isCustomField(FieldNode f) {
        String fn = f.fieldName();
        if (EntryFields.isPreset(fn)) {
            return false;
        }
        return !"remote".equals(f.kind());
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

    /** 删除条目及其全部字段（预设 + 自定义）。 */
    @Override
    public void delete() {
        UUID groupId = ctx().parentGroupUuid(data());
        for (String name : EntryFields.PRESET_NAMES) {
            ctx().delete(EntryFields.presetUuid(uuid(), name));
        }
        for (FieldNode f : fields()) {
            f.delete();
        }
        super.delete();
    }

    /** 写预设字段块（value 空则删除块；确定性 uuid 定位）。 */
    void writePreset(String name, String value, UUID groupId) {
        UUID pu = EntryFields.presetUuid(uuid(), name);
        if (value == null || value.isEmpty()) {
            ctx().delete(pu);
            return;
        }
        JsonObject f = new JsonObject();
        f.put("version", 1);
        f.put("type", NodeType.FIELD.tag());
        f.put("parent", uuid().toString());
        f.put("fieldName", name);
        f.put("value", value);
        ctx().write(pu, f, groupId);
    }
}
