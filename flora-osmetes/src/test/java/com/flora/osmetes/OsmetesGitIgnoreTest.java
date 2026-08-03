package com.flora.osmetes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Osmetes} 扫描与 gitignore 忽略范围的集成测试。
 * <p>
 * 用临时目录模拟真实仓库：根 {@code .gitignore} 排除目录与通配文件，
 * 验证被忽略文件不再产生检查问题，而取反重新包含的文件仍被检查。
 */
class OsmetesGitIgnoreTest {

    @TempDir
    Path tmp;

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /** 一个带 tab 缩进的 java 文件，Tab 检查项会命中（WARNING 级别）。 */
    private String javaWithTab(String className) {
        return "public class " + className + " {\n"
                + "\tpublic void m() {}\n"
                + "}\n";
    }

    private List<CheckIssue> tabIssues(Path root) throws IOException {
        return Osmetes.run(root, Osmetes.builtinChecks()).stream()
                .filter(i -> i.check().equals("tab"))
                .toList();
    }

    @Test
    void ignoredFilesProduceNoIssues() throws IOException {
        write(tmp.resolve(".gitignore"),
                "target/\n"
                        + "*.java\n"
                        + "!src/Keep.java\n");
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));        // 命中 *.java → 忽略
        write(tmp.resolve("src/Keep.java"), javaWithTab("Keep"));        // 取反 → 重新包含
        write(tmp.resolve("target/Gen.java"), javaWithTab("Gen"));       // target/ → 目录剪枝

        List<CheckIssue> issues = tabIssues(tmp);

        assertEquals(1, issues.size(), "只有被取反的 Keep.java 应产生 tab 告警: " + issues);
        assertEquals("src/Keep.java", issues.getFirst().relativeFile());
    }

    @Test
    void excludedDirWithInnerNegationCannotReinclude() throws IOException {
        write(tmp.resolve(".gitignore"), "build/\n");
        write(tmp.resolve("build/.gitignore"), "!keep.java\n");
        write(tmp.resolve("build/keep.java"), javaWithTab("Keep"));
        write(tmp.resolve("src/Ok.java"), javaWithTab("Ok"));

        List<CheckIssue> issues = tabIssues(tmp);

        assertEquals(1, issues.size(), "被排除目录内的取反不应生效: " + issues);
        assertEquals("src/Ok.java", issues.getFirst().relativeFile());
    }

    @Test
    void noGitIgnoreMeansEverythingChecked() throws IOException {
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));
        write(tmp.resolve("src/Sub/Deep.java"), javaWithTab("Deep"));

        List<CheckIssue> issues = tabIssues(tmp);

        assertEquals(2, issues.size(), "无 .gitignore 时全部文件都应被检查: " + issues);
    }

    @Test
    void dotGitDirectoryAlwaysSkipped() throws IOException {
        write(tmp.resolve(".git/config"), "tab here:\tvalue\n");
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));

        List<CheckIssue> issues = tabIssues(tmp);

        assertEquals(1, issues.size(), ".git 内部文件不应被检查: " + issues);
        assertEquals("src/Main.java", issues.getFirst().relativeFile());
    }
}
