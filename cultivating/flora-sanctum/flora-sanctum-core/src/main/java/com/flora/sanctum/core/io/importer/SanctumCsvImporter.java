package com.flora.sanctum.core.io.importer;

import com.flora.root.codec.json.JsonParser;
import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.root.codec.json.model.JsonValue;
import com.flora.sanctum.core.model.EntryFields;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.ObjectTree;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sanctum 自有 CSV 格式导入：读取 {@link com.flora.sanctum.core.io.exporter.SanctumCsvExporter} 产出的扁平表，
 * 按 {@code groupPath} 逐级重建组，再写回条目（预设字段 + 自定义字段 + 图标引用）。
 * <p>格式名 {@code "Sanctum CSV"}，识别扩展名 {@code .csv}。</p>
 */
public final class SanctumCsvImporter implements Importer {

    public static final String FORMAT = "Sanctum CSV";

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
        return hasExtension(file, "csv");
    }

    @Override
    public ImportResult importFile(Path file, ImportContext ctx) throws ImportException {
        groups = 0;
        entries = 0;
        fields = 0;
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                throw new ImportException("CSV 文件为空");
            }
            List<String> header = parseCsvLine(lines.get(0));
            int idxGroup = header.indexOf("groupPath");
            int idxName = header.indexOf("name");
            int idxUser = header.indexOf("username");
            int idxPass = header.indexOf("password");
            int idxUrl = header.indexOf("url");
            int idxLabels = header.indexOf("labels");
            int idxIcon = header.indexOf("iconRef");
            int idxCustom = header.indexOf("customFields");
            if (idxName < 0) {
                throw new ImportException("CSV 缺少 name 列");
            }
            ObjectTree tree = ctx.tree();
            Map<String, GroupNode> groupCache = new LinkedHashMap<>();
            for (int i = 1; i < lines.size(); i++) {
                if (lines.get(i).isBlank()) {
                    continue;
                }
                List<String> row = parseCsvLine(lines.get(i));
                String name = safe(row, idxName);
                if (name == null || name.isEmpty()) {
                    warnings.add("跳过无名条目（第 " + (i + 1) + " 行）");
                    continue;
                }
                GroupNode parent = resolveGroup(safe(row, idxGroup), tree, groupCache);
                String password = safe(row, idxPass);
                String url = safe(row, idxUrl);
                String username = safe(row, idxUser);
                List<String> labels = new ArrayList<>();
                String labelsStr = safe(row, idxLabels);
                if (labelsStr != null && !labelsStr.isBlank()) {
                    for (String p : labelsStr.split(",")) {
                        String t = p.trim();
                        if (!t.isEmpty()) {
                            labels.add(t);
                        }
                    }
                }
                EntryFields ef = new EntryFields(password, url, username, labels);
                EntryNode e = parent == null ? tree.createEntry(null, name, ef) : parent.createEntry(name, ef);
                entries++;
                String iconStr = safe(row, idxIcon);
                if (iconStr != null && !iconStr.isBlank()) {
                    try {
                        Ref icon = Ref.parse(JsonParser.parseObject(iconStr), "icon");
                        e.setIcon(icon);
                    } catch (Exception ex) {
                        warnings.add("第 " + (i + 1) + " 行图标引用解析失败，已忽略");
                    }
                }
                String customStr = safe(row, idxCustom);
                if (customStr != null && !customStr.isBlank()) {
                    try {
                        JsonArray cf = JsonParser.parseArray(customStr);
                        for (JsonValue fv : cf.elements()) {
                            JsonObject fo = fv.asObject();
                            String fn = str(fo, "name");
                            String fval = str(fo, "value");
                            String fk = str(fo, "kind");
                            if (fn != null && !fn.isEmpty()) {
                                e.writeField(fn, fval == null ? "" : fval, fk);
                                fields++;
                            }
                        }
                    } catch (Exception ex) {
                        warnings.add("第 " + (i + 1) + " 行自定义字段解析失败，已忽略");
                    }
                }
            }
            return new ImportResult(groups, entries, fields, warnings);
        } catch (Exception ex) {
            throw new ImportException("解析 Sanctum CSV 失败：" + ex.getMessage(), ex);
        }
    }

    /** 按 {@code /} 连接的组路径逐级定位/创建组（缓存避免重复创建）。 */
    private GroupNode resolveGroup(String groupPath, ObjectTree tree, Map<String, GroupNode> cache) {
        if (groupPath == null || groupPath.isBlank()) {
            return null;
        }
        GroupNode cached = cache.get(groupPath);
        if (cached != null) {
            return cached;
        }
        String[] parts = groupPath.split("/");
        GroupNode parent = null;
        StringBuilder acc = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (acc.length() > 0) {
                acc.append('/');
            }
            acc.append(part);
            GroupNode existing = cache.get(acc.toString());
            if (existing == null) {
                existing = parent == null ? tree.createGroup(null, part) : parent.createChildGroup(part);
                groups++;
                cache.put(acc.toString(), existing);
            }
            parent = existing;
        }
        return parent;
    }

    private static String safe(List<String> row, int idx) {
        return idx < 0 || idx >= row.size() ? null : row.get(idx);
    }

    private static String str(JsonObject o, String key) {
        JsonValue v = o.get(key);
        return v == null ? null : v.asString();
    }

    /** RFC 4180 单行解析：双引号包裹字段、内部双引号翻倍。 */
    private static List<String> parseCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out;
    }
}
