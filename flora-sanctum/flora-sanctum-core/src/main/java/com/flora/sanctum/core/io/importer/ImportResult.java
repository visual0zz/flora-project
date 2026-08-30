package com.flora.sanctum.core.io.importer;

import java.util.List;

/**
 * 导入结果统计。groups/entries/fields 为实际写入仓库的数量；warnings 为非致命告警。
 */
public final class ImportResult {

    public final int groups;
    public final int entries;
    public final int fields;
    public final List<String> warnings;

    public ImportResult(int groups, int entries, int fields, List<String> warnings) {
        this.groups = groups;
        this.entries = entries;
        this.fields = fields;
        this.warnings = List.copyOf(warnings);
    }

    @Override
    public String toString() {
        return "ImportResult{groups=" + groups + ", entries=" + entries
                + ", fields=" + fields + ", warnings=" + warnings.size() + "}";
    }
}
