package com.flora.sanctum.app.sync;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

import com.flora.sanctum.app.bootstrap.RepoCreator;

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
 * SSH 私钥<b>不落盘</b>：同步时为本次会话启动一个私有的 {@code ssh-agent}，经标准输入把私钥传给
 * {@code ssh-add -}（密钥只驻留 agent 内存），再通过 {@code SSH_AUTH_SOCK} 让 git/ssh 自动选用，
 * 同步结束后 {@code ssh-agent -k} 销毁该 agent 以清除内存中的密钥。相比写入临时文件（0600）的方案，
 * 彻底避免了密钥在磁盘/交换/休眠镜像中残留的可能。
 * <p>
 * 平台要求：git 与 ssh 命令跨平台一致。本方案的 SSH 部分依赖 {@code ssh-agent -s} / {@code ssh-add -} /
 * {@code ssh-keygen -y -f /dev/stdin}（私钥经管道而非文件传递）。在 Linux / macOS 与 Windows 的
 * <b>Git for Windows（其自带的 MSYS OpenSSH 支持上述用法）</b>下均可工作；Windows 的原生
 * {@code C:\Windows\System32\OpenSSH}（服务式 agent，无 {@code /dev/stdin}）不在支持范围内。
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

    /** 初始化 git 仓库（若缺）。Sanctum 默认 .gitignore 由 {@link RepoCreator} 在独立仓库创建/升级/刷新时统一管理。 */
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
        titles.add("启动 ssh-agent");
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

        Map<String, String> agentEnv = null; // 私有 ssh-agent 的环境（SSH_AUTH_SOCK / SSH_AGENT_PID）
        int idx = 0;
        try {
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

            // 3) 启动私有 ssh-agent（仅当存在需要密钥的远程）；密钥经 stdin 注入 agent 内存，不落盘
            listener.markRunning(idx);
            try {
                boolean needsAgent = remotes.stream()
                        .anyMatch(r -> r.sshKeyPem() != null && !r.sshKeyPem().isBlank());
                if (needsAgent) {
                    agentEnv = startAgent();
                    listener.markDone(idx, "ssh-agent 已启动");
                } else {
                    agentEnv = null;
                    listener.markDone(idx, "无需 SSH 密钥");
                }
            } catch (Exception e) {
                listener.markError(idx, e.getMessage());
                listener.done(false, "启动 ssh-agent 失败：" + e.getMessage());
                throw e;
            }
            idx++;

            // 4) 配置每个远程（已存在则校正地址），并把各自的 SSH 私钥注入 agent
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    if (remoteExists(r.name())) {
                        execToString("remote", "set-url", r.name(), r.url());
                    } else {
                        execToString("remote", "add", r.name(), r.url());
                    }
                    if (r.sshKeyPem() != null && !r.sshKeyPem().isBlank()) {
                        String pem = validateSshKeyPem(r.sshKeyPem());
                        if (agentEnv == null) {
                            throw new IOException("ssh-agent 未就绪，无法载入密钥");
                        }
                        addKeyToAgent(pem, agentEnv);
                    }
                    listener.markDone(idx, r.url());
                } catch (IllegalArgumentException e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "远程 " + r.name() + " 的密钥无效：" + e.getMessage());
                    throw e;
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "配置远端 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            // 5) 确保主分支
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

            // 6) 提交
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

            // 7) 逐个 fetch（各自 SSH 环境）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    gitWithLog(listener, "fetch " + r.name(), root, sshEnvFor(r, agentEnv),
                            "fetch", r.name());
                    listener.markDone(idx, "已拉取 " + r.name());
                } catch (Exception e) {
                    listener.markError(idx, e.getMessage());
                    listener.done(false, "拉取 " + r.name() + " 失败：" + e.getMessage());
                    throw e;
                }
                idx++;
            }

            // 8) 逐个 merge（冲突按块内时间戳仲裁；远端尚无该分支时跳过合并）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    String remoteBranch = r.name() + "/" + branch;
                    if (remoteBranchExists(remoteBranch)) {
                        int code = runGit(root, sshEnvFor(r, agentEnv), new StringBuilder(),
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

            // 9) 逐个 push（各自 SSH 环境）
            for (RemoteSpec r : remotes) {
                listener.markRunning(idx);
                try {
                    gitWithLog(listener, "push " + r.name(), root, sshEnvFor(r, agentEnv),
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
            // 销毁私有 ssh-agent，清除其内存中的全部密钥；即便中途异常也务必执行
            stopAgent(agentEnv);
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

    /**
     * 返回注入私有 ssh-agent 的环境变量（供 git/ssh 子进程继承 {@code SSH_AUTH_SOCK}）。
     * agent 已载入全部远端密钥，ssh 会自动选用匹配的密钥，无需 {@code -i} 临时文件。
     */
    private Map<String, String> sshEnvFor(RemoteSpec r, Map<String, String> agentEnv) {
        if (agentEnv == null || agentEnv.isEmpty()) {
            return null;
        }
        return new HashMap<>(agentEnv);
    }

    /**
     * 启动一个本次同步专用的 {@code ssh-agent}（通过 {@code ssh-agent -s} 解析其输出中的
     * {@code SSH_AUTH_SOCK} / {@code SSH_AGENT_PID}）。该 agent 仅服务于本次会话，结束后由
     * {@link #stopAgent(Map)} 销毁，密钥不会进入系统既有 agent，也不会落盘。
     */
    private static Map<String, String> startAgent() throws Exception {
        ProcessBuilder pb = new ProcessBuilder("ssh-agent", "-s");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            String hint = System.getProperty("os.name", "").toLowerCase().contains("win")
                    ? "（Windows 需安装 Git for Windows 以使用其自带的 OpenSSH，原生 System32\\OpenSSH 不支持此用法）"
                    : "";
            throw new IOException("ssh-agent 启动失败: " + out.trim() + hint);
        }
        Map<String, String> env = new HashMap<>();
        Matcher m1 = Pattern.compile("SSH_AUTH_SOCK=([^;\\s]+)").matcher(out);
        Matcher m2 = Pattern.compile("SSH_AGENT_PID=(\\d+)").matcher(out);
        if (m1.find()) {
            env.put("SSH_AUTH_SOCK", m1.group(1));
        }
        if (m2.find()) {
            env.put("SSH_AGENT_PID", m2.group(1));
        }
        if (!env.containsKey("SSH_AUTH_SOCK")) {
            throw new IOException("ssh-agent 输出缺少 SSH_AUTH_SOCK: " + out.trim());
        }
        return env;
    }

    /**
     * 经标准输入把私钥交给 {@code ssh-add -}；私钥仅流经管道进入 agent 内存，全程不写文件。
     *
     * @throws IOException 密钥无效或注入失败
     */
    private static void addKeyToAgent(String pem, Map<String, String> agentEnv) throws Exception {
        String validated = validateSshKeyPem(pem);
        ProcessBuilder pb = new ProcessBuilder("ssh-add", "-");
        pb.environment().putAll(agentEnv);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        try (OutputStream os = p.getOutputStream()) {
            os.write(validated.getBytes(StandardCharsets.UTF_8));
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("ssh-add 注入密钥失败: " + out.trim());
        }
    }

    /** 销毁私有 ssh-agent，清除其内存中的密钥；失败亦不抛出，避免掩盖主流程错误。 */
    private static void stopAgent(Map<String, String> agentEnv) {
        if (agentEnv == null || !agentEnv.containsKey("SSH_AGENT_PID")) {
            return;
        }
        try {
            ProcessBuilder pb = new ProcessBuilder("ssh-agent", "-k");
            pb.environment().putAll(agentEnv);
            pb.redirectErrorStream(true);
            pb.start().waitFor();
        } catch (Exception ignore) {
            // agent 会随父进程退出而释放，忽略清理失败
        }
    }

    /**
     * 校验并规整 SSH 私钥 PEM：修剪空白；把粘贴时被转义成字面 {@code \n} 的换行还原；
     * 检测公钥误填、缺少私钥头、受密码保护（非交互无法使用）等常见错误，给出可读中文说明。
     * 返回规整后的私钥文本（结尾保证换行）。
     *
     * @throws IllegalArgumentException 密钥不可用（消息可直接展示给用户）
     */
    public static String validateSshKeyPem(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new IllegalArgumentException("SSH 私钥为空，请在密钥设置中填入私钥");
        }
        String t = pem.trim();
        // 常见粘贴损坏：换行被转义成字面 \n（无真实换行但有 \n 序列）时还原
        if (!t.contains("\n") && t.contains("\\n")) {
            t = t.replace("\\n", "\n").trim();
        }
        if (!t.endsWith("\n")) {
            t = t + "\n";
        }
        // 公钥误填：以 ssh-rsa / ssh-ed25519 / ecdsa-... 开头的一行
        if (t.matches("(?s)^\\s*(ssh-(rsa|ed25519|dss)|ecdsa-[A-Za-z0-9-]+)\\s+\\S+.*")) {
            throw new IllegalArgumentException("填入的是 SSH 公钥，请改用对应的私有密钥"
                    + "（以 -----BEGIN ... PRIVATE KEY----- 开头）");
        }
        if (!t.contains("PRIVATE KEY")) {
            throw new IllegalArgumentException("SSH 私钥格式无效：缺少 -----BEGIN ... PRIVATE KEY----- 头，"
                    + "请确认填入的是私钥 PEM 文本");
        }
        if (t.contains("ENCRYPTED")) {
            throw new IllegalArgumentException("SSH 私钥受密码保护，非交互式同步无法使用，"
                    + "请改用无密码的私钥（ssh-keygen -p 去除密码）");
        }
        return t;
    }

    /**
     * 由私钥 PEM 推导公钥文本（形如 {@code ssh-rsa AAAA...}），全程不落盘：为本次推导启动一个一次性的
     * {@code ssh-agent}，经标准输入把私钥灌入 agent 内存（不写文件），再用 {@code ssh-add -L} 取回公钥，
     * 最后 {@code ssh-agent -k} 销毁 agent。失败（无 ssh-agent/ssh-add、密钥加密或损坏）返回
     * {@code null}，由调用方决定提示方式。与同步共用同一套 agent 机制，因此同样要求 Git for Windows
     * （见类注释）；原生 Windows OpenSSH 不支持此用法。
     */
    public static String derivePublicKey(String pem) {
        try {
            String validated = validateSshKeyPem(pem);
            Map<String, String> agent = startAgent();
            try {
                addKeyToAgent(validated, agent);
                ProcessBuilder pb = new ProcessBuilder("ssh-add", "-L");
                pb.environment().putAll(agent);
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                int code = p.waitFor();
                if (code != 0) {
                    return null;
                }
                for (String line : out.split("\n")) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        return line;
                    }
                }
                return null;
            } finally {
                stopAgent(agent);
            }
        } catch (Exception e) {
            return null;
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
