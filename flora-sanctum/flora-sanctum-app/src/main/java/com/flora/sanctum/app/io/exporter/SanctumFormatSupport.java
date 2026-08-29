package com.flora.sanctum.app.io.exporter;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.model.EntryFields;
import com.flora.sanctum.model.Ref;
import com.flora.sanctum.model.tree.EntryNode;
import com.flora.sanctum.model.tree.FieldNode;
import com.flora.sanctum.model.tree.GroupNode;
import com.flora.sanctum.model.tree.ObjectTree;

import java.util.ArrayList;
import java.util.List;

/**
 * Sanctum 自有格式（CSV / JSON）共用的模型读写辅助：把 {@link EntryNode} 转 JSON、递归遍历组树为扁平条目列表。
 * <p>仅导出/导入 Sanctum 格式内部使用，不对外暴露。</p>
 */
public final class SanctumFormatSupport {

    private SanctumFormatSupport() {
    }

    /** 把一个条目写为 JSON（预设字段 + 自定义字段 + 图标引用）。 */
    static JsonObject entryJson(EntryNode e) {
        JsonObject o = new JsonObject();
        putIfPresent(o, "name", e.name());
        putIfPresent(o, "username", e.username());
        putIfPresent(o, "password", e.password());
        putIfPresent(o, "url", e.url());
        List<String> labels = e.labels();
        if (labels != null && !labels.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String l : labels) {
                arr.add(l);
            }
            o.put("labels", arr);
        }
        if (e.iconRef() != null) {
            o.put("iconRef", e.iconRef().toJson());
        }
        List<FieldNode> custom = e.fields();
        if (custom != null && !custom.isEmpty()) {
            JsonArray cf = new JsonArray();
            for (FieldNode f : custom) {
                JsonObject fo = new JsonObject();
                fo.put("name", f.fieldName());
                fo.put("value", f.value());
                if (f.kind() != null) {
                    fo.put("kind", f.kind());
                }
                cf.add(fo);
            }
            o.put("customFields", cf);
        }
        return o;
    }

    /** 递归把组写为 JSON（含子组与条目）。 */
    static JsonObject groupJson(GroupNode g) {
        JsonObject o = new JsonObject();
        putIfPresent(o, "name", g.name());
        if (g.iconRef() != null) {
            o.put("iconRef", g.iconRef().toJson());
        }
        JsonArray entries = new JsonArray();
        for (EntryNode e : g.entries()) {
            entries.add(entryJson(e));
        }
        if (entries.size() > 0) {
            o.put("entries", entries);
        }
        JsonArray subgroups = new JsonArray();
        for (GroupNode child : g.childGroups()) {
            subgroups.add(groupJson(child));
        }
        if (subgroups.size() > 0) {
            o.put("groups", subgroups);
        }
        return o;
    }

    /** 扁平遍历：对每个条目回调其组路径（从根到父组的名称序列）与条目本身。 */
    static void walkEntries(ObjectTree tree, EntryConsumer consumer) {
        for (GroupNode g : tree.rootGroups()) {
            walkGroup(g, new ArrayList<>(), consumer);
        }
        for (EntryNode e : tree.rootEntries()) {
            consumer.accept(List.of(), e);
        }
    }

    private static void walkGroup(GroupNode g, List<String> path, EntryConsumer consumer) {
        List<String> here = new ArrayList<>(path);
        here.add(g.name());
        for (EntryNode e : g.entries()) {
            consumer.accept(here, e);
        }
        for (GroupNode child : g.childGroups()) {
            walkGroup(child, here, consumer);
        }
    }

    @FunctionalInterface
    interface EntryConsumer {
        void accept(List<String> groupPath, EntryNode entry);
    }

    private static void putIfPresent(JsonObject o, String key, String value) {
        if (value != null && !value.isEmpty()) {
            o.put(key, value);
        }
    }

    /** 从 JSON 构造条目预设字段（供导入器复用）。 */
    public static EntryFields fieldsFromJson(JsonObject o) {
        String password = str(o, "password");
        String url = str(o, "url");
        String username = str(o, "username");
        List<String> labels = new ArrayList<>();
        JsonArray arr = o.getArray("labels");
        if (arr != null) {
            for (JsonValue v : arr.elements()) {
                labels.add(v.asString());
            }
        }
        return new EntryFields(password, url, username, labels);
    }

    /** 从 JSON 读图标引用（可能为 null）。 */
    public static Ref iconRefFromJson(JsonObject o) {
        JsonValue v = o.get("iconRef");
        return v == null ? null : Ref.parse(v, "icon");
    }

    public static String str(JsonObject o, String key) {
        JsonValue v = o.get(key);
        return v == null ? null : v.asString();
    }
}
