package com.flora.osmetes;

import java.nio.file.Path;

/**
 * 一次检查发现的一个具体问题。
 * <p>
 * 携带问题发生的位置（相对根目录的文件路径、行号、列号）与描述，
 * 以及所属检查项与严重级别，便于聚合上报与排序。
 *
 * @param relativeFile 相对根目录的规范化文件路径（使用 {@code /} 分隔）
 * @param line         行号，从 1 开始；文件级问题可为 0
 * @param column       列号，从 1 开始；未知可为 0
 * @param check        产生该问题的检查项名称
 * @param severity     严重级别
 * @param message      人类可读的描述
 */
public record CheckIssue(
        String relativeFile,
        int line,
        int column,
        String check,
        Severity severity,
        String message) {

    /**
     * 构造一个文件级（无精确行列）问题。
     */
    public static CheckIssue file(String relativeFile, String check, Severity severity, String message) {
        return new CheckIssue(relativeFile, 0, 0, check, severity, message);
    }

    /**
     * 构造一个带精确位置的问题。
     */
    public static CheckIssue at(String relativeFile, int line, int column,
                                String check, Severity severity, String message) {
        return new CheckIssue(relativeFile, line, column, check, severity, message);
    }

    /**
     * 返回形如 {@code path:line:col} 的位置描述；行或列为 0 时省略对应部分。
     */
    public String location() {
        if (line <= 0) {
            return relativeFile;
        }
        if (column <= 0) {
            return relativeFile + ":" + line;
        }
        return relativeFile + ":" + line + ":" + column;
    }
}
