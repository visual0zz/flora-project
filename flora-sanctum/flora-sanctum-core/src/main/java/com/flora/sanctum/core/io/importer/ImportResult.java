package com.flora.sanctum.core.io.importer;

import java.util.List;

/**
 * 导入结果统计。groups/entries/fields 为实际写入仓库的数量；warnings 为非致命告警。
 * <p>图标相关字段仅 KDBX 导入填充（用于从日志/弹窗直接定位「自定义图标缺失」类问题），
 * 其它导入器传 0 / 空列表；counts 字段统一为 0 表示「未统计」。</p>
 */
public final class ImportResult {

    public final int groups;
    public final int entries;
    public final int fields;
    public final List<String> warnings;

    /** 文件 Meta/CustomIcons 中解析到的自定义图标数量（KDBX 导入填充，其它为 0）。 */
    public final int customIconsInFile;
    /** 被条目/分组引用到的不同自定义图标 UUID 数量。 */
    public final int customIconsReferenced;
    /** 引用中被成功解析（找到图像字节并写入图标树）的数量。 */
    public final int customIconsResolved;
    /** 被引用但文件中找不到对应图像数据的数量。 */
    public final int customIconsMissing;
    /** 缺失图标对应的完整 UUID 列表（便于在源文件/日志中定位）。 */
    public final List<String> missingIconUuids;

    public ImportResult(int groups, int entries, int fields, List<String> warnings) {
        this(groups, entries, fields, warnings, 0, 0, 0, 0, List.of());
    }

    public ImportResult(int groups, int entries, int fields, List<String> warnings,
                        int customIconsInFile, int customIconsReferenced,
                        int customIconsResolved, int customIconsMissing,
                        List<String> missingIconUuids) {
        this.groups = groups;
        this.entries = entries;
        this.fields = fields;
        this.warnings = List.copyOf(warnings);
        this.customIconsInFile = customIconsInFile;
        this.customIconsReferenced = customIconsReferenced;
        this.customIconsResolved = customIconsResolved;
        this.customIconsMissing = customIconsMissing;
        this.missingIconUuids = List.copyOf(missingIconUuids);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ImportResult{groups=").append(groups)
                .append(", entries=").append(entries)
                .append(", fields=").append(fields)
                .append(", warnings=").append(warnings.size());
        if (customIconsInFile > 0 || customIconsReferenced > 0
                || customIconsResolved > 0 || customIconsMissing > 0) {
            sb.append(", 自定义图标[文件=").append(customIconsInFile)
                    .append(", 被引用=").append(customIconsReferenced)
                    .append(", 成功=").append(customIconsResolved)
                    .append(", 缺失=").append(customIconsMissing).append("]");
        }
        sb.append("}");
        return sb.toString();
    }
}
