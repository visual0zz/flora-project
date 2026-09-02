package com.flora.sanctum.app.sync;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 同步封装（见设计 06"Git 同步"），基于本地 git 命令（ProcessBuilder），无 jgit 依赖。
 * <p>
 * 同步是可选能力，仅当库目录命中"完全托管"时使用。流程：
 * init（缺）→ commit 全部改动 → 对 origin fetch+pull --rebase+冲突自动解决 → push。
 * <p>
 * 冲突仲裁（见设计 06）：冲突发生在同一文件被两端同时修改时。按块时间戳（落盘
 * {@code timestamp:base64} 冒号前数字）大者 wins；被覆盖方复制到 {@code .conflict} 供核查。
 * <p>
 * SSH 私钥经 {@link #setSshCommand} 指定（{@code GIT_SSH_COMMAND} 注入，临时进程级，不写全局配置）。
 */
public final class SyncService {

    private final Path root;
    private String sshCommand;

    public SyncService(Path root) {
        this.root = root;
    }

    /** 设置本次同步使用的 SSH 命令（如 {@code "ssh -i /path/key -o IdentitiesOnly=yes"}）。 */
    public void setSshCommand(String sshCommand) {
        this.sshCommand = sshCommand;
    }

    /** 是否已是 git 仓库。 */
    public boolean isGitRepo() {
        return Files.isDirectory(root.resolve(".git"));
    }

    /** 初始化 git 仓库（若缺）。 */
    public void initIfNeeded() throws Exception {
        if (!isGitRepo()) {
            execToString("init");
        }
    }

    /**
     * 完全托管判定（见设计 06）：库根全部是 markdown 文件（含无块/用户正文），无其它扩展名/复杂子目录。
     * 独立仓库的 {@code lib/} 目录不算（应用分发内容，非数据）。
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
                if (p.startsWith(root.resolve("lib"))) {
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

    /** 提交全部改动。作者信息可配置，默认 sanctum <local>。无改动则 no-op。 */
    public void commit(String message) throws Exception {
        execToString("add", "-A");
        String status = execToString("status", "--porcelain");
        if (status.isBlank()) {
            return;
        }
        execToString("commit", "-m", message,
                "--author=Sanctum <local@sanctum>",
                "--no-verify");
    }

    /** pull --rebase + push 到 origin。冲突按时间戳自动解决（见设计 06）。 */
    public void sync() throws Exception {
        initIfNeeded();
        commit("sanctum sync");
        execToString("fetch", "origin");
        String rebase = execToString("rebase", "origin");
        if (rebase.contains("CONFLICT")) {
            resolveConflicts();
            execToString("rebase", "--continue");
        }
        execToString("push", "origin", "HEAD");
    }

    /** 克隆远程仓库到本地目录（用于从远端恢复库）。 */
    public void clone(String uri, Path target) throws Exception {
        execInDir(target.getParent(), "clone", uri, target.getFileName().toString());
    }

    /** 冲突自动解决：对每个冲突文件读 ours/theirs，按时间戳大者 wins；被覆盖方记 .conflict。 */
    private void resolveConflicts() throws Exception {
        String status = execToString("status", "--porcelain");
        for (String line : status.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            // 未合并冲突文件：首位两个状态码，其后为路径（含 rename 的用 -> 分隔取右侧）
            if (line.startsWith("UU")) {
                String path = line.substring(3).trim();
                int arrow = path.indexOf("->");
                if (arrow >= 0) {
                    path = path.substring(arrow + 2).trim();
                }
                resolveFile(path);
            }
        }
    }

    private void resolveFile(String path) throws Exception {
        byte[] ours = execToBytes("show", ":2:" + path);
        byte[] theirs = execToBytes("show", ":3:" + path);
        long oursTs = tsOf(ours);
        long theirsTs = tsOf(theirs);
        boolean oursWins = oursTs >= theirsTs;
        byte[] winner = oursWins ? ours : theirs;
        byte[] loser = oursWins ? theirs : ours;
        Path target = root.resolve(path);
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        Files.write(target, winner);
        if (loser != null) {
            Path conflictFile = root.resolve(".conflict/" + path.replace('/', '_'));
            if (conflictFile.getParent() != null) {
                Files.createDirectories(conflictFile.getParent());
            }
            Files.write(conflictFile, loser);
        }
        execToString("add", "--", path);
    }

    /** 从块内容解析时间戳：落盘格式 {@code timestamp:base64}，时间戳为冒号前数字（见设计 04b）。 */
    private static long tsOf(byte[] block) {
        if (block == null) {
            return 0;
        }
        try {
            String text = new String(block, StandardCharsets.UTF_8).trim();
            int colon = text.indexOf(':');
            if (colon <= 0) {
                return 0;
            }
            return Long.parseLong(text.substring(0, colon));
        } catch (NumberFormatException | IndexOutOfBoundsException e) {
            return 0;
        }
    }

    private String execToString(String... args) throws Exception {
        return new String(execToBytes(args), StandardCharsets.UTF_8);
    }

    private byte[] execToBytes(String... args) throws Exception {
        return execInDir(root, args);
    }

    private byte[] execInDir(Path cwd, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        if (sshCommand != null && !sshCommand.isBlank()) {
            pb.environment().put("GIT_SSH_COMMAND", sshCommand);
        }
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        int exit = p.waitFor();
        if (exit != 0) {
            throw new java.io.IOException(
                    "git " + String.join(" ", args) + " failed (exit " + exit + "): "
                            + new String(out, StandardCharsets.UTF_8));
        }
        return out;
    }
}
