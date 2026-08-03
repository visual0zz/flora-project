package com.flora.osmetes.gitignore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.flora.osmetes.gitignore.GitIgnore.Match.IGNORED;
import static com.flora.osmetes.gitignore.GitIgnore.Match.INCLUDED;
import static com.flora.osmetes.gitignore.GitIgnore.Match.NO_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GitIgnore} 单文件规则匹配的单元测试，覆盖 gitignore(5) 主要语义。
 */
class GitIgnoreTest {

    @TempDir
    Path tmp;

    private GitIgnore parse(String... lines) {
        return GitIgnore.parse(tmp, List.of(lines));
    }

    private Path p(String rel) {
        return tmp.resolve(rel);
    }

    private static void assertIgnored(GitIgnore gi, Path path, boolean isDir) {
        assertEquals(IGNORED, gi.matches(path, isDir), () -> "应被忽略: " + path);
    }

    private static void assertIncluded(GitIgnore gi, Path path, boolean isDir) {
        assertEquals(INCLUDED, gi.matches(path, isDir), () -> "应被取反重新包含: " + path);
    }

    private static void assertNoMatch(GitIgnore gi, Path path, boolean isDir) {
        assertEquals(NO_MATCH, gi.matches(path, isDir), () -> "不应命中: " + path);
    }

    // ==================== 基础模式 ====================

    @Test
    void basenamePatternMatchesAtAnyDepth() {
        GitIgnore gi = parse("*.log");
        assertIgnored(gi, p("a.log"), false);
        assertIgnored(gi, p("sub/deep/b.log"), false);
        assertNoMatch(gi, p("a.log.txt"), false);
        assertNoMatch(gi, p("log"), false);
    }

    @Test
    void anchoredPatternIsRelativeToBase() {
        GitIgnore gi = parse("/root.txt");
        assertIgnored(gi, p("root.txt"), false);
        assertNoMatch(gi, p("sub/root.txt"), false);
    }

    @Test
    void middleSlashAnchorsPattern() {
        GitIgnore gi = parse("a/b.txt");
        assertIgnored(gi, p("a/b.txt"), false);
        assertNoMatch(gi, p("x/a/b.txt"), false);
    }

    @Test
    void questionMarkMatchesSingleChar() {
        GitIgnore gi = parse("a?.txt");
        assertIgnored(gi, p("ab.txt"), false);
        assertNoMatch(gi, p("abc.txt"), false);
    }

    @Test
    void charClassAndNegatedClass() {
        GitIgnore gi = parse("file[0-9].txt", "[!a].md");
        assertIgnored(gi, p("file5.txt"), false);
        assertNoMatch(gi, p("fileA.txt"), false);
        assertIgnored(gi, p("b.md"), false);
        assertNoMatch(gi, p("a.md"), false);
    }

    // ==================== 目录模式 ====================

    @Test
    void dirOnlyPatternMatchesOnlyDirectories() {
        GitIgnore gi = parse("build/");
        assertIgnored(gi, p("build"), true);
        assertIgnored(gi, p("x/build"), true);
        // 名为 build 的普通文件不应被"仅目录"模式命中
        assertNoMatch(gi, p("build"), false);
    }

    // ==================== 双星号 ====================

    @Test
    void leadingDoubleStarMatchesAnyDepth() {
        GitIgnore gi = parse("**/foo.txt");
        assertIgnored(gi, p("foo.txt"), false);
        assertIgnored(gi, p("a/foo.txt"), false);
        assertIgnored(gi, p("a/b/foo.txt"), false);
        assertNoMatch(gi, p("a/b/bar.txt"), false);
    }

    @Test
    void trailingDoubleStarMatchesEverythingInside() {
        GitIgnore gi = parse("docs/**");
        assertIgnored(gi, p("docs/a.txt"), false);
        assertIgnored(gi, p("docs/a/b/c.txt"), false);
        assertNoMatch(gi, p("docs"), false); // 不匹配目录自身
        assertNoMatch(gi, p("other/docs/a.txt"), false);
    }

    @Test
    void middleDoubleStarMatchesZeroOrMoreDirs() {
        GitIgnore gi = parse("a/**/b.txt");
        assertIgnored(gi, p("a/b.txt"), false);
        assertIgnored(gi, p("a/x/b.txt"), false);
        assertIgnored(gi, p("a/x/y/b.txt"), false);
        assertNoMatch(gi, p("a/xb.txt"), false);
    }

    // ==================== 取反与最后一条匹配生效 ====================

    @Test
    void negationReincludesWithinSameFile() {
        GitIgnore gi = parse("*.log", "!keep.log");
        assertIgnored(gi, p("other.log"), false);
        assertIncluded(gi, p("keep.log"), false);
    }

    @Test
    void lastMatchingRuleWins() {
        GitIgnore gi = parse("foo", "!foo", "foo");
        assertIgnored(gi, p("foo"), false);
    }

    // ==================== 注释、空行与转义 ====================

    @Test
    void commentAndBlankLinesAreIgnored() {
        GitIgnore gi = parse("# 注释", "", "   ", "foo.log");
        assertIgnored(gi, p("foo.log"), false);
        assertNoMatch(gi, p("注释"), false);
    }

    @Test
    void escapedHashIsLiteral() {
        GitIgnore gi = parse("\\#literal");
        assertIgnored(gi, p("#literal"), false);
        assertNoMatch(gi, p("literal"), false);
    }

    @Test
    void escapedBangIsLiteralNotNegation() {
        GitIgnore gi = parse("\\!important");
        assertIgnored(gi, p("!important"), false);
    }

    @Test
    void trailingSpaceIsStrippedUnlessEscaped() {
        GitIgnore gi = parse("keep ");
        assertIgnored(gi, p("keep"), false);
    }

    @Test
    void escapedTrailingSpaceKeepsLiteralSpace() {
        GitIgnore gi = parse("keep\\ ");
        if (isWindows()) {
            // Windows 不允许文件名以空格结尾，无法构造该路径，仅验证未误匹配普通名
            assertNoMatch(gi, p("keep"), false);
            return;
        }
        assertIgnored(gi, p("keep "), false);
        assertNoMatch(gi, p("keep"), false);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    // ==================== 边界 ====================

    @Test
    void pathOutsideBaseNeverMatches() {
        GitIgnore gi = parse("foo");
        assertNoMatch(gi, tmp.getParent().resolve("foo"), false);
        // base 目录本身不参与判定
        assertNoMatch(gi, tmp, true);
    }

    @Test
    void bareBangAndSlashAloneProduceNoRules() {
        GitIgnore gi = parse("!", "/");
        assertNoMatch(gi, p("anything"), false);
    }
}
