package com.flora.sanctum.core.io.exporter;

import com.flora.root.codec.json.model.JsonArray;
import com.flora.root.codec.json.model.JsonObject;
import com.flora.sanctum.core.model.tree.ObjectTree;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sanctum 自有 JSON 格式导出：保留完整组层级与条目（预设字段 + 自定义字段 + 图标引用）。
 * <p>格式名 {@code "Sanctum JSON"}，文件扩展名 {@code .json}。</p>
 */
public final class SanctumJsonExporter implements Exporter {

    public static final String FORMAT = "Sanctum JSON";

    @Override
    public String formatName() {
        return FORMAT;
    }

    @Override
    public boolean supports(Path file) {
        return hasExtension(file, "json");
    }

    @Override
    public void exportTo(Path file, ObjectTree tree) throws ExportException {
        try {
            JsonObject root = new JsonObject();
            root.put("format", "sanctum-json");
            root.put("version", 1);
            JsonArray groups = new JsonArray();
            for (com.flora.sanctum.core.model.tree.GroupNode g : tree.rootGroups()) {
                groups.add(SanctumFormatSupport.groupJson(g));
            }
            root.put("groups", groups);
            JsonArray entries = new JsonArray();
            for (com.flora.sanctum.core.model.tree.EntryNode e : tree.rootEntries()) {
                entries.add(SanctumFormatSupport.entryJson(e));
            }
            root.put("entries", entries);
            Files.writeString(file, root.toString());
        } catch (Exception ex) {
            throw new ExportException("导出 Sanctum JSON 失败：" + ex.getMessage(), ex);
        }
    }
}
