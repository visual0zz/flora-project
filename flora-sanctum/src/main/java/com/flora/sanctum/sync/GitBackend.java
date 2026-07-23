package com.flora.sanctum.sync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 仓库管理：init、clone、open。
 * <p>
 * 通过 JDK {@link ProcessBuilder} 调用系统 git CLI，无需 JGit 依赖。
 */
public class GitBackend {

    private GitBackend() {
    }

    /**
     * 在指定目录初始化 Git 仓库。如果目录不存在则自动创建。
     *
     * @param repoDir 仓库目录
     * @throws GitException 如果初始化失败
     */
    public static void init(Path repoDir) {
        try {
            Files.createDirectories(repoDir);
        } catch (IOException e) {
            throw new GitException("cannot create directory: " + repoDir, e);
        }
        int exit = gitExec(repoDir, "git", "init");
        if (exit != 0) {
            throw new GitException("git init failed in " + repoDir);
        }
    }

    /**
     * 克隆远程仓库到本地目录。
     *
     * @param remoteUrl  远程仓库 URL（支持 file://、https://）
     * @param targetDir  目标目录（必须不存在或为空）
     * @throws GitException 如果克隆失败
     */
    public static void cloneRepo(String remoteUrl, Path targetDir) {
        if (Files.exists(targetDir)) {
            String[] children = targetDir.toFile().list();
            if (children != null && children.length > 0) {
                throw new GitException("target directory is not empty: " + targetDir);
            }
        }
        int exit = git(null, "clone", remoteUrl, targetDir.toAbsolutePath().toString());
        if (exit != 0) {
            throw new GitException("git clone failed from " + remoteUrl + " to " + targetDir);
        }
    }

    /**
     * 检查目录是否已是 Git 仓库（含 bare repo）。
     */
    public static boolean isGitRepo(Path dir) {
        return Files.exists(dir.resolve(".git"))
                || Files.exists(dir.resolve("HEAD"));
    }

    /**
     * 配置 user.name 和 user.email（提交所需）。
     */
    public static void configureUser(Path repoDir, String name, String email) {
        git(repoDir, "config", "user.name", name);
        git(repoDir, "config", "user.email", email);
    }

    /**
     * 在仓库目录中执行 git 命令。
     *
     * @param repoDir 仓库目录（null 表示不需要）
     * @param args    命令参数
     * @return 退出码
     */
    static int git(Path repoDir, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        if (repoDir != null) {
            cmd.add("-C");
            cmd.add(repoDir.toAbsolutePath().toString());
        }
        cmd.addAll(List.of(args));
        return gitExec(null, cmd.toArray(new String[0]));
    }

    /**
     * 在仓库目录中执行 git 命令并返回标准输出。
     *
     * @param repoDir 仓库目录
     * @param args    命令参数
     * @return 标准输出字符串（trimmed）
     */
    static String gitOutput(Path repoDir, String... args) {
        List<String> cmd = new ArrayList<>();
        cmd.add("git");
        cmd.add("-C");
        cmd.add(repoDir.toAbsolutePath().toString());
        cmd.addAll(List.of(args));
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes()).trim();
            int exit = process.waitFor();
            if (exit != 0) {
                throw new GitException("git command failed: " + String.join(" ", cmd) + "\n" + output);
            }
            return output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("git command failed: " + String.join(" ", cmd), e);
        }
    }

    private static int gitExec(Path workingDir, String... args) {
        try {
            ProcessBuilder pb = new ProcessBuilder(args);
            if (workingDir != null) {
                pb.directory(workingDir.toFile());
            }
            pb.redirectErrorStream(true);
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            return process.waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitException("git command failed: " + String.join(" ", args), e);
        }
    }
}
