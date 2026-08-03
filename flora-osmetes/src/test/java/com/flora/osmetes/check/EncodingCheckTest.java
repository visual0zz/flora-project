package com.flora.osmetes.check;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.flora.osmetes.CheckIssue;
import com.flora.osmetes.FileCheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EncodingCheck} 的可配置编码能力测试：默认仅 UTF-8，可通过
 * {@code encoding.allowed} 扩展允许的编码，并按任一允许编码解码后检测 C1 控制符。
 */
class EncodingCheckTest {

    @TempDir
    Path tmp;

    private EncodingCheck configured(String... allowed) {
        EncodingCheck check = new EncodingCheck();
        if (allowed.length == 0) {
            check.configure(Map.of());
        } else {
            check.configure(Map.of(EncodingCheck.CONFIG_ALLOWED, String.join(";", allowed)));
        }
        return check;
    }

    private List<CheckIssue> run(EncodingCheck check, byte[] bytes) throws IOException {
        Path file = tmp.resolve("Sample.java");
        Files.write(file, bytes);
        List<CheckIssue> sink = new ArrayList<>();
        check.check(file, "Sample.java", sink);
        return sink;
    }

    @Test
    void defaultOnlyUtf8AcceptsValidUtf8() throws IOException {
        List<CheckIssue> issues = run(configured(), "class A { }".getBytes(StandardCharsets.UTF_8));
        assertTrue(issues.isEmpty(), "合法 UTF-8 不应报告: " + issues);
    }

    @Test
    void defaultRejectsInvalidUtf8() throws IOException {
        // 0xFF 0xFE 不是合法 UTF-8 序列
        List<CheckIssue> issues = run(configured(), new byte[]{(byte) 0xFF, (byte) 0xFE});
        assertEquals(1, issues.size(), "非法 UTF-8 应报错: " + issues);
        assertTrue(issues.getFirst().message().contains("允许的编码"),
                "错误应列出允许的编码: " + issues.getFirst().message());
    }

    @Test
    void gbkAllowedAcceptsGbkFile() throws IOException {
        // "中文" 的 GBK 编码（非合法 UTF-8 字节序列）
        byte[] gbk = {(byte) 0xD6, (byte) 0xD0, (byte) 0xCE, (byte) 0xC4};
        List<CheckIssue> issues = run(configured("UTF-8", "GBK"), gbk);
        assertTrue(issues.isEmpty(), "允许 GBK 时 GBK 文件应通过: " + issues);
    }

    @Test
    void onlyUtf8RejectsGbkFile() throws IOException {
        byte[] gbk = {(byte) 0xD6, (byte) 0xD0, (byte) 0xCE, (byte) 0xC4};
        List<CheckIssue> issues = run(configured("UTF-8"), gbk);
        assertEquals(1, issues.size(), "仅 UTF-8 时 GBK 字节应报错: " + issues);
    }

    @Test
    void unknownEncodingNameFallsBackToUtf8() throws IOException {
        // 无法识别的编码名应被忽略并回退到 UTF-8 默认行为
        List<CheckIssue> issues = run(configured("NOT-A-CHARSET"), new byte[]{(byte) 0xFF});
        assertEquals(1, issues.size(), "未知编码回退到 UTF-8 后应报错: " + issues);
    }

    @Test
    void c1ControlReportedAfterDecoding() throws IOException {
        // U+0085（下一行）在 UTF-8 下为 0xC2 0x85，解码后应被 C1 检查捕获
        String text = "class A {\u0085\n}";
        List<CheckIssue> issues = run(configured(), text.getBytes(StandardCharsets.UTF_8));
        assertFalse(issues.isEmpty(), "含 C1 控制符应被报告: " + issues);
        assertTrue(issues.stream().anyMatch(i -> i.message().contains("C1 控制字符")),
                "应报告 C1 控制字符: " + issues);
        assertEquals(1, issues.getFirst().line(), "U+0085 位于第 1 行");
    }

    @Test
    void implementsFileCheckContract() {
        FileCheck check = new EncodingCheck();
        assertEquals("encoding", check.name());
        assertFalse(check.fileExtensions().isEmpty(), "应声明参与检查的后缀名");
    }
}
