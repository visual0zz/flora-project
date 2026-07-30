package com.flora.os.keyring;

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

    /** 返回当前平台支持的 Keyring 类型。 */
    public static String storageType() {
        if (OS_NAME.contains("mac")) return "macOS Keychain";
        if (OS_NAME.contains("linux")) return "DBus Secret Service";
        if (OS_NAME.contains("win")) return "Windows Credential Manager";
        return "unknown";
    }

    /** 检查当前平台是否支持 Keyring。 */
    public static boolean isSupported() {
        return OS_NAME.contains("mac") || OS_NAME.contains("linux") || OS_NAME.contains("win");
    }
}
