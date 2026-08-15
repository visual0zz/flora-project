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

    /**
     * 完全托管判定（见设计 06）：库根全部是 markdown 文件（含无块/用户正文），无其它扩展名/复杂子目录。
     */
    public boolean isFullyManaged() {
        if (!Files.isDirectory(root)) {
            return false;
        }
        try (var stream = Files.walk(root)) {
            for (java.nio.file.Path p : stream.filter(Files::isRegularFile).toList()) {
                if (p.startsWith(root.resolve(".git"))) {
                    continue;
                }
                if (p.startsWith(root.resolve(".conflict"))) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (!name.endsWith(".md")) {
                    return false;
                }
            }
            return true;
        } catch (java.io.IOException e) {
            return false;
        }
    }

    /** pull --rebase + push 到 origin。冲突按 updateTimestamp 自动解决（见设计 06）。 */
    public void sync() throws Exception {
        initIfNeeded();
        commit("sanctum sync");
        try (Repository repo = openRepo();
             Git git = new Git(repo)) {
            if (git.remoteList().call().stream().anyMatch(r -> "origin".equals(r.getName()))) {
                org.eclipse.jgit.api.PullResult pull = git.pull().setRemote("origin").setRebase(true).call();
                org.eclipse.jgit.api.RebaseResult result = pull.getRebaseResult();
                // 若 rebase 冲突，按 updateTimestamp 自动解决
                if (result != null && result.getStatus() == org.eclipse.jgit.api.RebaseResult.Status.CONFLICTS) {
                    resolveConflicts(git, repo);
                    git.rebase().setOperation(RebaseCommand.Operation.CONTINUE).call();
                }
            }
            git.push().setRemote("origin").call();
        }
    }

    /** 冲突自动解决：读 ours/theirs，按 updateTimestamp 大者 wins；对方版本记 .conflict。 */
    private void resolveConflicts(Git git, Repository repo) throws Exception {
        for (String path : git.status().call().getConflicting()) {
            byte[] ours = readBlob(repo, path, "ours");
            byte[] theirs = readBlob(repo, path, "theirs");
            long oursTs = tsOf(ours);
            long theirsTs = tsOf(theirs);
            byte[] winner = oursTs >= theirsTs ? ours : theirs;
            byte[] loser = oursTs >= theirsTs ? theirs : ours;
            java.nio.file.Path target = root.resolve(path);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, winner);
            git.add().addFilepattern(path).call();
            if (loser != null) {
                java.nio.file.Path conflictFile = root.resolve(".conflict/" + path.replace('/', '_'));
                if (conflictFile.getParent() != null) {
                    Files.createDirectories(conflictFile.getParent());
                }
                Files.write(conflictFile, loser);
            }
        }
    }

    private long tsOf(byte[] block) {
        if (block == null) {
            return 0;
        }
        try {
            byte[] deobf = com.flora.sanctum.store.BlockHeader.deobfuscate(block);
            if (!com.flora.sanctum.store.BlockHeader.isBlock(deobf)) {
                return 0;
            }
            // 明文块负载从偏移 22；此处只解析 updateTimestamp（若为加密块无法解，返回 0）
            if (deobf.length > 5 && (deobf[5] & 0x02) != 0) {
                byte[] payload = new byte[deobf.length - 22];
                System.arraycopy(deobf, 22, payload, 0, payload.length);
                com.flora.root.codec.json.model.JsonObject n = com.flora.root.codec.JsonUtil.parseObject(
                        new String(payload, java.nio.charset.StandardCharsets.UTF_8));
                Long ts = n.getLong("updateTimestamp");
                return ts == null ? 0 : ts;
            }
        } catch (Exception ignore) {
        }
        return 0;
    }

    private byte[] readBlob(Repository repo, String path, String stage) throws Exception {
        int want = "theirs".equals(stage) ? 3 : 2; // stage: 1=base, 2=ours, 3=theirs
        var index = repo.readDirCache();
        for (int i = 0; i < index.getEntryCount(); i++) {
            var entry = index.getEntry(i);
            if (entry.getStage() == want && entry.getPathString().equals(path)) {
                try (var reader = repo.newObjectReader()) {
                    var obj = reader.open(entry.getObjectId());
                    return obj.getBytes();
                }
            }
        }
        return null;
    }

    private Repository openRepo() throws Exception {
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        builder.setWorkTree(root.toFile());
        builder.setMustExist(true);
        return builder.build();
    }
}
