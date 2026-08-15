package com.flora.sanctum.sync;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RebaseCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Git 同步封装（见设计 06"Git 同步"）。
 * <p>
 * 同步是可选能力，仅当库目录命中"完全托管"时使用。流程：
 * 更新 manifest → commit 全部改动 → 对每个远端 fetch+pull --rebase+冲突自动解决 → push。
 * 本阶段实现基础：init（缺）、commit、pull --rebase、push（默认 origin）。
 */
public final class SyncService {

    private final Path root;

    public SyncService(Path root) {
        this.root = root;
    }

    /** 是否已是 git 仓库。 */
    public boolean isGitRepo() {
        return Files.isDirectory(root.resolve(".git"));
    }

    /** 初始化 git 仓库（若缺）。 */
    public void initIfNeeded() throws Exception {
        if (!isGitRepo()) {
            Git.init().setDirectory(root.toFile()).call().close();
        }
    }

    /** 提交全部改动。作者信息可配置，默认 sanctum <local>。 */
    public void commit(String message) throws Exception {
        try (Repository repo = openRepo();
             Git git = new Git(repo)) {
            git.add().addFilepattern(".").call();
            Status status = git.status().call();
            if (status.isClean()) {
                return;
            }
            git.commit().setMessage(message)
                    .setAuthor("sanctum", "local@sanctum")
                    .call();
        }
    }

    /** pull --rebase + push 到 origin。 */
    public void sync() throws Exception {
        initIfNeeded();
        commit("sanctum sync");
        try (Repository repo = openRepo();
             Git git = new Git(repo)) {
            // 若有 origin，先 pull --rebase
            if (git.remoteList().call().stream().anyMatch(r -> "origin".equals(r.getName()))) {
                git.pull().setRemote("origin").setRebase(true).call();
            }
            git.push().setRemote("origin").call();
        }
    }

    private Repository openRepo() throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setWorkTree(root.toFile());
        builder.setMustExist(true);
        return builder.build();
    }
}
