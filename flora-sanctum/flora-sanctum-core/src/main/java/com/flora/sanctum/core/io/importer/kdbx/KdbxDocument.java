package com.flora.sanctum.core.io.importer.kdbx;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * KDBX4 解密后的内存模型（与具体加密无关，仅描述分组/条目/字段）。
 * <p>字段值已是明文（受保护字段已用内层流解密）。时间字段为 epoch 毫秒，解析失败为 null。</p>
 */
public final class KdbxDocument {

    public final KdbxGroup root;
    /** 文档级自定义图标：CustomIconUUID 的 hex → 原始图像字节（PNG/JPG/GIF 等）。 */
    public final Map<String, byte[]> customIcons;

    public KdbxDocument(KdbxGroup root) {
        this(root, Map.of());
    }

    public KdbxDocument(KdbxGroup root, Map<String, byte[]> customIcons) {
        this.root = root;
        this.customIcons = customIcons;
    }

    /** 扁平统计（含嵌套）。 */
    public int countGroups() {
        return root == null ? 0 : countGroups(root);
    }

    public int countEntries() {
        return root == null ? 0 : countEntries(root);
    }

    private static int countGroups(KdbxGroup g) {
        int n = 1;
        for (KdbxGroup c : g.groups) {
            n += countGroups(c);
        }
        return n;
    }

    private static int countEntries(KdbxGroup g) {
        int n = g.entries.size();
        for (KdbxGroup c : g.groups) {
            n += countEntries(c);
        }
        return n;
    }

    /** KeePass 分组（可嵌套）。 */
    public static final class KdbxGroup {
        public String name = "";
        public String uuid;                       // 原 16 字节 uuid 的 hex，仅作参考
        public Integer iconId;                    // 内置图标索引（可空）
        public String customIconUuid;             // 自定义图标 UUID hex（可空）
        public final List<KdbxGroup> groups = new ArrayList<>();
        public final List<KdbxEntry> entries = new ArrayList<>();
    }

    /** KeePass 条目。 */
    public static final class KdbxEntry {
        public String name = "";
        public String uuid;
        public Integer iconId;                    // 内置图标索引（可空）
        public String customIconUuid;             // 自定义图标 UUID hex（可空）
        /** 字段名 → 字段（保持 XML 出现顺序，便于内层流顺序解密）。 */
        public final Map<String, KdbxField> fields = new LinkedHashMap<>();
        public Long creationTime;
        public Long lastModificationTime;
    }

    /** 条目字段。 */
    public static final class KdbxField {
        public String value = "";
        /** 原 XML 是否 Protected（仅用于 TOTP 等识别，不影响落库）。 */
        public final boolean protectedValue;

        public KdbxField(String value, boolean protectedValue) {
            this.value = value;
            this.protectedValue = protectedValue;
        }
    }
}
