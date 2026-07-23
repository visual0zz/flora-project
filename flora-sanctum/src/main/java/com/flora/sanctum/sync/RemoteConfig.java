package com.flora.sanctum.sync;

import java.nio.file.Path;

/**
 * 远程仓库配置管理。
 */
public class RemoteConfig {

    private RemoteConfig() {
    }

    /**
     * 添加或更新远程仓库。
     *
     * @param repoDir 仓库目录
     * @param name    远程名称（如 "origin"）
     * @param url     远程 URL
     * @throws GitException 如果操作失败
     */
    public static void addRemote(Path repoDir, String name, String url) {
        // 先尝试删除已有远程
        GitBackend.git(repoDir, "remote", "remove", name);
        // 添加新远程
        int exit = GitBackend.git(repoDir, "remote", "add", name, url);
        if (exit != 0) {
            throw new GitException("failed to add remote " + name + ": " + url);
        }
    }

    /**
     * 获取远程仓库 URL。
     *
     * @param repoDir 仓库目录
     * @param name    远程名称
     * @return URL，如果不存在返回 null
     */
    public static String getRemoteUrl(Path repoDir, String name) {
        try {
            return GitBackend.gitOutput(repoDir, "remote", "get-url", name);
        } catch (GitException e) {
            return null;
        }
    }

    /**
     * 列出所有远程名称。
     */
    public static String listRemotes(Path repoDir) {
        try {
            return GitBackend.gitOutput(repoDir, "remote");
        } catch (GitException e) {
            return "";
        }
    }

    /**
     * 配置 HTTPS 凭据（通过 git credential store）。
     * <p>
     * 注意：生产环境建议使用 SSH 密钥或 git credential.helper 而非明文 token。
     *
     * @param repoDir 仓库目录
     * @param username 用户名
     * @param password 密码/令牌
     */
    public static void configureHttpCredentials(Path repoDir, String username, String password) {
        String remoteUrl = getRemoteUrl(repoDir, "origin");
        if (remoteUrl == null || remoteUrl.isEmpty()) {
            return;
        }

        // 使用 git credential 存储凭据
        String credentialInput = "protocol=https\nhost=" + extractHost(remoteUrl) +
                "\nusername=" + username + "\npassword=" + password + "\n";

        try {
            ProcessBuilder pb = new ProcessBuilder("git", "-C", repoDir.toAbsolutePath().toString(),
                    "credential", "approve");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            process.getOutputStream().write(credentialInput.getBytes());
            process.getOutputStream().flush();
            process.getOutputStream().close();
            process.waitFor();
        } catch (Exception e) {
            throw new GitException("failed to configure credentials", e);
        }
    }

    private static String extractHost(String url) {
        // 从 URL 中提取主机名
        if (url.startsWith("https://")) {
            String rest = url.substring(8);
            int slash = rest.indexOf('/');
            if (slash > 0) return rest.substring(0, slash);
            return rest;
        }
        return url;
    }
}
