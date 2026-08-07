package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.Severity;

import java.util.List;
import java.util.Set;

/**
 * 行尾空白检查项（名称 {@code trailing-whitespace}）：检测文本文件每行末尾的多余空白字符（空格/Tab）。
 * <p>
 * 报告精确位置并收集一个文件内的全部命中。
 */
public final class TrailingWhitespaceCheck extends LineCheck {

    private static final Set<String> EXTENSIONS = Set.of(
            ".java", ".ramet", ".xml", ".properties", ".yaml", ".yml",
            ".json", ".md", ".txt", ".sh", ".cmd", ".bat", ".ps1", ".kts", ".gradle");

    @Override
    public String name() {
        return "trailing-whitespace";
    }

    @Override
    public Set<String> fileExtensions() {
        return EXTENSIONS;
    }

    @Override
    protected void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink) {
        int trailStart = line.length();
        while (trailStart > 0) {
            char c = line.charAt(trailStart - 1);
            if (c != ' ' && c != '\t') {
                break;
            }
            trailStart--;
        }
        if (trailStart < line.length()) {
            sink.add(CheckIssue.at(relativeFile, lineNo, trailStart + 1, name(),
                    Severity.WARNING, "行尾包含多余空白"));
        }
    }
}
