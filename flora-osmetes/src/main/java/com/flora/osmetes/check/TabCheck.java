package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Severity;

import java.util.List;
import java.util.Set;

/**
 * Tab 缩进检查项：检测文本文件中是否使用 Tab 制表符缩进。
 * <p>
 * 每行报告首个 Tab 的精确行列。
 */
public final class TabCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".kts", ".gradle", ".py", ".js", ".ts", ".xml", ".yaml", ".yml");

    @Override
    public String name() {
        return "tab";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    protected void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink) {
        int col = line.indexOf('\t');
        if (col >= 0) {
            sink.add(CheckIssue.at(relativeFile, lineNo, col + 1, name(), Severity.WARNING,
                    "包含 Tab 缩进，建议改用空格"));
        }
    }
}
