package com.flora.osmetes.check;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.FileCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 基于行的检查项基类。
 * <p>
 * 统一完成 UTF-8 读取与按行切分，子类只需实现单行检查逻辑。
 * <p>
 * 读取失败（UTF-8 解码异常）时静默跳过本文件，不自行报告：文件是否"合法"取决于
 * {@code encoding} 检查项在允许编码清单（可能含非 UTF-8 编码，如 GBK）下的判定，
 * 该判定权在 {@code encoding} 一处，避免 LineCheck 子类各自重复报告或将合法的非
 * UTF-8 文件误报。因此 {@code encoding} 检查项隐式承担了"文件可解码性"的兜底职责；
 * 若 {@code encoding} 被 {@code disabledChecks} 禁用，则无法解码的文件将不再被报告
 * （解码失败检测一并被关闭），这是关闭该项时的预期副作用。
 */
public abstract class LineCheck implements FileCheck {

    @Override
    public final void check(Path file, String relativeFile, List<CheckIssue> sink) {
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return; // 读取失败由 encoding 检查项报告
        }
        String[] lines = text.split("\r?\n", -1);
        for (int i = 0; i < lines.length; i++) {
            checkLine(relativeFile, lines[i], i + 1, sink);
        }
    }

    /**
     * 检查单行文本。
     *
     * @param relativeFile 相对检查根目录的文件路径
     * @param line         该行内容（不含行终止符）
     * @param lineNo       从 1 开始的行号
     * @param sink         问题收集器
     */
    protected abstract void checkLine(String relativeFile, String line, int lineNo, List<CheckIssue> sink);
}
