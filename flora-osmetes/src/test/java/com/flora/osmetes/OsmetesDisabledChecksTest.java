package com.flora.osmetes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code disabledChecks} 关闭检查项能力的单元测试：覆盖名称禁用、
 * 多名称并集、分隔符解析与空串行为。
 */
class OsmetesDisabledChecksTest {

    @TempDir
    Path tmp;

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** 含 tab 与行尾空白的 Java 文件，用于同时触发两类检查。 */
    private Path writeMixed() throws IOException {
        Path f = tmp.resolve("Sample.java");
        write(f, "class Sample {\n"
                + "\tint a = 1; \n"
                + "}\n");
        return f;
    }

    private List<CheckIssue> runWith(Set<String> disabled) throws IOException {
        return Osmetes.run(tmp, Osmetes.builtinChecks(), "", disabled);
    }

    @Test
    void disablingWhitetailKeepsTab() throws IOException {
        writeMixed();
        List<CheckIssue> issues = runWith(Set.of("whitetail"));
        assertFalse(issues.stream().anyMatch(i -> i.check().equals("whitetail")),
                "whitetail 应被关闭: " + issues);
        assertEquals(1, issues.stream().filter(i -> i.check().equals("tab")).count(),
                "tab 仍应报告: " + issues);
    }

    @Test
    void disablingTabKeepsWhitetail() throws IOException {
        writeMixed();
        List<CheckIssue> issues = runWith(Set.of("tab"));
        assertFalse(issues.stream().anyMatch(i -> i.check().equals("tab")),
                "tab 应被关闭: " + issues);
        assertEquals(1, issues.stream().filter(i -> i.check().equals("whitetail")).count(),
                "whitetail 仍应报告: " + issues);
    }

    @Test
    void disablingMultipleChecksViaUnion() throws IOException {
        writeMixed();
        // 两类检查同时关闭后不应再产生任何问题
        List<CheckIssue> issues = runWith(Set.of("tab", "whitetail"));
        assertTrue(issues.isEmpty(), "tab 与 whitetail 均关闭后应为空: " + issues);
    }

    @Test
    void emptyDisabledSetRunsEverything() throws IOException {
        writeMixed();
        List<CheckIssue> issues = runWith(Set.of());
        assertEquals(2, issues.size(), "未禁用时 tab 与 whitetail 都应报告: " + issues);
    }

    @Test
    void unknownNameIsIgnoredSilently() throws IOException {
        writeMixed();
        // 不存在的名称不影响其余检查的执行
        List<CheckIssue> issues = runWith(Set.of("nonexistent"));
        assertEquals(2, issues.size(), "未知名称应被忽略: " + issues);
    }

    @Test
    void parseNamesSplitsByAnyDelimiter() {
        assertEquals(Set.of("secret", "tab"),
                Osmetes.parseNames("secret;tab"));
        assertEquals(Set.of("secret", "tab", "whitetail", "encoding"),
                Osmetes.parseNames("secret,tab|whitetail &encoding"),
                "逗号/分号/竖线/& 都应作为分隔符");
        assertEquals(Set.of("whitetail"),
                Osmetes.parseNames("  whitetail  "), "首尾空白应被去除");
    }

    @Test
    void parseNamesHandlesEmptyAndNull() {
        assertTrue(Osmetes.parseNames("").isEmpty(), "空串应解析为空集");
        assertTrue(Osmetes.parseNames(null).isEmpty(), "null 应解析为空集");
        assertTrue(Osmetes.parseNames(" ; , ").isEmpty(), "仅分隔符应解析为空集");
    }
}
