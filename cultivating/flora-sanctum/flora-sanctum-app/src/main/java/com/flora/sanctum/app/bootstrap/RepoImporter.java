package com.flora.sanctum.app.bootstrap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.flora.root.runtime.log.Logger;
import com.flora.root.runtime.log.LoggerFactory;

/**
 * 导入仓库（见设计"形态与启动"）。
 * <p>
 * {@code git clone <remote> <local>} 克隆后按结构分类：
 * <ul>
 *   <li><b>非仓库</b>：无 data/、无 lib/、无仓库配置、无 md → 判定为"输入了别的代码仓库地址"，报错。</li>
 *   <li><b>空仓库</b>：克隆后为空目录 → 视为合法，建立基本结构（默认普通仓库）。</li>
 *   <li><b>普通仓库</b>：有 md（或仓库配置），无 data/lib → 直接可用。</li>
 *   <li><b>独立仓库</b>：有 data/ + lib/ → 直接可用。</li>
 * </ul>
 */
public final class RepoImporter {

    private static final Logger LOG = LoggerFactory.getLogger(RepoImporter.class);

    /** 导入结果：分类 + 可用 vault 根（非仓库为 null）。 */
    public static final class Result {
        public final VaultDetector.Type type;
        public final Path cloneRoot;
        public final Path vaultRoot;

        Result(VaultDetector.Type type, Path cloneRoot, Path vaultRoot) {
            this.type = type;
            this.cloneRoot = cloneRoot;
            this.vaultRoot = vaultRoot;
        }
    }

    private RepoImporter() {
    }

    /**
     * 克隆并分类。
     *
     * @param remote  远程仓库地址
     * @param local   本地目标目录（克隆目标）
     * @return 分类结果；非仓库 / 克隆失败抛异常
     */
    public static Result importRemote(String remote, Path local) throws Exception {
        LOG.info("Importing remote repository {} into {}", remote, local);
        clone(remote, local);
        VaultDetector.Type type = VaultDetector.detect(local);
        if (type == VaultDetector.Type.NOT_A_VAULT) {
            // 空目录 → 建基本结构（普通仓库）
            if (isEmpty(local)) {
                VaultDetector.Type t = VaultDetector.Type.NORMAL;
                LOG.info("Cloned repo is empty, treating as new normal repository: {}", local);
                return new Result(t, local, local);
            }
            LOG.warn("Cloned repository is not a flora-sanctum vault: {}", local);
            throw new IllegalArgumentException("not a flora-sanctum repository: " + local);
        }
        Path vaultRoot = VaultDetector.vaultRoot(local);
        LOG.info("Imported repository classified as {} at {}", type, vaultRoot);
        return new Result(type, local, vaultRoot);
    }

    private static void clone(String remote, Path local) throws Exception {
        LOG.info("Cloning {} into {}", remote, local);
        if (local.getParent() != null) {
            Files.createDirectories(local.getParent());
        }
        runIn(local.getParent(), "clone", remote, local.getFileName().toString());
        LOG.info("Clone finished: {}", local);
    }

    private static boolean isEmpty(Path dir) {
        if (!Files.isDirectory(dir)) {
            return false;
        }
        try (var stream = Files.list(dir)) {
            return stream.findAny().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static void runIn(Path cwd, String... args) throws Exception {
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
        int exit = p.waitFor();
        if (exit != 0) {
            throw new java.io.IOException(
                    "git " + String.join(" ", args) + " failed (exit " + exit + "): "
                            + new String(out, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
