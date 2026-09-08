package com.flora.sanctum.app.sync;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

/**
 * Git 同步封装（见设计 06"Git 同步"），基于本地 git 命令（ProcessBuilder），无 jgit 依赖。
 * <p>
 * 本实现<b>不依赖任何既有 git 仓库配置、也不要求库目录已是 git 仓库</b>：完全依赖本地安装的
 * {@code git} 命令（缺失则报错）与库自身存储的「远程 {@code RemoteNode} + SSH 密钥
 * {@code SshKeyNode}」。每个远程以其名称作为 git remote 名，各自 fetch / merge / push。
 * <p>
 * 主分支策略：优先 {@code main}；存在 {@code master} 且无 {@code main} 则改名；二者皆无则新建
 * {@code main}（兼容无提交的空仓）。
 * <p>
 * 冲突仲裁（见设计 06）：merge 冲突发生在同一文件被两端同时修改时。按块时间戳（落盘
 * {@code timestamp:base64} 冒号前数字）大者 wins；被覆盖方复制到 {@code .conflict} 供核查。
 * <p>
 * SSH 私钥按远程写入临时文件（权限 0600），经 {@code GIT_SSH_COMMAND} 注入该远端的
 * fetch / push 进程，同步结束统一删除临时文件。
 */
public final class SyncService {

    private static final Logger LOG = LoggerFactory.getLogger(SyncService.class);

    private final Path root;

    public SyncService(Path root) {
        this.root = root;
    }

    /** 一个远程配置：名称（同时用作 git remote 名）、地址、可选 SSH 私钥 PEM。 */
    public record RemoteSpec(String name, String url, String sshKeyPem) {}

    /** 同步步骤监听器：由 UI 进度窗实现，方法体须自行切回 EDT。 */
    public interface SyncStepListener {
        /** 步骤清单建立时登记标题（尚未开始）。 */
        void beginStep(int index, String title);

        /** 该步骤进入执行中。 */
        void markRunning(int index);

        /** 该步骤成功结束（detail 为摘要）。 */
        void markDone(int index, String detail);

        /** 该步骤失败（detail 为错误摘要）。 */
        void markError(int index, String detail);

        /** 追加一行 git 原始输出到日志区。 */
        void log(String message);

        /** 整个同步结束：ok 表示成功，msg 为最终提示。 */
        void done(boolean ok, String message);
    }

    /** 本地是否安装可用的 git 命令。 */
    public boolean isGitAvailable() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 是否已是 git 仓库。 */
    public boolean isGitRepo() {
        return Files.isDirectory(root.resolve(".git"));
    }

    /** 初始化 git 仓库（若缺）。 */
    public void initIfNeeded() throws Exception {
        if (!isGitRepo()) {
            LOG.info("Initializing git repository at {}", root);
            execToString("init");
        }
    }

    /** 提交全部改动。作者信息固定为 Sanctum <local@sanctum>。无改动则 no-op。 */
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

    /** 克隆远程仓库到本地目录（用于从远端恢复库）。 */
    public void clone(String uri, Path target) throws Exception {
        execInDir(target.getParent(), null, "clone", uri, target.getFileName().toString());
    }

    /**
     * 主同步流程：按步骤检查 git → 配置每个远程 → 确保本地仓库/主分支 → 提交 → 逐个 fetch/merge/push。
     * 每一步通过 {@code listener} 汇报；异常时通过 listener 展示并抛出以中止。
     *
     * @throws IllegalStateException 当远程列表为空
     * @throws Exception             任意 git 步骤失败（含无 git）
     */
    public void sync(List<RemoteSpec> remotes, SyncStepListener listener) throws Exception {
        if (remotes == null || remotes.isEmpty()) {
            listener.done(false, "未配置任何远程，无法同步");
            throw new IllegalStateException("no remote configured");
        }

        // 步骤清单：检查 git → 初始化 → 配置每个远端 → 主分支 → 提交 → fetch/merge/push 每个远端
        List<String> titles = new ArrayList<>();
        titles.add("检查 git 可用性");
        titles.add("初始化本地仓库");
        for (RemoteSpec r : remotes) {
            titles.add("配置远端 " + r.name());
        }
        titles.add("确保主分支 main");
        titles.add("提交本地改动");
        for (RemoteSpec r : remotes) {
            titles.add("拉取 " + r.name());
        }
        for (RemoteSpec r : remotes) {
            titles.add("合并 " + r.name());
        }
        for (RemoteSpec r : remotes) {
            titles.add("推送 " + r.name());
        }
        for (int i = 0; i < titles.size(); i++) {
            listener.beginStep(i, titles.get(i));
        }

        Map<String, Path> sshFiles = new HashMap<>();
        List<Path> tempFiles = new ArrayList<>();
        int idx = 0;
        try {
            // 预备 SSH 临时文件（权限 0600），按远程名索引
            for (RemoteSpec r : remotes) {
                if (r.sshKeyPem() != null && !r.sshKeyPem().isBlank()) {
                    Path tmp = Files.createTempFile("sanctum-ssh-", ".key");
                    Files.writeString(tmp, r.sshKeyPem());
                    trySetOwnerOnly(tmp);
                    sshFiles.put(r.name(), tmp);
                    tempFiles.add(tmp);
                }
            }

            // 1) git 可用性
            listener.markRunning(idx);
            if (!isGitAvailable()) {
                listener.markError(idx, "未检测到 git 命令");
                listener.done(false, "未安装 git，无法同步");
                throw new IllegalStateException("git command not found");
            }
            listener.markDone(idx, gitVersion());
            idx++;

            // 2) 初始化仓库（必须在配置远端之前，否则 git 命令无处执行）
            listener.markRunning(idx);
            try {
                initIfNeeded();
                listener.markDone(idx, "仓库就绪");
            } catch (Exception e) {
                listener.markError(idx, e.getMessage());
                listener.done(false, "初始化仓库失败：" + e.getMessage());
                throw e;
            }
            idx++;

            // 3) 配置每个远程（已存在则校正地址）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    if (remoteExists(r.name())) {
                        execToString("remote", "set-url", r.name(), r.url());
                    } else {
                        execToString("remote", "add", r.name(), r.url());
                    }
                    listener.markDone(idx, r.url());
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "配置远端 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            // 4) 确保主分支
            String branch;
            listener.markRunning(idx);
            try {
                branch = ensureMainBranch();
                listener.markDone(idx, "分支 " + branch);
            } catch (Exception e) {
                listener.markError(idx, e.getMessage());
                listener.done(false, "准备主分支失败：" + e.getMessage());
                throw e;
            }
            idx++;

            // 5) 提交
            listener.markRunning(idx);
            try {
                commit("sanctum sync");
                listener.markDone(idx, "已提交本地改动");
            } catch (Exception e) {
                listener.markError(idx, e.getMessage());
                listener.done(false, "提交失败：" + e.getMessage());
                throw e;
            }
            idx++;

            // 6) 逐个 fetch（各自 SSH 环境）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    gitWithLog(listener, "fetch " + r.name(), root, sshEnvFor(r, sshFiles),
                            "fetch", r.name());
                    listener.markDone(idx, "已拉取 " + r.name());
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "拉取 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            // 7) 逐个 merge（冲突按块内时间戳仲裁；远端尚无该分支时跳过合并）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    String remoteBranch = r.name() + "/" + branch;
                    if (remoteBranchExists(remoteBranch)) {
                        int code = runGit(root, sshEnvFor(r, sshFiles), new StringBuilder(),
                                "merge", remoteBranch, "--no-edit",
                                "--allow-unrelated-histories");
                        if (code != 0) {
                            String status = execToString("status", "--porcelain");
                            if (status.contains("UU")) {
                                resolveConflicts();
                                execToString("add", "-A");
                                execToString("commit",
                                        "--author=Sanctum <local@sanctum>",
                                        "--no-verify", "-m", "sanctum merge auto-resolve");
                                listener.markDone(idx, "合并并自动解决冲突 " + r.name());
                            } else {
                                throw new IOException("git merge 返回 " + code);
                            }
                        } else {
                            listener.markDone(idx, "已合并 " + r.name());
                        }
                    } else {
                        listener.markDone(idx, "远端尚无 " + branch + " 分支，跳过合并");
                    }
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "合并 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            // 8) 逐个 push（各自 SSH 环境）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    gitWithLog(listener, "push " + r.name(), root, sshEnvFor(r, sshFiles),
                            "push", r.name(), branch);
                    listener.markDone(idx, "已推送 " + r.name());
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "推送 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            listener.done(true, "同步完成");
        } finally {
            for (Path p : tempFiles) {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignore) {
                    // 临时文件残留不影响主流程，留待系统清理
                }
            }
        }
    }

    /** 主分支策略：优先 main；有 master 则改名；皆无则新建（兼容空仓）。 */
    private String ensureMainBranch() throws Exception {
        String branch = execToString("branch", "--format=%(refname:short)");
        boolean hasMain = false;
        boolean hasMaster = false;
        for (String b : branch.split("\n")) {
            b = b.trim();
            if (b.equals("main")) {
                hasMain = true;
            } else if (b.equals("master")) {
                hasMaster = true;
            }
        }
        if (hasMain) {
            return "main";
        }
        if (hasMaster) {
            execToString("branch", "-m", "master", "main");
            return "main";
        }
        execToString("checkout", "-B", "main");
        return "main";
    }

    private boolean remoteExists(String name) {
        try {
            String out = execToString("remote");
            for (String line : out.split("\n")) {
                if (name.equals(line.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /** 远端跟踪分支（refs/remotes/<remote>/<branch>）是否存在（首次同步时远端可能尚无该分支）。 */
    private boolean remoteBranchExists(String remoteBranch) {
        try {
            StringBuilder out = new StringBuilder();
            int code = runGit(root, null, out, "rev-parse", "--verify",
                    "refs/remotes/" + remoteBranch);
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Map<String, String> sshEnvFor(RemoteSpec r, Map<String, Path> sshFiles) {
        Path key = sshFiles.get(r.name());
        if (key == null) {
            return null;
        }
        Map<String, String> env = new HashMap<>();
        env.put("GIT_SSH_COMMAND", "ssh -i " + key + " -o IdentitiesOnly=yes -o StrictHostKeyChecking=no");
        return env;
    }

    private static void trySetOwnerOnly(Path file) {
        try {
            Set<PosixFilePermission> perms = EnumSet.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);
            Files.setPosixFilePermissions(file, perms);
        } catch (UnsupportedOperationException | IOException ignore) {
            // 非 POSIX 文件系统（如 Windows）忽略：临时文件仅本会话使用
        }
    }

    private String gitVersion() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (Exception e) {
            return "?";
        }
    }

    /** 执行 git 并捕获输出到日志；非 0 退出抛异常。 */
    private void gitWithLog(SyncStepListener listener, String label,
            Path cwd, Map<String, String> env, String... args) throws Exception {
        StringBuilder out = new StringBuilder();
        int code = runGit(cwd, env, out, args);
        String text = out.toString().trim();
        if (!text.isBlank()) {
            listener.log("[" + label + "] " + text);
        }
        if (code != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed (exit " + code + ")");
        }
    }

    // =========== 冲突仲裁（复用设计 06） ===========

    /** 冲突自动解决：对每个冲突文件读 ours/theirs，按时间戳大者 wins；被覆盖方记 .conflict。 */
    private void resolveConflicts() throws Exception {
        String status = execToString("status", "--porcelain");
        for (String line : status.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
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

    // =========== git 进程执行 ===========

    private String execToString(String... args) throws Exception {
        return new String(execInDir(root, null, args), StandardCharsets.UTF_8);
    }

    private byte[] execToBytes(String... args) throws Exception {
        return execInDir(root, null, args);
    }

    private byte[] execInDir(Path cwd, Map<String, String> env, String... args) throws Exception {
        StringBuilder out = new StringBuilder();
        int code = runGit(cwd, env, out, args);
        if (code != 0) {
            String msg = "git " + String.join(" ", args) + " failed (exit " + code + "): "
                    + out;
            LOG.error("Git command failed in {}: {}", root, msg);
            throw new IOException(msg);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 执行 git 进程并返回退出码（不抛异常）。stdout/stderr 合并写入 out，供上层决定日志与是否报错。
     */
    private int runGit(Path cwd, Map<String, String> env, StringBuilder out, String... args)
            throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        Collections.addAll(cmd, args);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        if (env != null) {
            pb.environment().putAll(env);
        }
        Process p = pb.start();
        String text = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        out.append(text);
        return code;
    }
}
