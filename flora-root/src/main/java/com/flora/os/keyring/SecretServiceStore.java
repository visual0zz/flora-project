package com.flora.os.keyring;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** Linux DBus Secret Service 实现，通过 {@code secret-tool} CLI 交互（libsecret 依赖 GLib，不适合纯 FFM 调用）。 */
class SecretServiceStore implements Keyring {

    @Override
    public void setPassword(String domain, String account, String password) throws KeyringException {
        run("store", "--label=java-keyring", "domain", domain, "account", account);
        // secret-tool 不支持直接传密码，需要用 stdin
        try {
            ProcessBuilder pb = new ProcessBuilder("secret-tool",
                    "store", "--label=java-keyring", "domain", domain, "account", account);
            Process p = pb.start();
            try (OutputStream os = p.getOutputStream()) {
                os.write((password + "\n").getBytes(StandardCharsets.UTF_8));
                os.write((password + "\n").getBytes(StandardCharsets.UTF_8)); // 确认
                os.flush();
            }
            int exit = p.waitFor();
            if (exit != 0) throw new KeyringException("secret-tool store 失败 (exit=" + exit + ")");
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyringException("secret-tool 执行失败", e);
        }
    }

    @Override
    public String getPassword(String domain, String account) throws KeyringException {
        return run("lookup", "domain", domain, "account", account);
    }

    @Override
    public void deletePassword(String domain, String account) throws KeyringException {
        try {
            run("clear", "domain", domain, "account", account);
        } catch (KeyringException e) {
            if (e.getMessage().contains("not found")) return;
            throw e;
        }
    }

    @Override
    public String getStorageType() {
        return "DBus Secret Service";
    }

    @Override
    public void close() {}

    private String run(String... args) throws KeyringException {
        try {
            ProcessBuilder pb = new ProcessBuilder("secret-tool");
            for (String a : args) pb.command().add(a);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                output = r.lines().collect(Collectors.joining("\n"));
            }
            int exit = p.waitFor();
            if (exit != 0) throw new KeyringException(output.isEmpty()
                    ? "secret-tool 命令失败 (exit=" + exit + ")" : output);
            return output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyringException("secret-tool 执行失败", e);
        }
    }
}
