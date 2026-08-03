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
 * 可配置忽略规则（{@code ignorePatterns}）在 {@link Osmetes} 扫描中的集成测试。
 */
class OsmetesIgnorePatternsTest {

    @TempDir
    Path tmp;

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private String javaWithTab(String className) {
        return "public class " + className + " {\n"
                + "\tpublic void m() {}\n"
                + "}\n";
    }

    private List<CheckIssue> tabIssues(String ignorePatterns) throws IOException {
        return Osmetes.run(tmp, Osmetes.builtinChecks(), ignorePatterns).stream()
                .filter(i -> i.check().equals("tab"))
                .toList();
    }

    @Test
    void configuredPatternsIgnoreMatchingPaths() throws IOException {
        write(tmp.resolve("absent/Other.java"), javaWithTab("Other"));
        write(tmp.resolve("deep/absent/x/Log.java"), javaWithTab("Log")); // 嵌套 absent 目录
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));

        List<CheckIssue> issues = tabIssues("absent/;*.log");

        assertEquals(1, issues.size(), "仅 src/Main.java 应被检查: " + issues);
        assertEquals("src/Main.java", issues.getFirst().relativeFile());
    }

    @Test
    void configuredPatternsSupportNegation() throws IOException {
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));
        write(tmp.resolve("src/Keep.java"), javaWithTab("Keep"));

        List<CheckIssue> issues = tabIssues("*.java;!src/Keep.java");

        assertEquals(1, issues.size(), "仅 src/Keep.java 应被检查: " + issues);
        assertEquals("src/Keep.java", issues.getFirst().relativeFile());
    }

    @Test
    void configuredPatternsOverrideGitIgnore() throws IOException {
        // .gitignore 用取反重新包含 Keep.java，但显式配置 *.java 优先级更高
        write(tmp.resolve(".gitignore"), "!src/Keep.java\n");
        write(tmp.resolve("src/Keep.java"), javaWithTab("Keep"));

        List<CheckIssue> issues = tabIssues("*.java");

        assertEquals(0, issues.size(), "显式配置应压过 .gitignore 取反: " + issues);
    }

    @Test
    void emptyPatternsBehavesLikeNoConfig() throws IOException {
        write(tmp.resolve("src/Main.java"), javaWithTab("Main"));

        List<CheckIssue> issues = tabIssues("");

        assertEquals(1, issues.size(), "空配置不应忽略任何文件: " + issues);
    }
}
