package com.flora.os.keyring;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.stream.Collectors;

/**
 * Windows DPAPI 加密存储实现。
 * <p>通过 PowerShell 调用 .NET 的 System.Security.Cryptography.ProtectedData
 * 用 DPAPI 加密凭据。数据仅限当前 Windows 用户解密。</p>
 */
class WinCredentialStore implements Keyring {

    private static final Path STORE_DIR = Paths.get(
            System.getProperty("user.home"), ".flora", "credentials");

    @Override
    public void setPassword(String domain, String account, String password) throws KeyringException {
        String script = String.format(
            "[System.Reflection.Assembly]::LoadWithPartialName('System.Security') | Out-Null; " +
            "$bytes = [System.Text.Encoding]::UTF8.GetBytes('%s'); " +
            "$entropy = [System.Text.Encoding]::UTF8.GetBytes('%s/%s'); " +
            "$encrypted = [System.Security.Cryptography.ProtectedData]::Protect($bytes, $entropy, 'CurrentUser'); " +
            "[System.IO.File]::WriteAllBytes('%s', $encrypted)",
            password.replace("'", "''"),
            domain, account,
            storePath(domain, account).toString().replace("\\", "\\\\"));
        execPowerShell(script);
    }

    @Override
    public String getPassword(String domain, String account) throws KeyringException {
        Path file = storePath(domain, account);
        if (!Files.exists(file))
            throw new KeyringException("凭据不存在: " + domain + "/" + account);

        String script = String.format(
            "[System.Reflection.Assembly]::LoadWithPartialName('System.Security') | Out-Null; " +
            "$encrypted = [System.IO.File]::ReadAllBytes('%s'); " +
            "$entropy = [System.Text.Encoding]::UTF8.GetBytes('%s/%s'); " +
            "$decrypted = [System.Security.Cryptography.ProtectedData]::Unprotect($encrypted, $entropy, 'CurrentUser'); " +
            "[System.Text.Encoding]::UTF8.GetString($decrypted)",
            file.toString().replace("\\", "\\\\"),
            domain, account);
        return execPowerShell(script);
    }

    @Override
    public void deletePassword(String domain, String account) throws KeyringException {
        try {
            Files.deleteIfExists(storePath(domain, account));
        } catch (IOException e) {
            throw new KeyringException("删除凭据失败", e);
        }
    }

    @Override
    public String getStorageType() {
        return "Windows DPAPI";
    }

    @Override
    public void close() {}

    private Path storePath(String domain, String account) {
        try { Files.createDirectories(STORE_DIR); } catch (IOException ignored) {}
        return STORE_DIR.resolve(domain + "_" + account + ".dpapi");
    }

    private String execPowerShell(String script) throws KeyringException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-NoProfile", "-Command", script);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output;
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                output = r.lines().collect(Collectors.joining("\n"));
            }
            int exit = p.waitFor();
            if (exit != 0)
                throw new KeyringException(output.isEmpty()
                        ? "PowerShell 失败 (exit=" + exit + ")" : output);
            return output.trim();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyringException("PowerShell 执行失败", e);
        }
    }
}
