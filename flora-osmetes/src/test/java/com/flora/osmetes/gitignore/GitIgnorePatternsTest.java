package com.flora.osmetes.gitignore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static com.flora.osmetes.gitignore.GitIgnore.Match.IGNORED;
import static com.flora.osmetes.gitignore.GitIgnore.Match.INCLUDED;
import static com.flora.osmetes.gitignore.GitIgnore.Match.NO_MATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link GitIgnore#parsePatterns(String)} 分隔符解析与取并集语义的测试。
 */
class GitIgnorePatternsTest {

    @TempDir
    Path tmp;

    private Path p(String rel) {
        return tmp.resolve(rel);
    }

    private static void assertIgnored(GitIgnore gi, Path path, boolean isDir) {
        assertEquals(IGNORED, gi.matches(path, isDir), () -> "应被忽略: " + path);
    }

    @Test
    void semicolonSeparatesPatterns() {
        GitIgnore gi = GitIgnore.parsePatterns(tmp, "absent/;*.log");
        assertIgnored(gi, p("absent"), true);
        // 嵌套的 absent 目录本身命中（遍历方据此剪枝其内部文件）
        assertIgnored(gi, p("deep/absent"), true);
        assertIgnored(gi, p("a.log"), false);
        assertIgnored(gi, p("sub/deep/b.log"), false);
        assertEquals(NO_MATCH, gi.matches(p("src/Main.java"), false));
    }

    @Test
    void anySeparatorHasSameUnionSemantics() {
        for (String sep : new String[]{",", ";", "|", "&"}) {
            GitIgnore gi = GitIgnore.parsePatterns(tmp, "foo" + sep + "*.txt");
            assertIgnored(gi, p("foo"), true);
            assertIgnored(gi, p("x.txt"), false);
        }
    }

    @Test
    void negationStillSupported() {
        GitIgnore gi = GitIgnore.parsePatterns(tmp, "*.log;!keep.log");
        assertIgnored(gi, p("other.log"), false);
        assertEquals(INCLUDED, gi.matches(p("keep.log"), false));
    }

    @Test
    void emptyAndBlankSegmentsAreFiltered() {
        GitIgnore gi = GitIgnore.parsePatterns(tmp, "  ;;absent/  ;");
        assertIgnored(gi, p("absent"), true);
        assertEquals(NO_MATCH, gi.matches(p("x.java"), false));
    }
}
