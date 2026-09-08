package com.flora.sanctum.app.sync;

import com.flora.sanctum.core.model.Sanctum;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class SyncServiceTest {

    @TempDir
    Path dir;

    /** 无 git 环境直接跳过（同步能力依赖本地 git 命令）。 */
    private void assumeGit() {
        Assumptions.assumeTrue(new SyncService(dir).isGitAvailable(),
                "本地未安装 git，跳过同步相关用例");
    }

    private void runGit(Path cwd, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        byte[] out = p.getInputStream().readAllBytes();
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("git " + String.join(" ", args) + " failed: "
                    + new String(out));
        }
    }

    /** 收集 done 状态与错误信息的监听器，任意步骤失败即记录失败原因。 */
    private static final class RecordingListener implements SyncService.SyncStepListener {
        final AtomicBoolean ok = new AtomicBoolean(true);
        final AtomicReference<String> msg = new AtomicReference<>("");

        @Override
        public void beginStep(int index, String title) {
        }

        @Override
        public void markRunning(int index) {
        }

        @Override
        public void markDone(int index, String detail) {
        }

        @Override
        public void markError(int index, String detail) {
            ok.set(false);
            msg.set(detail);
        }

        @Override
        public void log(String message) {
        }

        @Override
        public void done(boolean ok, String message) {
            this.ok.set(ok);
            this.msg.set(message);
        }
    }

    @Test
    void initAndCommit() throws Exception {
        assumeGit();
        Path vault = dir.resolve("vault");
        Sanctum.createAndUnlock(vault, "pw".toCharArray(), 8192, 2, 1);

        SyncService sync = new SyncService(vault);
        sync.initIfNeeded();
        assertTrue(sync.isGitRepo());

        sync.commit("initial");
        assertTrue(Files.isDirectory(vault.resolve(".git")));
        assertTrue(Files.exists(vault.resolve(".git/HEAD")));
    }

    @Test
    void cloneAndCommitRoundTrip() throws Exception {
        assumeGit();
        Path src = dir.resolve("src");
        Sanctum.createAndUnlock(src, "pw".toCharArray(), 8192, 2, 1);
        SyncService srcSync = new SyncService(src);
        srcSync.initIfNeeded();
        srcSync.commit("v1");

        Path dst = dir.resolve("dst");
        new SyncService(src).clone(src.toUri().toString(), dst);

        assertTrue(Files.isDirectory(dst.resolve(".git")));
        assertTrue(Files.list(dst).count() > 0);
    }

    /** 完整流程：本地裸远端为空 → commit/建 main/首次推送；远端接收 main 分支。 */
    @Test
    void syncPushesToEmptyBareRemote() throws Exception {
        assumeGit();
        Path bare = dir.resolve("remote.git");
        Files.createDirectories(bare);
        runGit(bare, "init", "--bare");

        Path src = dir.resolve("src");
        Sanctum.createAndUnlock(src, "pw".toCharArray(), 8192, 2, 1);

        SyncService svc = new SyncService(src);
        RecordingListener listener = new RecordingListener();
        List<SyncService.RemoteSpec> specs = List.of(
                new SyncService.RemoteSpec("origin", bare.toAbsolutePath().toString(), null));
        svc.sync(specs, listener);

        assertTrue(listener.ok.get(), "同步应成功：" + listener.msg.get());
        String branches = runGitCapture(bare, "branch", "--format=%(refname:short)");
        assertTrue(branches.contains("main"), "裸远端应被推送 main 分支，实际：" + branches);
    }

    /** 两库经公共裸远端互相同步：src 推送 → dst 拉取合并 → dst 推送 → src 拉取合并。 */
    @Test
    void bidirectionalSyncViaSharedRemote() throws Exception {
        assumeGit();
        Path bare = dir.resolve("remote.git");
        Files.createDirectories(bare);
        runGit(bare, "init", "--bare");

        Path a = dir.resolve("a");
        Sanctum.createAndUnlock(a, "pw".toCharArray(), 8192, 2, 1);
        SyncService sa = new SyncService(a);
        sa.sync(List.of(new SyncService.RemoteSpec("origin", bare.toAbsolutePath().toString(), null)),
                new RecordingListener());

        Path b = dir.resolve("b");
        Sanctum.createAndUnlock(b, "pw".toCharArray(), 8192, 2, 1);
        SyncService sb = new SyncService(b);
        RecordingListener lb = new RecordingListener();
        sb.sync(List.of(new SyncService.RemoteSpec("origin", bare.toAbsolutePath().toString(), null)), lb);
        assertTrue(lb.ok.get(), "b 首次同步应成功：" + lb.msg.get());

        // b 修改后推送
        sb.commit("b-change");
        RecordingListener lb2 = new RecordingListener();
        sb.sync(List.of(new SyncService.RemoteSpec("origin", bare.toAbsolutePath().toString(), null)), lb2);
        assertTrue(lb2.ok.get(), "b 推送应成功：" + lb2.msg.get());

        // a 拉取应成功合并到 main
        RecordingListener la = new RecordingListener();
        sa.sync(List.of(new SyncService.RemoteSpec("origin", bare.toAbsolutePath().toString(), null)), la);
        assertTrue(la.ok.get(), "a 拉取合并应成功：" + la.msg.get());
    }

    private String runGitCapture(Path cwd, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        for (String a : args) {
            cmd.add(a);
        }
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        return new String(p.getInputStream().readAllBytes());
    }

    // ============ SSH 私钥校验 ============

    @Test
    void rejectPublicKeyAsPrivate() {
        String pub = "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGb9ECWmEzf6FQbrBZ9w7lshQhqowzIFj79znR5XUUi user@host";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SyncService.validateSshKeyPem(pub));
        assertTrue(ex.getMessage().contains("公钥"), ex.getMessage());
    }

    @Test
    void rejectEncryptedKey() {
        String enc = "-----BEGIN RSA PRIVATE KEY-----\n"
                + "Proc-Type: 4,ENCRYPTED\n"
                + "DEK-Info: AES-128-CBC,ABCDEF\n\n"
                + "abcdef\n-----END RSA PRIVATE KEY-----\n";
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SyncService.validateSshKeyPem(enc));
        assertTrue(ex.getMessage().contains("密码"), ex.getMessage());
    }

    /** 粘贴时被转义成字面 \n 的 PEM 应被还原为可用私钥。 */
    @Test
    void normalizeLiteralBackslashN() {
        String literal = "-----BEGIN OPENSSH PRIVATE KEY-----\\nbase64content\\n-----END OPENSSH PRIVATE KEY-----";
        String normalized = SyncService.validateSshKeyPem(literal);
        assertTrue(normalized.contains("\n"), "应还原真实换行");
        assertTrue(normalized.startsWith("-----BEGIN OPENSSH PRIVATE KEY-----"));
        assertTrue(normalized.endsWith("\n"));
    }

    @Test
    void acceptValidPrivateKey() {
        String pem = "-----BEGIN OPENSSH PRIVATE KEY-----\n"
                + "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAA\n"
                + "-----END OPENSSH PRIVATE KEY-----\n";
        assertEquals(pem, SyncService.validateSshKeyPem(pem));
    }
}
