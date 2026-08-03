package com.flora.osmetes.gitignore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 从扫描根到当前目录的 {@code .gitignore} 规则链。
 * <p>
 * 目录遍历时沿路径下潜，每进入一个含 {@code .gitignore} 的目录就压栈一份规则；
 * 离开时弹栈。判定某路径时按"深者优先、先命中即定"的顺序求值，等价于 git 的
 * "浅层规则被深层规则覆盖、整条链上最后一条匹配生效"。
 * <p>
 * 若某目录自身被判定为忽略，遍历方应直接剪枝（不进入其内部），因此位于被忽略
 * 目录内部的 {@code .gitignore} 永远不会被读取——这与 git"被排除目录内的内容
 * 无法被重新包含"的语义一致。
 * <p>
 * 可选地传入一份"覆盖规则"（通常来自 Maven 等外部显式配置）：它始终优先于
 * 链上所有 {@code .gitignore}，命中即定，用于让用户显式配置的忽略范围压过仓库
 * 自身的忽略规则。
 */
public final class GitIgnoreChain {

    private final GitIgnore override;
    private final Deque<GitIgnore> stack = new ArrayDeque<>();

    public GitIgnoreChain() {
        this(null);
    }

    /**
     * @param override 最高优先级的覆盖规则，可为 {@code null}
     */
    public GitIgnoreChain(GitIgnore override) {
        this.override = override;
    }

    /**
     * 进入目录时调用：若其中存在 {@code .gitignore} 则压栈（对扫描根同样适用）。
     */
    public void pushGitIgnore(Path dir) throws IOException {
        Path gitIgnore = dir.resolve(".gitignore");
        if (Files.isRegularFile(gitIgnore)) {
            stack.push(GitIgnore.load(gitIgnore));
        }
    }

    /**
     * 离开目录时调用。
     * <p>
     * 防御性校验栈顶归属：若该目录因被忽略而剪枝、从未压栈，则不弹栈，避免误删
     * 父目录的规则。
     */
    public void popGitIgnore(Path dir) {
        if (!stack.isEmpty()) {
            Path topBase = stack.peek().base();
            if (topBase.equals(dir.toAbsolutePath().normalize())) {
                stack.pop();
            }
        }
    }

    /**
     * 沿规则链判定路径是否被忽略。
     * <p>
     * 覆盖规则（若存在）优先于链上所有 {@code .gitignore}：覆盖规则命中即定，
     * 不命中才继续沿链求值。
     *
     * @param path        待判定的路径
     * @param isDirectory 目标是否为目录
     * @return {@code true} 表示忽略（不含"被取反重新包含"的情形）
     */
    public boolean isIgnored(Path path, boolean isDirectory) {
        if (override != null) {
            GitIgnore.Match match = override.matches(path, isDirectory);
            if (match != GitIgnore.Match.NO_MATCH) {
                return match == GitIgnore.Match.IGNORED;
            }
        }
        // ArrayDeque 迭代从头到尾，即最新压栈（最深）的规则先求值
        for (GitIgnore gitIgnore : stack) {
            GitIgnore.Match match = gitIgnore.matches(path, isDirectory);
            if (match != GitIgnore.Match.NO_MATCH) {
                return match == GitIgnore.Match.IGNORED;
            }
        }
        return false;
    }
}
