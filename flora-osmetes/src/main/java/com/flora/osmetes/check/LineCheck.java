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
 * 统一完成 UTF-8 读取与按行切分，子类只需实现单行检查逻辑；
 * 读取失败由 {@code encoding} 检查项负责报告，此处静默跳过。
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
