package com.flora.sanctum.sync;

import java.nio.file.Path;

/**
 * Git 提交与推送操作。
 */
public class CommitPush {

    private CommitPush() {
    }

    /**
     * 暂存所有变更并提交。
     *
     * @param repoDir 仓库目录
     * @param message 提交信息
     * @throws GitException 如果操作失败
     */
    public static void addAndCommit(Path repoDir, String message) {
        // 暂存所有变更（包括新增、修改、删除）
        int exitAdd = GitBackend.git(repoDir, "add", "--all");
        if (exitAdd != 0) {
            throw new GitException("git add failed in " + repoDir);
        }

        // 检查是否有变更需要提交
        String status = GitBackend.gitOutput(repoDir, "status", "--porcelain");
        if (status.isEmpty()) {
            return; // 无变更，跳过提交
        }

        int exitCommit = GitBackend.git(repoDir, "commit", "-m", message);
        if (exitCommit != 0) {
            throw new GitException("git commit failed in " + repoDir);
        }
    }

    /**
     * 推送到远程仓库。
     *
     * @param repoDir   仓库目录
     * @param remote    远程名称（如 "origin"）
     * @param branch    分支名称（如 "master"）
     * @throws GitException 如果推送失败
     */
    public static void push(Path repoDir, String remote, String branch) {
        int exit = GitBackend.git(repoDir, "push", remote, branch);
        if (exit != 0) {
            throw new GitException("git push failed: " + remote + "/" + branch);
        }
    }

    /**
     * 推送到远程仓库（默认 origin/master）。
     */
    public static void push(Path repoDir) {
        push(repoDir, "origin", "master");
    }

    /**
     * 获取当前分支名称。
     */
    public static String currentBranch(Path repoDir) {
        return GitBackend.gitOutput(repoDir, "rev-parse", "--abbrev-ref", "HEAD");
    }
}
