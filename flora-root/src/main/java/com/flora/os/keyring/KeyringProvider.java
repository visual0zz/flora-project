package com.flora.os.keyring;

import java.io.File;

/**
 * Keyring 实例工厂。自动检测操作系统并返回对应的实现。
 */
public class KeyringProvider {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    private KeyringProvider() {}

    /** 创建当前平台对应的 Keyring 实例。 */
    public static Keyring create() throws KeyringException {
        if (OS_NAME.contains("mac")) {
            return new KeychainStore();
        }
        if (OS_NAME.contains("linux")) {
            return new SecretServiceStore();
        }
        if (OS_NAME.contains("win")) {
            return new WinCredentialStore();
        }
        throw new KeyringException("不支持的操作系统: " + OS_NAME);
    }

    /** 检查当前平台的原生密钥链是否可用。 */
    public static boolean isAvailable() {
        if (!isSupported()) return false;
        return switch (storageType()) {
            case "macOS Keychain (FFM)" -> checkBinary("security");
            case "Windows Credential Manager (FFM)" -> checkBinary("powershell.exe");
            case "DBus Secret Service" -> checkSecretService();
            default -> false;
        };
    }

    /** 检测 secret-tool 是否连通（后台守护进程存在）。 */
    private static boolean checkSecretService() {
        if (!checkBinary("secret-tool")) return false;
        try {
            // 检测 DBus Secret Service 是否可达
            Process p = new ProcessBuilder("dbus-send", "--session",
                    "--dest=org.freedesktop.secrets",
                    "--type=method_call",
                    "--print-reply",
                    "/org/freedesktop/secrets",
                    "org.freedesktop.DBus.Peer.Ping")
                    .redirectErrorStream(true).start();
            boolean exited = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) { p.destroyForcibly(); return false; }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 返回当前平台支持的 Keyring 类型。 */
    public static String storageType() {
        if (OS_NAME.contains("mac")) return "macOS Keychain (FFM)";
        if (OS_NAME.contains("linux")) return "DBus Secret Service";
        if (OS_NAME.contains("win")) return "Windows Credential Manager (FFM)";
        return "unknown";
    }

    /** 检查当前平台是否支持 Keyring。 */
    public static boolean isSupported() {
        return OS_NAME.contains("mac") || OS_NAME.contains("linux") || OS_NAME.contains("win");
    }

    private static boolean checkBinary(String name) {
        try {
            Process p = new ProcessBuilder("which", name)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
