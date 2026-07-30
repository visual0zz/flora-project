package com.flora.os.keyring;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/** macOS Keychain 实现，通过 {@code security} CLI 交互。 */
class KeychainStore implements Keyring {

    @Override
    public void setPassword(String domain, String account, String password) throws KeyringException {
        run("add-generic-password", "-a", account, "-s", domain, "-w", password, "-U");
    }

    @Override
    public String getPassword(String domain, String account) throws KeyringException {
        return run("find-generic-password", "-a", account, "-s", domain, "-w");
    }

    @Override
    public void deletePassword(String domain, String account) throws KeyringException {
        try {
            run("delete-generic-password", "-a", account, "-s", domain);
        } catch (KeyringException e) {
            if (e.getMessage().contains("The specified item could not be found")) return;
            throw e;
        }
    }

    @Override
    public String getStorageType() {
        return "macOS Keychain";
    }

    @Override
    public void close() {}

    private String run(String... args) throws KeyringException {
        try {
            ProcessBuilder pb = new ProcessBuilder("security");
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
                    ? "security 命令失败 (exit=" + exit + ")" : output);
            return output;
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KeyringException("security 命令执行失败", e);
        }
    }
}
