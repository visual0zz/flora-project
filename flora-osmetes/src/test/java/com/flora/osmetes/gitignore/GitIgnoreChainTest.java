package com.flora.osmetes.gitignore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link GitIgnoreChain} 组合语义测试：深者覆盖浅者、被排除目录内部不可重新包含。
 */
class GitIgnoreChainTest {

    @TempDir
    Path tmp;

    private void write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private GitIgnoreChain chainOf(Path root, String rootGitIgnore) throws IOException {
        write(root.resolve(".gitignore"), rootGitIgnore);
        GitIgnoreChain chain = new GitIgnoreChain();
        chain.pushGitIgnore(root);
        return chain;
    }

    @Test
    void deeperFileOverridesShallower() throws IOException {
        write(tmp.resolve("src").resolve(".gitignore"), "!keep.java\n");
        GitIgnoreChain chain = chainOf(tmp, "*.java\n");

        // src 目录未被忽略，可进入
        assertFalse(chain.isIgnored(tmp.resolve("src"), true));
        chain.pushGitIgnore(tmp.resolve("src"));

        assertTrue(chain.isIgnored(tmp.resolve("src/Other.java"), false));
        assertFalse(chain.isIgnored(tmp.resolve("src/keep.java"), false)); // 被深层取反
    }

    @Test
    void excludedDirectoryPrunesEverythingInside() throws IOException {
        // 根规则排除 build/：目录被判定忽略后，遍历方（Osmetes.run）会整棵剪枝，
        // build/.gitignore 永远不会被读取，故其内部的取反规则不可能生效。
        // 剪枝由遍历方负责，chain 层只负责正确判定目录本身。
        write(tmp.resolve("build").resolve(".gitignore"), "!keep.java\n");
        GitIgnoreChain chain = chainOf(tmp, "build/\n");

        assertTrue(chain.isIgnored(tmp.resolve("build"), true));
        // 无关目录不受影响，仍可进入
        assertFalse(chain.isIgnored(tmp.resolve("src"), true));
    }

    @Test
    void dirContentsIgnoredButDirItselfWalkable() throws IOException {
        write(tmp.resolve("build").resolve(".gitignore"), "!keep.java\n");
        GitIgnoreChain chain = chainOf(tmp, "build/*\n");

        // build 自身不被排除
        assertFalse(chain.isIgnored(tmp.resolve("build"), true));
        chain.pushGitIgnore(tmp.resolve("build"));

        assertTrue(chain.isIgnored(tmp.resolve("build/Other.java"), false));
        assertFalse(chain.isIgnored(tmp.resolve("build/keep.java"), false));
    }

    @Test
    void popDoesNotRemoveParentRulesOnSkippedDir() throws IOException {
        GitIgnoreChain chain = chainOf(tmp, "build/\n");
        Path build = tmp.resolve("build");

        // 模拟"剪枝目录仍触发 postVisitDirectory"的防御场景：
        // build 从未压栈（直接剪枝），此时误触 pop 不应弹掉根规则
        chain.popGitIgnore(build);

        // 根规则 build/ 仍在生效
        assertTrue(chain.isIgnored(build, true));
        // 无关目录仍可进入
        assertFalse(chain.isIgnored(tmp.resolve("src"), true));
    }
}
