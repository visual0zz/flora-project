package com.flora.sanctum.core.io.importer;

import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.core.io.exporter.SanctumFormatSupport;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Sanctum 自有 JSON 格式导入：读取 {@link SanctumJsonExporter} 产出的结构，重建组层级与条目。
 * <p>格式名 {@code "Sanctum JSON"}，识别扩展名 {@code .json}。导入到目标树根（保留原层级）。</p>
 */
public final class SanctumJsonImporter implements Importer {

    public static final String FORMAT = "Sanctum JSON";

    private int groups;
    private int entries;
    private int fields;
    private final List<String> warnings = new ArrayList<>();

    @Override
    public String formatName() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path file) {
        return hasExtension(file, "json");
    }

    @Override
    public ImportResult importFile(Path file, ImportContext ctx) throws ImportException {
        groups = 0;
        entries = 0;
        fields = 0;
        try {
            String text = Files.readString(file);
            JsonObject root = JsonParser.parseObject(text);
            ObjectTree tree = ctx.tree();
            JsonArray groupsArr = root.getArray("groups");
            if (groupsArr != null) {
                for (JsonValue g : groupsArr.elements()) {
                    importGroup(g.asObject(), null, tree);
                }
            }
            JsonArray entriesArr = root.getArray("entries");
            if (entriesArr != null) {
                for (JsonValue e : entriesArr.elements()) {
                    importEntry(e.asObject(), null, tree);
                }
            }
            return new ImportResult(groups, entries, fields, warnings);
        } catch (Exception ex) {
            throw new ImportException("解析 Sanctum JSON 失败：" + ex.getMessage(), ex);
        }
    }

    private void importGroup(JsonObject o, GroupNode parent, ObjectTree tree) {
        String name = str(o, "name");
        if (name == null || name.isEmpty()) {
            warnings.add("跳过无名组");
            return;
        }
        GroupNode g = parent == null ? tree.createGroup(null, name) : parent.createChildGroup(name);
        groups++;
        Ref icon = iconRefFromJson(o);
        if (icon != null) {
            g.setIcon(icon);
        }
        JsonArray entriesArr = o.getArray("entries");
        if (entriesArr != null) {
            for (JsonValue e : entriesArr.elements()) {
                importEntry(e.asObject(), g, tree);
            }
        }
        JsonArray subgroups = o.getArray("groups");
        if (subgroups != null) {
            for (JsonValue sg : subgroups.elements()) {
                importGroup(sg.asObject(), g, tree);
            }
        }
    }

    private void importEntry(JsonObject o, GroupNode parent, ObjectTree tree) {
        String name = str(o, "name");
        if (name == null || name.isEmpty()) {
            warnings.add("跳过无名条目");
            return;
        }
        var ef = SanctumFormatSupport.fieldsFromJson(o);
        EntryNode e = parent == null ? tree.createEntry(null, name, ef) : parent.createEntry(name, ef);
        entries++;
        Ref icon = iconRefFromJson(o);
        if (icon != null) {
            e.setIcon(icon);
        }
        JsonArray cf = o.getArray("customFields");
        if (cf != null) {
            for (JsonValue f : cf.elements()) {
                JsonObject fo = f.asObject();
                String fn = str(fo, "name");
                String fv = str(fo, "value");
                String fk = str(fo, "kind");
                if (fn != null && !fn.isEmpty()) {
                    e.writeField(fn, fv == null ? "" : fv, fk);
                    fields++;
                }
            }
        }
    }

    private static Ref iconRefFromJson(JsonObject o) {
        JsonValue v = o.get("iconRef");
        return v == null ? null : Ref.parse(v, "icon");
    }

    private static String str(JsonObject o, String key) {
        JsonValue v = o.get(key);
        return v == null ? null : v.asString();
    }
}
