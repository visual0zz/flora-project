package com.flora.sanctum.app.io.importer.kdbx;

import com.flora.sanctum.app.io.importer.ImportContext;
import com.flora.sanctum.app.io.importer.ImportListener;
import com.flora.sanctum.app.io.importer.ImportResult;
import com.flora.sanctum.model.EntryFields;
import com.flora.sanctum.model.tree.EntryNode;
import com.flora.sanctum.model.tree.GroupNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 把解密后的 {@link KdbxDocument} 映射进 Sanctum 的 {@code ObjectTree}。
 * <p>分组 → GroupNode（保留嵌套）；条目 → EntryNode；Title/UserName/Password/URL 走预设字段，
 * 其余（含 Notes）作为自定义字段；TOTP 字段标记 kind="totp"。图标按约定留空（不映射）。</p>
 */
final class KdbxMapper {

    private final ImportContext ctx;
    private final ImportListener listener;
    private final List<String> warnings = new ArrayList<>();
    private int groups;
    private int entries;
    private int fields;

    private KdbxMapper(ImportContext ctx) {
        this.ctx = ctx;
        this.listener = ctx.listener();
    }

    static ImportResult map(KdbxDocument doc, ImportContext ctx) {
        return new KdbxMapper(ctx).doMap(doc);
    }

    private ImportResult doMap(KdbxDocument doc) {
        GroupNode top;
        if (ctx.targetGroup() != null) {
            top = ctx.targetGroup();
        } else {
            top = ctx.tree().createGroup(null, firstNonEmpty(doc.root.name, "KeePassXC 导入"));
            groups++;
        }
        int totalEntries = doc.countEntries();
        int done = 0;
        for (KdbxDocument.KdbxGroup g : doc.root.groups) {
            groups += mapGroup(top, g);
        }
        for (KdbxDocument.KdbxEntry e : doc.root.entries) {
            entries++;
            fields += mapEntry(top, e);
            if (++done % 50 == 0) {
                listener.onProgress(done, totalEntries, "导入条目");
            }
        }
        listener.onProgress(totalEntries, totalEntries, "导入完成");
        return new ImportResult(groups, entries, fields, warnings);
    }

    private int mapGroup(GroupNode parent, KdbxDocument.KdbxGroup kg) {
        GroupNode g = parent.createChildGroup(firstNonEmpty(kg.name, "分组"));
        int n = 1;
        for (KdbxDocument.KdbxEntry e : kg.entries) {
            entries++;
            fields += mapEntry(g, e);
        }
        for (KdbxDocument.KdbxGroup c : kg.groups) {
            n += mapGroup(g, c);
        }
        return n;
    }

    private int mapEntry(GroupNode parent, KdbxDocument.KdbxEntry ke) {
        String pw = fieldValue(ke, "Password");
        String user = fieldValue(ke, "UserName");
        String url = fieldValue(ke, "URL");
        EntryFields ef = new EntryFields(pw, url, user, List.of());
        EntryNode en = parent.createEntry(firstNonEmpty(ke.name, "未命名"), ef);
        int fcount = 0;
        for (Map.Entry<String, KdbxDocument.KdbxField> me : ke.fields.entrySet()) {
            String key = me.getKey();
            if (isPresetKey(key)) {
                continue; // 已由 EntryFields 写入
            }
            KdbxDocument.KdbxField kf = me.getValue();
            String fieldName = "Notes".equalsIgnoreCase(key) ? "notes" : key;
            String kind = null;
            if (key.toLowerCase().contains("totp") || kf.value.startsWith("otpauth://")) {
                kind = "totp";
            }
            try {
                en.createField(fieldName, kf.value, kind);
                fcount++;
            } catch (IllegalArgumentException ex) {
                warnings.add("条目「" + ke.name + "」字段「" + fieldName + "」跳过：" + ex.getMessage());
            }
        }
        return fcount;
    }

    private static String fieldValue(KdbxDocument.KdbxEntry ke, String key) {
        KdbxDocument.KdbxField f = ke.fields.get(key);
        return f == null ? "" : f.value;
    }

    private static boolean isPresetKey(String key) {
        return "Title".equals(key) || "UserName".equals(key)
                || "Password".equals(key) || "URL".equals(key);
    }

    private static String firstNonEmpty(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
