package com.flora.sanctum.core.io.importer.kdbx;

import com.flora.sanctum.core.icon.BuiltinIcons;
import com.flora.sanctum.core.io.importer.ImportContext;
import com.flora.sanctum.kdbx.KdbxDocument;
import com.flora.sanctum.core.io.importer.ImportListener;
import com.flora.sanctum.core.io.importer.ImportResult;
import com.flora.sanctum.core.model.EntryFields;
import com.flora.sanctum.core.model.Ref;
import com.flora.sanctum.core.model.tree.EntryNode;
import com.flora.sanctum.core.model.tree.GroupNode;
import com.flora.sanctum.core.model.tree.IconNode;
import com.flora.sanctum.core.model.tree.IconTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 把解密后的 {@link KdbxDocument} 映射进 Sanctum 的 {@code ObjectTree}。
 * <p>分组 → GroupNode（保留嵌套）；条目 → EntryNode；Title/UserName/Password/URL 走预设字段，
 * 其余（含 Notes）作为自定义字段；TOTP 字段标记 kind="totp"。
 * 图标：KeePass 自定义图标（CustomIconUUID）按 UUID 去重后复制进 iconTree 并建 node 引用；
 * 内置图标（IconID）在本轮导入内按 iconId 稳定地随机映射到一个 Sanctum 内置图标（builtin 引用）。</p>
 */
final class KdbxMapper {

    private final ImportContext importContext;
    private final ImportListener listener;
    private final List<String> warnings = new ArrayList<>();
    private int groups;
    private int entries;
    private int fields;

    /** 文档级自定义图标字节（来自 KDBX 的 Meta/CustomIcons，无论是否启用图标映射都填充用于统计）。 */
    private Map<String, byte[]> customIcons = Map.of();
    /** 文件 Meta/CustomIcons 中解析到的自定义图标数量（用于导入统计）。 */
    private int customIconsInFile;
    /** iconTree 非 null 时启用图标映射：自定义图标 UUID → 已建好的引用（去重）。 */
    private final Map<String, Ref> customIconRefs = new HashMap<>();
    /** 内置图标 iconId → 本轮稳定映射的 Sanctum 内置图标引用。 */
    private final Map<Integer, Ref> builtinIconRefs = new HashMap<>();
    /** 已告警过的缺失自定义图标 UUID（避免引用同一图标的每个条目都重复告警），保持首次出现顺序。 */
    private final java.util.Set<String> missingIconUuids = new java.util.LinkedHashSet<>();

    private KdbxMapper(ImportContext ctx) {
        this.importContext = ctx;
        this.listener = ctx.listener();
    }

    /**
     * 记录一条非致命告警：既计入结果统计，也即时推送给监听器
     * （应用侧经 {@code ImportListeners.logging(...)} 落到日志文件，避免只看到告警条数而不见内容）。
     */
    private void warn(String message) {
        warnings.add(message);
        listener.onWarning(message);
    }

    static ImportResult map(KdbxDocument doc, ImportContext ctx) {
        return new KdbxMapper(ctx).doMap(doc);
    }

    private ImportResult doMap(KdbxDocument doc) {
        this.customIcons = doc.customIcons;
        this.customIconsInFile = doc.customIcons.size();
        GroupNode top;
        if (importContext.targetGroup() != null) {
            top = importContext.targetGroup();
        } else {
            top = importContext.tree().createGroup(null, firstNonEmpty(doc.root.name, "KeePassXC 导入"));
            groups++;
        }
        int totalEntries = doc.countEntries();
        listener.onInfo("开始映射 KDBX 文档：根分组=" + top.name()
                + "，预计条目=" + totalEntries + "，文件含自定义图标=" + customIconsInFile);
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

        int referenced = customIconRefs.size();
        int resolved = 0;
        for (Ref r : customIconRefs.values()) {
            if (r != null) {
                resolved++;
            }
        }
        int missing = missingIconUuids.size();
        if (referenced > 0 || customIconsInFile > 0) {
            String detail = missing > 0 ? "；缺失 UUID：" + String.join(", ", missingIconUuids) : "";
            listener.onInfo("KDBX 导入图标统计：文件含 " + customIconsInFile + " 个自定义图标；被引用 "
                    + referenced + " 个（成功解析 " + resolved + " 个，缺失 " + missing + " 个）" + detail);
        }
        return new ImportResult(groups, entries, fields, warnings,
                customIconsInFile, referenced, resolved, missing, new ArrayList<>(missingIconUuids));
    }

    private int mapGroup(GroupNode parent, KdbxDocument.KdbxGroup kg) {
        GroupNode g = parent.createChildGroup(firstNonEmpty(kg.name, "分组"));
        Ref icon = resolveIcon(kg.iconId, kg.customIconUuid, kg.name);
        if (icon != null) {
            g.setIcon(icon);
        }
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
        Ref icon = resolveIcon(ke.iconId, ke.customIconUuid, ke.name);
        if (icon != null) {
            en.setIcon(icon);
        }
        int fcount = 0;
        for (Map.Entry<String, KdbxDocument.KdbxField> me : ke.fields.entrySet()) {
            String key = me.getKey();
            if (isPresetKey(key)) {
                continue; // 已由 EntryFields 写入
            }
            KdbxDocument.KdbxField kf = me.getValue();
            // KeePassXC 的 Notes 转译为内置备注字段（预设块），而非附加自定义字段
            if ("Notes".equalsIgnoreCase(key)) {
                try {
                    en.setNotes(kf.value);
                } catch (Exception ex) {
                    warn("条目「" + ke.name + "」备注跳过：" + ex.getMessage());
                }
                continue;
            }
            String fieldName = key;
            String kind = null;
            if (key.toLowerCase().contains("totp") || kf.value.startsWith("otpauth://")) {
                kind = "totp";
            }
            try {
                en.writeField(fieldName, kf.value, kind);
                fcount++;
            } catch (IllegalArgumentException ex) {
                warn("条目「" + ke.name + "」字段「" + fieldName + "」跳过：" + ex.getMessage());
            }
        }
        return fcount;
    }

    /**
     * 解析 KeePass 的图标引用为本导入可用的 Sanctum 图标引用：
     * 自定义图标（CustomIconUUID）→ 复制进 iconTree 并建稳定 node 引用（按 UUID 去重）；
     * 内置图标（IconID）→ 本轮内按 iconId 稳定地随机选一个 Sanctum 内置图标（builtin 引用）；
     * 两者皆无 / 未提供 iconTree → null（不设置图标）。
     */
    private Ref resolveIcon(Integer iconId, String customIconUuid, String ownerName) {
        IconTree iconTree = importContext.iconTree();
        if (iconTree == null) {
            return null;
        }
        if (customIconUuid != null) {
            Ref cached = customIconRefs.get(customIconUuid);
            if (cached != null || customIconRefs.containsKey(customIconUuid)) {
                return cached; // 已尝试解析（含 null，表示没有对应字节）
            }
            byte[] data = customIcons.get(customIconUuid);
            Ref ref = null;
            if (data != null && data.length > 0) {
                String name = "kp-" + customIconUuid.substring(0, Math.min(8, customIconUuid.length()));
                // 按内容去重：重复导入同一份文件（或库内已有相同字节的图标）时复用，不产生副本
                IconNode node = iconTree.findOrCreate(name, data, formatOf(data));
                ref = Ref.nodeIcon(node.uuid());
            } else if (missingIconUuids.add(customIconUuid)) {
                String where = ownerName == null || ownerName.isEmpty() ? "某条目/分组" : "「" + ownerName + "」";
                warn("自定义图标缺失：CustomIconUUID " + customIconUuid
                        + " 在文件 Meta/CustomIcons 中找不到对应图像数据，引用它的 " + where + " 不会设置图标");
            }
            customIconRefs.put(customIconUuid, ref);
            return ref;
        }
        if (iconId != null) {
            Ref cached = builtinIconRefs.get(iconId);
            if (cached != null) {
                return cached;
            }
            List<String> libs = BuiltinIcons.names();
            Ref ref = null;
            if (!libs.isEmpty()) {
                int idx = Math.floorMod(iconId, libs.size());
                ref = Ref.builtinIcon(libs.get(idx));
            }
            builtinIconRefs.put(iconId, ref);
            return ref;
        }
        return null;
    }

    /** 按文件头魔数判定图像格式（png/jpg/gif/bmp），未知回落到 png。 */
    private static String formatOf(byte[] data) {
        if (data.length >= 4
                && (data[0] & 0xff) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "png";
        }
        if (data.length >= 3 && (data[0] & 0xff) == 0xFF && data[1] == 0xD8) {
            return "jpg";
        }
        if (data.length >= 3 && data[0] == 0x47 && data[1] == 0x49 && data[2] == 0x46) {
            return "gif";
        }
        if (data.length >= 2 && data[0] == 0x42 && data[1] == 0x4D) {
            return "bmp";
        }
        return "png";
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
