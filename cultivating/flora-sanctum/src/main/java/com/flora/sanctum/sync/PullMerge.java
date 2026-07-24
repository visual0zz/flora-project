package com.flora.sanctum.sync;

import java.nio.file.Path;

/**
 * Git 拉取与冲突处理。
 */
public class PullMerge {

    /** 冲突文件的本地备份后缀。 */
    public static final String CONFLICT_LOCAL_SUFFIX = ".enc.local";
    /** 冲突文件的远程备份后缀。 */
    public static final String CONFLICT_REMOTE_SUFFIX = ".enc.remote";

    private PullMerge() {
    }

    /**
     * 拉取远程变更（fetch + merge）。
     * <p>
     * 如果发生冲突，冲突条目会以 {@code <uuid>.enc.local} 和
     * {@code <uuid>.enc.remote} 保留双份，供用户在 GUI 中手动解决。
     *
     * @param repoDir 仓库目录
     * @return 有冲突时返回 true
     * @throws GitException 如果拉取失败（网络错误等）
     */
    public static boolean pull(Path repoDir) {
        // 先拉取
        int exit = GitBackend.git(repoDir, "pull", "--no-rebase");
        if (exit == 0) {
            return false; // 无冲突
        }

        // 有冲突——检测并备份冲突文件
        String conflicted = GitBackend.gitOutput(repoDir, "diff", "--name-only", "--diff-filter=U");
        if (conflicted.isEmpty()) {
            throw new GitException("git pull failed (non-conflict error)");
        }

        // 备份冲突文件的本地和远程版本
        String[] files = conflicted.split("\n");
        for (String file : files) {
            String trimmed = file.trim();
            if (trimmed.isEmpty()) continue;

            Path base = repoDir.resolve(trimmed);
            Path localBackup = repoDir.resolve(trimmed + CONFLICT_LOCAL_SUFFIX);
            Path remoteBackup = repoDir.resolve(trimmed + CONFLICT_REMOTE_SUFFIX);

            // 保留当前工作区版本（本地版本）
            GitBackend.git(repoDir, "show", ":2:" + trimmed, ">", localBackup.toString());
            // 保留远程版本
            GitBackend.git(repoDir, "show", ":3:" + trimmed, ">", remoteBackup.toString());
            // 保持本地版本（last-write-wins）
            GitBackend.git(repoDir, "checkout", "--ours", trimmed);
            GitBackend.git(repoDir, "add", trimmed);
        }

        // 完成合并
        GitBackend.git(repoDir, "commit", "-m", "auto-merge conflicts (kept local versions)");
        return true;
    }

    /**
     * 拉取远程变更（默认 origin/master）。
     */
    public static boolean pull(Path repoDir, String remote, String branch) {
        int exit = GitBackend.git(repoDir, "pull", remote, branch);
        return exit != 0;
    }
}
