package com.flora.osmetes;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@code @SuppressWarnings("osmetes:...")} 注解在 {@link Osmetes} 扫描中的集成测试。
 */
class OsmetesSuppressWarningsTest {

    @TempDir
    Path tmp;

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private List<CheckIssue> tabIssues() throws IOException {
        return Osmetes.run(tmp, Osmetes.builtinChecks()).stream()
                .filter(i -> i.check().equals("tab"))
                .toList();
    }

    @Test
    void classLevelAnnotationSuppressesAllTabsInClass() throws IOException {
        write(tmp.resolve("Annotated.java"),
                "@SuppressWarnings(\"osmetes:tab\")\n"
                        + "class A {\n"
                        + "\tvoid m() {}\n"
                        + "\tvoid n() {\n"
                        + "\t\tint x = 1;\n"
                        + "\t}\n"
                        + "}\n");
        write(tmp.resolve("Plain.java"),
                "class B {\n"
                        + "\tvoid m() {}\n"
                        + "}\n");

        List<CheckIssue> issues = tabIssues();

        assertEquals(1, issues.size(), "仅 Plain.java 应被报告: " + issues);
        assertEquals("Plain.java", issues.getFirst().relativeFile());
        assertEquals(2, issues.getFirst().line());
    }

    @Test
    void methodLevelAnnotationSuppressesOnlyThatMethod() throws IOException {
        write(tmp.resolve("C.java"),
                "class C {\n"
                        + "\tvoid a() {}\n"
                        + "\t@SuppressWarnings(\"osmetes:tab\")\n"
                        + "\tvoid b() {\n"
                        + "\t\tint y = 1;\n"
                        + "\t}\n"
                        + "}\n");

        List<CheckIssue> issues = tabIssues();

        assertEquals(1, issues.size(), "仅未注解的 a 方法应被报告: " + issues);
        assertEquals(2, issues.getFirst().line());
    }

    @Test
    void statementLevelAnnotationSuppressesThatStatement() throws IOException {
        write(tmp.resolve("D.java"),
                "class D {\n"
                        + "\tvoid m() {\n"
                        + "\t\t@SuppressWarnings(\"osmetes:tab\")\n"
                        + "\t\tint x = 1;\n"
                        + "\t\tint y = 2;\n"
                        + "\t}\n"
                        + "}\n");

        List<CheckIssue> issues = tabIssues();

        // 语句级注解只覆盖第 3-4 行（注解行与被注解声明）；
        // 方法声明(2)、后续语句(5)、闭合括号(6)的 tab 仍应被报告
        assertFalse(issues.stream().anyMatch(i -> i.line() == 4),
                "被注解的语句不应被报告: " + issues);
        assertEquals(3, issues.size(), "未被抑制的行应被报告: " + issues);
        assertEquals(List.of(2, 5, 6),
                issues.stream().map(CheckIssue::line).sorted().toList());
    }

    @Test
    void suppressionOnlyAffectsNamedCheck() throws IOException {
        // 注解只抑制 tab，trailing-whitespace 仍应报告
        write(tmp.resolve("E.java"),
                "@SuppressWarnings(\"osmetes:tab\")\n"
                        + "class E {\n"
                        + "\tvoid m() {} \n"
                        + "}\n");

        List<CheckIssue> issues = Osmetes.run(tmp, Osmetes.builtinChecks());

        assertEquals(1, issues.size(), "tab 被抑制，trailing-whitespace 仍应报告: " + issues);
        assertEquals("trailing-whitespace", issues.getFirst().check());
        assertEquals(3, issues.getFirst().line());
    }
}
