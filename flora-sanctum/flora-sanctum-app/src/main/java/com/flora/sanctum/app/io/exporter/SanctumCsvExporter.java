package com.flora.sanctum.app.io.exporter;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.model.EntryFields;
import com.flora.sanctum.model.tree.EntryNode;
import com.flora.sanctum.model.tree.FieldNode;
import com.flora.sanctum.model.tree.ObjectTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Sanctum 自有 CSV 格式导出：扁平表，每行一个条目。
 * <p>列：{@code groupPath,name,username,password,url,labels,iconRef,customFields}。
 * {@code groupPath} 用 {@code /} 连接组层级；{@code labels} 用 {@code , } 连接；
 * {@code iconRef} 与 {@code customFields} 以 JSON 文本存储（避免分隔符冲突）。字段按 RFC 4180 双引号转义。</p>
 */
public final class SanctumCsvExporter implements Exporter {

    public static final String FORMAT = "Sanctum CSV";

    private static final String[] HEADER = {
            "groupPath", "name", "username", "password", "url", "labels", "iconRef", "customFields"};

    @Override
    public String formatName() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path file) {
        return hasExtension(file, "csv");
    }

    @Override
    public void exportTo(Path file, ObjectTree tree) throws ExportException {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append(String.join(",", HEADER)).append('\n');
            SanctumFormatSupport.walkEntries(tree, (path, e) -> {
                String groupPath = String.join("/", path);
                String labels = EntryFields.labelsToString(e.labels());
                String iconRef = e.iconRef() == null ? "" : e.iconRef().toJson().toString();
                sb.append(csvField(groupPath)).append(',')
                        .append(csvField(e.name() == null ? "" : e.name())).append(',')
                        .append(csvField(blank(e.username()))).append(',')
                        .append(csvField(blank(e.password()))).append(',')
                        .append(csvField(blank(e.url()))).append(',')
                        .append(csvField(labels)).append(',')
                        .append(csvField(iconRef)).append(',')
                        .append(csvField(customFieldsJson(e.fields()))).append('\n');
            });
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new ExportException("导出 Sanctum CSV 失败：" + ex.getMessage(), ex);
        }
    }

    /** 自定义字段序列化为 JSON 数组串（空则空串）。 */
    private static String customFieldsJson(List<FieldNode> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        JsonArray arr = new JsonArray();
        for (FieldNode f : fields) {
            JsonObject o = new JsonObject();
            o.put("name", f.fieldName());
            o.put("value", f.value());
            if (f.kind() != null) {
                o.put("kind", f.kind());
            }
            arr.add(o);
        }
        return arr.toString();
    }

    private static String blank(String s) {
        return s == null ? "" : s;
    }

    /** RFC 4180 字段转义：含逗号/引号/换行则双引号包裹，内部引号翻倍。 */
    private static String csvField(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
